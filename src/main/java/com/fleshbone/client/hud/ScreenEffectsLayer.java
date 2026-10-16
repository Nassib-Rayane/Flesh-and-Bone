package com.fleshbone.client.hud;

import com.fleshbone.FleshBone;
import com.fleshbone.client.ClientBody;
import com.fleshbone.config.FBConfig;
import com.fleshbone.sim.Body;
import com.fleshbone.sim.Vitals;
import com.fleshbone.sim.system.ConsciousnessSystem;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * Full-screen overlays drawn under the HUD: red pain vignette, washed-out blood loss, blue hypoxia, cold and heat
 * tints, and the darkening that leads to unconsciousness (2.1 % darker per point of consciousness below 70).
 */
public final class ScreenEffectsLayer implements GuiLayer {
    private static final Identifier VIGNETTE = FleshBone.id("textures/gui/vignette.png");
    /** Consciousness at which the screen is fully dark. */
    private static final float FULL_DARK = 22.38f;

    @Override
    public void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Body body = ClientBody.get();
        if (body == null) return;
        float intensity = FBConfig.SCREEN_EFFECTS.get().floatValue();
        int w = g.guiWidth();
        int h = g.guiHeight();
        Vitals v = body.vitals;

        if (intensity > 0f) {
            float pain = body.derived.totalPain;
            float painAlpha = clamp((pain - 10f) / 90f) * 0.8f;
            if (painAlpha > 0f) vignette(g, w, h, 0xB01010, painAlpha * intensity);

            float bloodLoss = clamp((4.6f - v.bloodVolume) / 2.0f);
            if (bloodLoss > 0f) {
                g.fill(0, 0, w, h, color(0x8A8A8A, bloodLoss * 0.28f * intensity));
                vignette(g, w, h, 0xE6E6E6, bloodLoss * 0.55f * intensity);
            }
            float hypoxia = clamp((90f - v.spo2) / 50f);
            if (hypoxia > 0f) vignette(g, w, h, 0x1C2C66, hypoxia * 0.7f * intensity);
            float cold = clamp((35.5f - v.bodyTemp) / 6f);
            if (cold > 0f) vignette(g, w, h, 0xA8D8FF, cold * 0.7f * intensity);
            float heat = clamp((v.bodyTemp - 38.5f) / 3f);
            if (heat > 0f) vignette(g, w, h, 0xFF7A1A, heat * 0.5f * intensity);
            // Opioid haze, and the clammy sweat of withdrawal
            float high = clamp((com.fleshbone.sim.system.ChemistrySystem.relief(body.chem) - 10f) / 70f);
            if (high > 0f) vignette(g, w, h, 0x7A3CC8, high * 0.55f * intensity);
            float withdrawal = clamp((com.fleshbone.sim.system.ChemistrySystem.withdrawal(body.chem) - 10f) / 50f);
            if (withdrawal > 0f) vignette(g, w, h, 0xB8C870, withdrawal * 0.4f * intensity);
            float allergy = clamp(body.chem.anaphylaxis / 60f);
            if (allergy > 0f) vignette(g, w, h, 0xE03070, allergy * 0.45f * intensity);
        }

        // A lost eye takes one side of the view, both take nearly everything
        var senses = body.senses;
        if (senses.blind()) {
            g.fill(0, 0, w, h, 0xF4000000);
        } else {
            if (senses.leftEyeLost) g.fill(0, 0, w / 2, h, 0xF0000000);
            if (senses.rightEyeLost) g.fill(w / 2, 0, w, h, 0xF0000000);
        }
        float flash = com.fleshbone.client.ClientEpisodes.flash();
        if (flash > 0f) g.fill(0, 0, w, h, color(0xFFFFFF, flash * 0.85f));
        float hallucination = com.fleshbone.client.ClientEpisodes.hallucination();
        if (hallucination > 0f) vignette(g, w, h, 0xD01010, hallucination * 0.8f);

        // Darkening is a gameplay signal, not decoration: never fully disabled
        float c = v.consciousness;
        if (c < ConsciousnessSystem.DARKENING) {
            float dark = c <= FULL_DARK ? 1f : Math.min(1f, (ConsciousnessSystem.DARKENING - c) * 0.021f);
            g.fill(0, 0, w, h, color(0x000000, dark * Math.max(0.6f, Math.min(1f, intensity))));
        }
        boolean asleep = body.needs.asleep || mc.player.isSleeping();
        if (asleep && !v.cardiacArrest) {
            g.centeredText(mc.font, Component.translatable("fleshbone.screen.asleep",
                    com.fleshbone.client.hud.Words.format(body.needs.energy, 0)), w / 2, h / 2 - 10, 0xFF8090B0);
            if (body.needs.asleep) {
                g.centeredText(mc.font, Component.translatable("fleshbone.screen.asleep.hint",
                        com.fleshbone.client.FBKeys.SLEEP.get().getTranslatedKeyMessage()), w / 2, h / 2 + 4, 0xFF505868);
            }
        } else if (c < ConsciousnessSystem.UNCONSCIOUS) {
            Component title = Component.translatable(v.cardiacArrest ? "fleshbone.screen.dying" : "fleshbone.screen.unconscious");
            int color = v.cardiacArrest ? 0xFF8A1010 : 0xFF707070;
            g.centeredText(mc.font, title, w / 2, h / 2 - 10, color);
            if (v.cardiacArrest) {
                g.centeredText(mc.font, Component.translatable("fleshbone.screen.dying.hint"), w / 2, h / 2 + 4, 0xFF505050);
            }
        }
        if (v.lastStand > 0f) {
            g.centeredText(mc.font, Component.translatable("fleshbone.screen.last_stand", com.fleshbone.client.hud.Words.duration(v.lastStand)),
                    w / 2, 30, Math.sin(com.fleshbone.client.ClientBody.time() * 6.0) > 0 ? 0xFFFFD040 : 0xFFFF8020);
        }
    }

    private static void vignette(GuiGraphicsExtractor g, int w, int h, int rgb, float alpha) {
        if (alpha <= 0.005f) return;
        g.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, 0, 0, 0f, 0f, w, h, 256, 256, 256, 256, color(rgb, alpha));
    }

    private static int color(int rgb, float alpha) {
        int a = (int) (clamp(alpha) * 255f);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static float clamp(float value) {
        return value < 0f ? 0f : Math.min(1f, value);
    }
}
