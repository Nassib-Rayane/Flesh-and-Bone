package com.fleshbone.client.minigame;

import java.util.concurrent.ThreadLocalRandom;

import com.fleshbone.sim.minigame.PullTracker;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Grab the fragment and pull it out slowly along the arrow. Too fast and it slips. Tweezers forgive more. */
public class ExtractMinigame extends MinigameScreen {
    private static final int WOUND_RADIUS = 28;
    private static final float DISTANCE = 52f;
    private final boolean tweezers;
    private PullTracker tracker;
    private float angle;

    public ExtractMinigame(Screen parent, Completion completion, boolean tweezers) {
        super(Component.translatable(tweezers ? "fleshbone.minigame.extract" : "fleshbone.minigame.extract_hands"),
                Component.translatable("fleshbone.minigame.extract.help"), parent, completion);
        this.tweezers = tweezers;
    }

    @Override
    protected void init() {
        super.init();
        angle = (float) (ThreadLocalRandom.current().nextDouble() * Math.PI * 2);
        float startX = cx + (float) Math.cos(angle) * 6f;
        float startY = cy - 12 + (float) Math.sin(angle) * 6f;
        tracker = new PullTracker(startX, startY, angle, DISTANCE, tweezers ? 240f : 140f, tweezers ? 7f : 5f);
    }

    @Override
    protected void drawGame(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int y = cy - 12;
        g.fill(cx - 70, y - 40, cx + 70, y + 40, 0xFFD8B8A0);
        disc(g, cx, y, WOUND_RADIUS, 0xFF9A2020);
        disc(g, cx, y, WOUND_RADIUS - 6, 0xFF6A0E0E);
        // Pull direction
        float ax = cx + (float) Math.cos(angle) * (WOUND_RADIUS + 8);
        float ay = y + (float) Math.sin(angle) * (WOUND_RADIUS + 8);
        float bx = cx + (float) Math.cos(angle) * (WOUND_RADIUS + 22);
        float by = y + (float) Math.sin(angle) * (WOUND_RADIUS + 22);
        line(g, ax, ay, bx, by, 0xC0FFD27A);
        int fx = Math.round(tracker.fragmentX());
        int fy = Math.round(tracker.fragmentY());
        g.fill(fx - 3, fy - 2, fx + 3, fy + 2, tracker.slipped() ? 0xFF404040 : 0xFF9A9AA0);
        g.fill(fx - 1, fy - 3, fx + 1, fy + 3, tracker.slipped() ? 0xFF404040 : 0xFFC8C8D0);
        if (tracker.grabbing()) g.outline(fx - 5, fy - 5, 11, 11, tweezers ? 0xFFC8C8D0 : 0xFFE0C0A0);

        int barX = cx - 60;
        int barY = cy + 32;
        float ratio = Math.min(1.2f, tracker.speedRatio());
        g.fill(barX, barY, barX + 120, barY + 4, 0xFF2A2420);
        g.fill(barX, barY, barX + Math.round(100f * ratio), barY + 4, ratio > 0.85f ? BAD : ratio > 0.6f ? 0xFFE0B040 : GOOD);
        g.fill(barX + 100, barY - 1, barX + 101, barY + 5, 0xFFFFFFFF);
        g.centeredText(font, Component.translatable("fleshbone.minigame.extract.status", Math.round(tracker.progress() * 100f)), cx, barY + 8, TEXT);

        if (tracker.slipped()) finish(0f, 0);
        else if (tracker.done()) finish(1f, 0);
    }

    @Override
    protected boolean click(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            tracker.grab((float) event.x(), (float) event.y(), time());
            return true;
        }
        return super.click(event, doubleClick);
    }

    @Override
    protected boolean drag(MouseButtonEvent event, double dx, double dy) {
        tracker.drag((float) event.x(), (float) event.y(), time());
        return true;
    }

    @Override
    protected boolean release(MouseButtonEvent event) {
        tracker.release();
        return super.release(event);
    }

    @Override
    protected void onTimeout() {
        finish(tracker.quality(), 0);
    }
}
