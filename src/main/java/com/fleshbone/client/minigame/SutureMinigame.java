package com.fleshbone.client.minigame;

import com.fleshbone.sim.minigame.StitchSequence;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Click the stitch points in order, alternating sides of the wound. Precision makes a clean, strong suture. */
public class SutureMinigame extends MinigameScreen {
    private static final int STITCHES = 5;
    private StitchSequence stitches;
    private float missFlash;

    public SutureMinigame(Screen parent, Completion completion) {
        super(Component.translatable("fleshbone.minigame.suture"), Component.translatable("fleshbone.minigame.suture.help"), parent, completion);
    }

    @Override
    protected void init() {
        super.init();
        stitches = new StitchSequence(cx - 70, cx + 70, cy - 12, 9, STITCHES, 5);
    }

    @Override
    protected void drawGame(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int y = cy - 12;
        g.fill(cx - 90, y - 26, cx + 90, y + 26, 0xFFD8B8A0);
        // The wound, closing as it is stitched
        g.fill(cx - 75, y - 2, cx + 75, y + 2, 0xFF8A0E0E);
        g.fill(cx - 75, y - 1, cx + 75, y + 1, 0xFFC01818);
        int done = stitches.next();
        for (int i = 1; i < done; i++) {
            line(g, stitches.x(i - 1), stitches.y(i - 1), stitches.x(i), stitches.y(i), 0xFF101010);
        }
        for (int i = 0; i < stitches.points(); i++) {
            int px = Math.round(stitches.x(i));
            int py = Math.round(stitches.y(i));
            if (i < done) {
                g.fill(px - 1, py - 1, px + 2, py + 2, 0xFF101010);
            } else if (i == done) {
                float pulse = 0.5f + 0.5f * (float) Math.sin(time() * 8.0);
                int r = 3 + Math.round(pulse * 2f);
                g.outline(px - r, py - r, r * 2 + 1, r * 2 + 1, 0xFFFFD27A);
                g.fill(px, py, px + 1, py + 1, 0xFFFFFFFF);
            } else {
                g.fill(px, py, px + 1, py + 1, 0x80606060);
            }
        }
        if (missFlash > 0f) {
            g.fill(cx - 90, y - 26, cx + 90, y + 26, ((int) (missFlash * 120f) << 24) | 0xC00000);
            missFlash = Math.max(0f, missFlash - 0.05f);
        }
        g.centeredText(font, Component.translatable("fleshbone.minigame.suture.status", done / 2, STITCHES, stitches.mistakes()),
                cx, cy + 36, TEXT);
    }

    @Override
    protected boolean click(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (!stitches.click((float) event.x(), (float) event.y())) missFlash = 1f;
            if (stitches.done()) finish(stitches.quality(), 0);
            return true;
        }
        return super.click(event, doubleClick);
    }

    @Override
    protected void onTimeout() {
        if (stitches.next() >= 2) finish(stitches.quality() * stitches.next() / stitches.points(), 0);
        else onClose();
    }
}
