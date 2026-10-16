package com.fleshbone.client;

import com.fleshbone.FleshBone;
import com.fleshbone.network.FBPayloads;
import com.fleshbone.sim.BodyEvent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec2;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * What a damaged brain does to the senses (Casualties: Unknown's brain damage episodes): an attack that is not there,
 * left and right swapped for a while, a white flash, the world shaking.
 */
@EventBusSubscriber(modid = FleshBone.MODID, value = Dist.CLIENT)
public final class ClientEpisodes {
    private static long mirroredUntil;
    private static long flashStart = -100_000L;
    private static long hallucinationStart = -100_000L;
    private static long shakeUntil;

    private ClientEpisodes() {}

    @SubscribeEvent
    static void registerHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(FBPayloads.Episode.TYPE, (payload, context) -> onEpisode(payload));
    }

    private static void onEpisode(FBPayloads.Episode payload) {
        BodyEvent.Type[] types = BodyEvent.Type.values();
        if (payload.episode() < 0 || payload.episode() >= types.length) return;
        long now = net.minecraft.util.Util.getMillis();
        Minecraft mc = Minecraft.getInstance();
        switch (types[payload.episode()]) {
            case BRAIN_HALLUCINATION -> {
                hallucinationStart = now;
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_HURT, 1f, 0.8f));
                shakeUntil = Math.max(shakeUntil, now + 600);
            }
            case BRAIN_MIRRORED -> mirroredUntil = now + 10_000;
            case BRAIN_SHAKE -> shakeUntil = now + 3_000;
            case BRAIN_FLASH -> flashStart = now;
            default -> { }
        }
    }

    /** Left and right swap while mirrored. */
    @SubscribeEvent
    static void onMovementInput(MovementInputUpdateEvent event) {
        if (net.minecraft.util.Util.getMillis() >= mirroredUntil) return;
        Vec2 move = event.getInput().moveVector;
        event.getInput().moveVector = new Vec2(-move.x, move.y);
    }

    public static boolean mirrored() {
        return net.minecraft.util.Util.getMillis() < mirroredUntil;
    }

    /** 0–1 white flash. */
    public static float flash() {
        float t = (net.minecraft.util.Util.getMillis() - flashStart) / 700f;
        return t < 0f || t > 1f ? 0f : 1f - t;
    }

    /** 0–1 red flash of the attack that is not there. */
    public static float hallucination() {
        float t = (net.minecraft.util.Util.getMillis() - hallucinationStart) / 500f;
        return t < 0f || t > 1f ? 0f : 1f - t;
    }

    /** Extra camera shake in degrees. */
    public static float shake() {
        return net.minecraft.util.Util.getMillis() < shakeUntil ? 2.5f : 0f;
    }
}
