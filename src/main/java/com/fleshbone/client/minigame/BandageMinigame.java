package com.fleshbone.client.minigame;

import com.fleshbone.client.hud.BodyDoll;
import com.fleshbone.sim.minigame.WrapTracker;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Hold the left button and circle around the limb to wrap the dressing; right-click (or Enter) to tie it off.
 * Four to six neat turns make a perfect dressing; each turn uses a bit of the roll.
 */
public class BandageMinigame extends MinigameScreen {
    private static final int LIMB_RADIUS = 20;
    private static final int RING_RADIUS = 38;
    private final float skin;
    private final int maxWraps;
    private WrapTracker tracker;
    private boolean holding;

    /**
     * @param skin     skin health of the part, for colouring the limb
     * @param maxWraps roll length left (rags: as many as wanted)
     */
    public BandageMinigame(Screen parent, Completion completion, float skin, int maxWraps) {
        super(Component.translatable("fleshbone.minigame.bandage"), Component.translatable("fleshbone.minigame.bandage.help"), parent, completion);
        this.skin = skin;
        this.maxWraps = Math.max(1, maxWraps);
    }

    @Override
    protected void init() {
        super.init();
        tracker = new WrapTracker(cx, cy - 12, RING_RADIUS);
    }

    @Override
    protected void drawGame(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = cx;
        int y = cy - 12;
        disc(g, x, y, LIMB_RADIUS, BodyDoll.tissueColor(skin));
        disc(g, x + 4, y - 3, 6, 0xFFB01818);
        int wraps = Math.min(tracker.wraps(), 8);
        for (int i = 0; i < wraps; i++) {
            int r = LIMB_RADIUS + 1 + i;
            dottedRing(g, x, y, r, 0xFFF0EEE6, 120);
        }
        dottedRing(g, x, y, RING_RADIUS, 0x80FFD27A, 48);
        // Current turn in progress
        int arcDots = Math.round(tracker.currentWrapProgress() * 48);
        for (int i = 0; i < arcDots; i++) {
            double a = i * 2 * Math.PI / 48;
            int px = x + (int) Math.round(Math.cos(a) * RING_RADIUS);
            int py = y + (int) Math.round(Math.sin(a) * RING_RADIUS);
            g.fill(px - 1, py - 1, px + 1, py + 1, 0xFFFFFFFF);
        }
        if (holding) g.fill(mouseX - 3, mouseY - 2, mouseX + 3, mouseY + 2, 0xFFF0EEE6);
        int quality = Math.round(tracker.quality() * 100f);
        g.centeredText(font, Component.translatable("fleshbone.minigame.bandage.status", tracker.wraps(), maxWraps, quality),
                cx, cy + 36, tracker.wraps() >= 4 ? GOOD : TEXT);
        if (tracker.wraps() >= maxWraps) finish(tracker.quality(), tracker.lengthUsed());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            holding = true;
            tracker.sample((float) event.x(), (float) event.y());
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            tieOff();
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
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            tieOff();
            return true;
        }
        return super.keyPressed(event);
    }

    private void tieOff() {
        if (tracker.wraps() >= 1) finish(tracker.quality(), tracker.lengthUsed());
    }

    @Override
    protected void onTimeout() {
        if (tracker.wraps() >= 1) finish(tracker.quality() * 0.8f, tracker.lengthUsed());
        else onClose();
    }
}
