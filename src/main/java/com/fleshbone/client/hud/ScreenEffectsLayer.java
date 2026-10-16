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
        }

        // Darkening is a gameplay signal, not decoration: never fully disabled
        float c = v.consciousness;
        if (c < ConsciousnessSystem.DARKENING) {
            float dark = c <= FULL_DARK ? 1f : Math.min(1f, (ConsciousnessSystem.DARKENING - c) * 0.021f);
            g.fill(0, 0, w, h, color(0x000000, dark * Math.max(0.6f, Math.min(1f, intensity))));
        }
        if (c < ConsciousnessSystem.UNCONSCIOUS) {
            Component title = Component.translatable(v.cardiacArrest ? "fleshbone.screen.dying" : "fleshbone.screen.unconscious");
            int color = v.cardiacArrest ? 0xFF8A1010 : 0xFF707070;
            g.centeredText(mc.font, title, w / 2, h / 2 - 10, color);
            if (v.cardiacArrest) {
                g.centeredText(mc.font, Component.translatable("fleshbone.screen.dying.hint"), w / 2, h / 2 + 4, 0xFF505050);
            }
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
