package com.fleshbone.client.procedure;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.Vitals;
import com.fleshbone.sim.procedure.DefibProcedure;
import com.fleshbone.sim.procedure.DefibProcedure.Rhythm;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Place the pads, read the rhythm, decide, pick the energy, charge, shock. */
public class DefibScreen extends ProcedureScreen<DefibProcedure> {
    private static final float PAD = 0.05f;
    private final float heartRate;

    public DefibScreen(Screen parent, Completion completion, Body patient) {
        this(parent, completion, rhythmOf(patient.vitals), patient.vitals.heartRate);
    }

    private DefibScreen(Screen parent, Completion completion, Rhythm rhythm, float heartRate) {
        super("defib", new DefibProcedure(rhythm, rhythm == Rhythm.TACHYCARDIA ? Math.max(170f, heartRate) : heartRate), parent, completion);
        this.heartRate = rhythm == Rhythm.TACHYCARDIA ? Math.max(170f, heartRate) : Math.max(40f, heartRate);
    }

    /**
     * What the monitor shows: a heart stopped for over a minute without CPR has gone flat; a stopped or badly
     * fibrillating heart quivers; a fibrillating one still pumping races.
     */
    public static Rhythm rhythmOf(Vitals v) {
        if (v.cardiacArrest) return v.arrestTime > 60f && v.cprPerfusion < 0.3f ? Rhythm.ASYSTOLE : Rhythm.FIBRILLATION;
        if (v.fibrillation > 75f) return Rhythm.FIBRILLATION;
        if (v.fibrillation > 50f) return Rhythm.TACHYCARDIA;
        return Rhythm.SINUS;
    }

    @Override
    protected void drawWork(GuiGraphicsExtractor g) {
        DefibProcedure p = procedure;
        // Monitor strip
        rect(g, 0.03f, 0.02f, 0.97f, 0.16f, 0xFF081008);
        int x0 = px(0.04f);
        int x1 = px(0.96f);
        int mid = py(0.09f);
        int amp = size(0.055f);
        int lastY = mid;
        for (int x = x0; x < x1; x++) {
            float t = time - (x1 - x) / 80f;
            int y = mid - Math.round(wave(p.rhythm, t) * amp);
            g.fill(x, Math.min(y, lastY), x + 1, Math.max(y, lastY) + 1, 0xFF60FF70);
            lastY = y;
        }
        // Chest
        rect(g, 0.2f, 0.2f, 0.8f, 0.8f, SKIN);
        rect(g, 0.22f, 0.24f, 0.46f, 0.255f, SKIN_DARK);
        rect(g, 0.54f, 0.24f, 0.78f, 0.255f, SKIN_DARK);
        rect(g, 0.495f, 0.26f, 0.505f, 0.6f, SKIN_DARK);
        disc(g, 0.37f, 0.48f, 0.01f, SKIN_DARK);
        disc(g, 0.63f, 0.48f, 0.01f, SKIN_DARK);
        if (p.stepKey().equals("pads")) {
            ring(g, DefibProcedure.PAD_TARGET_A[0], DefibProcedure.PAD_TARGET_A[1], PAD * 1.3f, alpha(HEADER, 0.25f));
            ring(g, DefibProcedure.PAD_TARGET_B[0], DefibProcedure.PAD_TARGET_B[1], PAD * 1.3f, alpha(HEADER, 0.25f));
        }
        pad(g, p.padA, p.placedA());
        pad(g, p.padB, p.placedB());
        // Charge / shock button
        String step = p.stepKey();
        if (step.equals("charge") || step.equals("shock")) {
            float bx = DefibProcedure.CHARGE_BUTTON[0];
            float by = DefibProcedure.CHARGE_BUTTON[1];
            boolean shock = step.equals("shock");
            disc(g, bx, by, 0.06f, shock ? alpha(BAD, 0.6f + 0.4f * pulse(10f)) : 0xFFE0B040);
            g.centeredText(font, Component.translatable(key() + (shock ? ".button.shock" : ".button.charge")), px(bx), py(by) - 4, 0xFF000000);
            if (!shock) rect(g, 0.3f, 0.83f, 0.3f + 0.4f * Math.min(1f, p.charge()), 0.845f, GOOD);
        }
    }

    private void pad(GuiGraphicsExtractor g, float[] pad, boolean placed) {
        rect(g, pad[0] - PAD, pad[1] - PAD * 0.7f, pad[0] + PAD, pad[1] + PAD * 0.7f, 0xFFE8E8E8);
        g.outline(px(pad[0] - PAD), py(pad[1] - PAD * 0.7f), size(PAD * 2f), size(PAD * 1.4f), placed ? GOOD : 0xFF808080);
    }

    /** Monitor trace, −1 to 1. */
    private float wave(Rhythm rhythm, float t) {
        return switch (rhythm) {
            case SINUS -> {
                float phase = (t * heartRate / 60f) % 1f;
                if (phase < 0f) phase += 1f;
                if (phase < 0.1f) yield 0.15f * (float) Math.sin(phase / 0.1f * Math.PI);
                if (phase < 0.16f) yield 0f;
                if (phase < 0.18f) yield -0.2f;
                if (phase < 0.21f) yield 1f;
                if (phase < 0.23f) yield -0.35f;
                if (phase < 0.32f) yield 0f;
                if (phase < 0.46f) yield 0.25f * (float) Math.sin((phase - 0.32f) / 0.14f * Math.PI);
                yield 0f;
            }
            case TACHYCARDIA -> {
                float phase = (t * heartRate / 60f) % 1f;
                yield 0.8f * (float) Math.sin(phase * 2 * Math.PI) * (phase < 0.5f ? 1f : 0.6f);
            }
            case FIBRILLATION -> 0.35f * (float) (Math.sin(t * 31) + 0.6 * Math.sin(t * 47 + 1) + 0.4 * Math.sin(t * 71 + 2) + 0.3 * Math.sin(t * 13));
            case ASYSTOLE -> 0.03f * (float) Math.sin(t * 1.3);
        };
    }

    @Override
    protected Component[] choices() {
        return switch (procedure.stepKey()) {
            case "analyze" -> new Component[] {Component.translatable(key() + ".choice.shock"), Component.translatable(key() + ".choice.no_shock")};
            case "energy" -> new Component[] {Component.literal("150 J"), Component.literal("200 J"), Component.literal("360 J")};
            default -> null;
        };
    }

    @Override
    protected Component status() {
        DefibProcedure p = procedure;
        return switch (p.stepKey()) {
            case "charge" -> Component.translatable(key() + ".status.charge", percent(Math.min(1f, p.charge())));
            case "shock" -> Component.translatable(key() + ".status.clear");
            default -> null;
        };
    }

    /** Deciding not to shock uses nothing. */
    @Override
    protected void onDone() {
        if (procedure.noShock()) close();
        else super.onDone();
    }

    @Override
    protected void onTimeout() {
        close();
    }

    @Override
    public void onClose() {
        close();
    }

    @Override
    protected float timeLimit() {
        return 40f;
    }
}
