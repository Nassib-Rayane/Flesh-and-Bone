package com.fleshbone.client.minigame;

import java.util.concurrent.ThreadLocalRandom;

import com.fleshbone.sim.minigame.TimingGauge;
import com.fleshbone.sim.treatment.Treatments;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Pull and rotate: click (or press space) when the needle crosses the green zone. Pain narrows the zone. */
public class ResetMinigame extends MinigameScreen {
    private static final int GAUGE_W = 200;
    private final float pain;
    private TimingGauge gauge;

    public ResetMinigame(Screen parent, Completion completion, float pain) {
        super(Component.translatable("fleshbone.minigame.reset"), Component.translatable("fleshbone.minigame.reset.help"), parent, completion);
        this.pain = pain;
    }

    @Override
    protected void init() {
        super.init();
        gauge = new TimingGauge(1.6f, 0.25f + ThreadLocalRandom.current().nextFloat() * 0.5f, pain);
    }

    @Override
    protected void drawGame(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x0 = cx - GAUGE_W / 2;
        int y = cy - 16;
        g.fill(x0, y, x0 + GAUGE_W, y + 12, 0xFF2A2420);
        int zone0 = x0 + Math.round((gauge.zoneCenter() - gauge.zoneHalfWidth()) * GAUGE_W);
        int zone1 = x0 + Math.round((gauge.zoneCenter() + gauge.zoneHalfWidth()) * GAUGE_W);
        int edge = Math.round(gauge.zoneHalfWidth() * GAUGE_W);
        g.fill(zone0 - edge, y, zone1 + edge, y + 12, 0xFF6A5A20);
        g.fill(zone0, y, zone1, y + 12, 0xFF3E8A2E);
        int needle = x0 + Math.round(gauge.needle(time()) * GAUGE_W);
        g.fill(needle - 1, y - 4, needle + 2, y + 16, 0xFFFFFFFF);
        if (pain > Treatments.RESET_PAIN_LIMIT) {
            g.centeredText(font, Component.translatable("fleshbone.minigame.reset.too_painful"), cx, y + 24, BAD);
        }
    }

    private void attempt() {
        finish(gauge.quality(time()), 0);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            attempt();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_SPACE) {
            attempt();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void onTimeout() {
        onClose();
    }
}
