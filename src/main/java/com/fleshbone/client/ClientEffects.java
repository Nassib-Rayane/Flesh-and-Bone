package com.fleshbone.client;

import com.fleshbone.FleshBone;
import com.fleshbone.config.FBConfig;
import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.Vitals;
import com.fleshbone.sim.injury.InjuryApplier;
import com.fleshbone.sim.system.ConsciousnessSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Camera sway and shake, tunnel vision, fainting blur and the heartbeat. */
@EventBusSubscriber(modid = FleshBone.MODID, value = Dist.CLIENT)
public final class ClientEffects {
    private static final Identifier BLUR = Identifier.withDefaultNamespace("blur");
    private static boolean blurActive;
    private static float heartbeatTimer;

    private ClientEffects() {}

    @SubscribeEvent
    static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Body body = ClientBody.get();
        if (body == null || !FBConfig.CAMERA_EFFECTS.get()) return;
        Vitals v = body.vitals;
        float t = ClientBody.time();

        float sway = 0f;
        if (v.consciousness < ConsciousnessSystem.CONFUSED) sway += (ConsciousnessSystem.CONFUSED - v.consciousness) / 90f * 7f;
        if (body.part(BodyPart.HEAD).muscle < InjuryApplier.CONCUSSION_MUSCLE) sway += 5f;
        if (v.brainHealth < 80f) sway += (80f - v.brainHealth) / 80f * 6f;
        sway += Math.min(5f, com.fleshbone.sim.system.ChemistrySystem.relief(body.chem) / 12f);
        float roll = (float) (Math.sin(t * 0.9) * sway + Math.sin(t * 2.3) * sway * 0.3);

        float shake = com.fleshbone.client.ClientEpisodes.shake();
        float pain = body.derived.totalPain;
        if (pain > 55f) shake += (pain - 55f) / 45f * 0.6f;
        if (v.bodyTemp < 34.5f) shake += Math.min(1.2f, (34.5f - v.bodyTemp) * 0.4f);
        if (body.mind.trauma > 45f) shake += 0.3f;
        shake += Math.min(0.8f, com.fleshbone.sim.system.ChemistrySystem.withdrawal(body.chem) / 60f);
        if (body.chem.caffeine > 40f || body.chem.epinephrine > 40f) shake += 0.25f;
        if (v.shock > 30f) shake += v.shock / 100f * 0.5f;
        float jitterYaw = (float) (Math.sin(t * 37.0) * Math.sin(t * 13.0)) * shake;
        float jitterPitch = (float) (Math.sin(t * 41.0 + 1.3) * Math.sin(t * 11.0)) * shake;

        event.setRoll(event.getRoll() + roll + jitterYaw * 0.5f);
        event.setYaw(event.getYaw() + jitterYaw);
        event.setPitch(event.getPitch() + jitterPitch);
    }

    /** Shock narrows the field of view. */
    @SubscribeEvent
    static void onFov(ViewportEvent.ComputeFov event) {
        Body body = ClientBody.get();
        if (body == null || !FBConfig.CAMERA_EFFECTS.get()) return;
        float narrowing = body.vitals.shock / 100f * 0.2f;
        if (narrowing > 0f) event.setFOV(event.getFOV() * (1f - narrowing));
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Body body = ClientBody.get();
        updateBlur(mc.gameRenderer, body);
        updateHeartbeat(mc, body);
    }

    private static void updateBlur(GameRenderer renderer, Body body) {
        // Fainting blurs the view, and so does a damaged brain (below 80 %, as in Casualties: Unknown)
        boolean want = body != null && FBConfig.BLUR.get()
                && (body.vitals.consciousness < ConsciousnessSystem.FAINTING || body.vitals.brainHealth < com.fleshbone.sim.system.BrainSystem.BLURRED);
        if (want && !blurActive && renderer.currentPostEffect() == null) {
            renderer.setPostEffect(BLUR);
            blurActive = true;
        } else if (!want && blurActive) {
            if (BLUR.equals(renderer.currentPostEffect())) renderer.clearPostEffect();
            blurActive = false;
        }
    }

    private static void updateHeartbeat(Minecraft mc, Body body) {
        heartbeatTimer -= 0.05f;
        if (body == null || !FBConfig.HEARTBEAT.get() || mc.isPaused()) return;
        Vitals v = body.vitals;
        if (v.cardiacArrest || v.heartRate <= 0f) return;
        boolean audible = v.heartRate > 125f || v.heartRate < 45f || v.consciousness < ConsciousnessSystem.INCAPACITATED;
        if (!audible || heartbeatTimer > 0f) return;
        float pitch = 0.9f + Math.min(0.5f, Math.max(0f, (v.heartRate - 70f) / 300f));
        float volume = v.consciousness < ConsciousnessSystem.INCAPACITATED ? 0.6f : 0.35f;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, pitch, volume));
        heartbeatTimer = 60f / Math.max(30f, v.heartRate);
    }
}
