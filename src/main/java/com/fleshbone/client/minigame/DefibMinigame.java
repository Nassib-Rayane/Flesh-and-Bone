package com.fleshbone.client.minigame;

import com.fleshbone.sim.minigame.ChargeMeter;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Hold to charge the paddles, release in the top band to shock. "Clear!" */
public class DefibMinigame extends MinigameScreen {
    private static final int BAR_W = 180;
    private final ChargeMeter meter = new ChargeMeter(1.4f);

    public DefibMinigame(Screen parent, Completion completion) {
        super(Component.translatable("fleshbone.minigame.defib"), Component.translatable("fleshbone.minigame.defib.help"), parent, completion);
    }

    @Override
    protected void drawGame(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x0 = cx - BAR_W / 2;
        int y = cy - 16;
        g.fill(x0, y, x0 + BAR_W, y + 12, 0xFF2A2420);
        g.fill(x0 + Math.round(BAR_W * 0.85f / 1.3f), y, x0 + Math.round(BAR_W * 1f / 1.3f), y + 12, 0xFF3E8A2E);
        float charge = meter.charge(time());
        int fill = Math.round(BAR_W * Math.min(1.3f, charge) / 1.3f);
        int color = charge > 1f ? BAD : charge >= 0.85f ? GOOD : 0xFFE0B040;
        g.fill(x0, y + 3, x0 + fill, y + 9, color);
        g.centeredText(font, Component.translatable(meter.charging() ? "fleshbone.minigame.defib.charging" : "fleshbone.minigame.defib.ready",
                Math.round(Math.min(charge, 1.3f) * 200f)), cx, y + 20, TEXT);
    }

    private void press() {
        if (!meter.charging()) meter.press(time());
    }

    private void releaseShock() {
        if (meter.charging()) finish(meter.release(time()), 0);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            press();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) releaseShock();
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_SPACE) {
            press();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_SPACE) {
            releaseShock();
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    protected void onTimeout() {
        onClose();
    }
}
