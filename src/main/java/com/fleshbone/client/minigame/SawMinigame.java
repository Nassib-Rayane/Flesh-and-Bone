package com.fleshbone.client.minigame;

import com.fleshbone.client.hud.BodyDoll;
import com.fleshbone.sim.minigame.SawTracker;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Hold the left button and saw left and right along the line until the limb comes off. */
public class SawMinigame extends MinigameScreen {
    private static final int STROKES = 12;
    private final float skin;
    private SawTracker tracker;
    private boolean holding;

    public SawMinigame(Screen parent, Completion completion, float skin) {
        super(Component.translatable("fleshbone.minigame.saw"), Component.translatable("fleshbone.minigame.saw.help"), parent, completion);
        this.skin = skin;
    }

    @Override
    protected void init() {
        super.init();
        tracker = new SawTracker(cy - 12, 30f, STROKES);
    }

    @Override
    protected void drawGame(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int y = cy - 12;
        g.fill(cx - 22, y - 40, cx + 22, y + 40, BodyDoll.tissueColor(skin));
        g.fill(cx - 24, y - 1, cx + 24, y + 1, 0x90FFD27A);
        int depth = Math.round(tracker.progress() * 44f);
        g.fill(cx - 22, y - 2, cx - 22 + depth, y + 2, 0xFF8A0E0E);
        if (holding) {
            g.fill(mouseX - 16, mouseY - 3, mouseX + 16, mouseY + 1, 0xFFB0B4BC);
            for (int tx = mouseX - 16; tx < mouseX + 16; tx += 3) g.fill(tx, mouseY + 1, tx + 2, mouseY + 3, 0xFF909498);
        }
        g.centeredText(font, Component.translatable("fleshbone.minigame.saw.status", Math.round(tracker.progress() * 100f)), cx, cy + 36, TEXT);
        if (tracker.done()) finish(tracker.quality(), 0);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            holding = true;
            tracker.sample((float) event.x(), (float) event.y());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (holding) tracker.sample((float) event.x(), (float) event.y());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        holding = false;
        return super.mouseReleased(event);
    }

    @Override
    protected void onTimeout() {
        // Half-done amputations are not a thing: stop, nothing happens
        onClose();
    }
}
