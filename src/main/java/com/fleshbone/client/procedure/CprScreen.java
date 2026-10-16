package com.fleshbone.client.procedure;

import com.fleshbone.client.ClientPatients;
import com.fleshbone.network.FBPayloads;
import com.fleshbone.sim.Body;
import com.fleshbone.sim.procedure.CprProcedure;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * CPR on another player, in cycles of 30 compressions and 2 breaths. The blood kept moving is sent to the server twice
 * a second with any ribs cracked since; it goes on until the heart restarts or the medic stops (Escape).
 */
public class CprScreen extends ProcedureScreen<CprProcedure> {
    private static final float HANDS_Y = 0.55f;
    private final int patientId;
    private float lastSent;
    private int sentCracks;

    public CprScreen(Screen parent, int patientId) {
        super("cpr", new CprProcedure(), parent, (q, a, m) -> { });
        this.patientId = patientId;
    }

    @Override
    protected void update(float t) {
        if (t - lastSent >= 0.5f) {
            lastSent = t;
            int cracks = procedure.mistakes().tear - sentCracks;
            sentCracks = procedure.mistakes().tear;
            ClientPacketDistributor.sendToServer(new FBPayloads.Cpr(patientId, procedure.perfusion(), cracks));
        }
        Body body = ClientPatients.body(patientId);
        if (body == null || (!body.vitals.cardiacArrest && body.vitals.respRate >= 2.5f)) close();
    }

    @Override
    protected void drawWork(GuiGraphicsExtractor g) {
        CprProcedure p = procedure;
        String step = p.stepKey();
        float rise = step.equals("breathe") ? 0.02f * Math.min(1f, p.breathHold()) : 0f;
        float squeeze = step.equals("compress") && p.holding() ? 0.03f * Math.min(1f, p.depth()) : 0f;
        // Patient seen from above
        disc(g, CprProcedure.HEAD[0], CprProcedure.HEAD[1], 0.08f, SKIN);
        rect(g, 0.3f - rise, 0.22f, 0.7f + rise, 0.95f, SKIN);
        rect(g, 0.3f - rise + squeeze, HANDS_Y - 0.1f, 0.7f + rise - squeeze, HANDS_Y + 0.1f, 0xFFCF9A74);
        if (step.equals("tilt")) ring(g, CprProcedure.HEAD[0], CprProcedure.HEAD[1], 0.1f, alpha(HEADER, 0.5f + 0.5f * pulse(5f)));
        if (step.equals("breathe")) {
            ring(g, CprProcedure.HEAD[0], CprProcedure.HEAD[1] + 0.03f, 0.03f, alpha(HEADER, 0.6f));
            rect(g, 0.35f, 0.04f, 0.35f + 0.3f * Math.min(1f, p.breathHold()), 0.055f, p.breathHold() >= 0.7f ? GOOD : WARN);
        }
        // Metronome at 110 a minute
        float beat = (time * 110f / 60f) % 1f;
        disc(g, 0.12f, 0.12f, 0.025f + 0.02f * (1f - beat), alpha(HEADER, 0.3f + 0.7f * (1f - beat)));
        // Depth gauge
        rect(g, 0.85f, 0.3f, 0.9f, 0.8f, 0xFF2A2420);
        rect(g, 0.85f, 0.3f + 0.5f * CprProcedure.DEPTH_MIN, 0.9f, 0.3f + 0.5f * CprProcedure.DEPTH_MAX, 0xFF2E5A24);
        rect(g, 0.85f, 0.3f + 0.5f * 0.75f, 0.9f, 0.3f + 0.5f * 0.76f, BAD);
        float depth = Math.min(1f, p.depth());
        int color = depth > 0.75f ? BAD : depth >= CprProcedure.DEPTH_MIN && depth <= CprProcedure.DEPTH_MAX ? GOOD : WARN;
        rect(g, 0.855f, 0.3f, 0.895f, 0.3f + 0.5f * depth, color);
        // Blood moved
        gauge(g, 0.1f, 0.9f, 0.8f, 0.94f, p.perfusion(), 1f, 0.6f, 1f, p.perfusion() > 0.6f ? GOOD : WARN);
    }

    @Override
    protected void drawTool(GuiGraphicsExtractor g, int x, int y) {
        if (procedure.stepKey().equals("compress")) {
            g.fill(x - 7, y - 4, x + 7, y + 4, 0xE0CF9A74);
            g.outline(x - 7, y - 4, 14, 8, 0xFF8A5A3A);
        } else {
            super.drawTool(g, x, y);
        }
    }

    @Override
    protected Component status() {
        CprProcedure p = procedure;
        Component perfusion = Component.translatable(key() + ".status.perfusion", percent(p.perfusion()));
        return switch (p.stepKey()) {
            case "compress" -> Component.translatable(key() + ".status.count", p.count(), CprProcedure.COMPRESSIONS).copy().append(" · ").append(perfusion);
            case "breathe" -> Component.translatable(key() + ".status.breaths", p.breaths()).copy().append(" · ").append(perfusion);
            default -> perfusion;
        };
    }

    /** CPR goes on as long as needed. */
    @Override
    protected float timeLimit() {
        return Float.MAX_VALUE;
    }

    @Override
    public void onClose() {
        close();
    }
}
