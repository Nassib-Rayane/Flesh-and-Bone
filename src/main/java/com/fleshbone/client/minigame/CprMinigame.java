package com.fleshbone.client.minigame;

import com.fleshbone.client.ClientPatients;
import com.fleshbone.network.FBPayloads;
import com.fleshbone.sim.Body;
import com.fleshbone.sim.minigame.RhythmTracker;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * CPR on another player: press in rhythm with the pulse (110 per minute). The blood you keep moving is sent to the
 * server twice a second. Keep going until the heart restarts or help arrives; Escape to stop.
 */
public class CprMinigame extends MinigameScreen {
    private final int patientId;
    private final RhythmTracker rhythm = new RhythmTracker();
    private float lastSent;
    private float lastPress = -10f;
    private float lastAccuracy;

    public CprMinigame(Screen parent, int patientId) {
        super(Component.translatable("fleshbone.minigame.cpr"), Component.translatable("fleshbone.minigame.cpr.help"), parent, (q, a) -> { });
        this.patientId = patientId;
        boxH = 210;
    }

    @Override
    protected void drawGame(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        float t = time();
        float beat = 60f / RhythmTracker.TARGET_BPM;
        float phase = (t % beat) / beat;
        int pulse = 18 + Math.round((1f - phase) * 8f);
        disc(g, cx, cy - 14, pulse, 0x40FFD27A);
        float pressed = Math.max(0f, 1f - (t - lastPress) * 6f);
        disc(g, cx, cy - 14, 14 - Math.round(pressed * 4f), pressed > 0f ? (lastAccuracy > 0.6f ? GOOD : 0xFFE0B040) : 0xFF8A3020);
        float perfusion = rhythm.perfusion(t);
        g.fill(cx - 60, cy + 22, cx + 60, cy + 26, 0xFF2A2420);
        g.fill(cx - 60, cy + 22, cx - 60 + Math.round(120 * perfusion), cy + 26, perfusion > 0.6f ? GOOD : 0xFFE0B040);
        g.centeredText(font, Component.translatable("fleshbone.minigame.cpr.status", Math.round(perfusion * 100f)), cx, cy + 30, TEXT);

        if (t - lastSent >= 0.5f) {
            lastSent = t;
            ClientPacketDistributor.sendToServer(new FBPayloads.Cpr(patientId, perfusion));
        }
        Body body = ClientPatients.body(patientId);
        if (body == null || (!body.vitals.cardiacArrest && body.vitals.respRate >= 2.5f)) onClose();
    }

    private void compress() {
        lastPress = time();
        lastAccuracy = rhythm.compress(lastPress);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            compress();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_SPACE) {
            compress();
            return true;
        }
        return super.keyPressed(event);
    }

    /** CPR goes on as long as needed: the timer only marks rounds. */
    @Override
    protected float timeLimit() {
        return Float.MAX_VALUE;
    }

    @Override
    protected void onTimeout() {
    }
}
