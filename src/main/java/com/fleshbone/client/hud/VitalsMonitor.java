package com.fleshbone.client.hud;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.Vitals;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Small vitals monitor shown while carrying one: scrolling ECG (sinus rhythm, ventricular tachycardia, fibrillation,
 * asystole, CPR artefacts), heart rate, blood pressure and SpO2.
 */
public final class VitalsMonitor {
    public static final int WIDTH = 76;
    public static final int HEIGHT = 38;
    private static final int TRACE_H = 18;

    private VitalsMonitor() {}

    public static void draw(GuiGraphicsExtractor g, Font font, Body body, int x, int y, float time) {
        Vitals v = body.vitals;
        g.fill(x, y, x + WIDTH, y + HEIGHT, 0xD0081008);
        g.outline(x, y, WIDTH, HEIGHT, 0xFF2E4A2E);
        int traceColor = v.cardiacArrest || v.fibrillation > 50f ? 0xFFFF5040 : 0xFF50FF70;
        int baseY = y + 3 + TRACE_H / 2;
        int previous = baseY;
        for (int i = 0; i < WIDTH - 4; i++) {
            float t = time - (WIDTH - 4 - i) * 0.025f;
            int py = baseY - Math.round(wave(v, t) * (TRACE_H / 2f - 1));
            int px = x + 2 + i;
            g.fill(px, Math.min(previous, py), px + 1, Math.max(previous, py) + 1, traceColor);
            previous = py;
        }
        String hr = v.cardiacArrest ? "---" : String.valueOf(Math.round(v.heartRate));
        g.text(font, "♥" + hr, x + 3, y + HEIGHT - 17, traceColor, false);
        g.text(font, Math.round(v.systolic) + "/" + Math.round(v.diastolic), x + 36, y + HEIGHT - 17, 0xFFE0E0E0, false);
        g.text(font, "O2 " + Math.round(v.spo2) + "%", x + 3, y + HEIGHT - 9, v.spo2 < 85f ? 0xFFFF6050 : 0xFF70C0FF, false);
        g.text(font, String.format(java.util.Locale.ROOT, "%.1f°", v.bodyTemp), x + 44, y + HEIGHT - 9, 0xFFE0C080, false);
    }

    /** ECG amplitude (-1..1) at time {@code t}. */
    static float wave(Vitals v, float t) {
        if (v.cardiacArrest) {
            if (v.cprPerfusion > 0.3f) return (float) Math.sin(t * 2 * Math.PI * 110f / 60f) * 0.6f;
            // Fibrillation for the first minute, then flat line
            if (v.arrestTime < 60f) return noise(t) * 0.7f * (1f - v.arrestTime / 60f);
            return 0f;
        }
        if (v.fibrillation > 75f) return noise(t) * 0.8f;
        if (v.fibrillation > 50f) return (float) Math.sin(t * 2 * Math.PI * 3.2f) * 0.85f;
        float period = 60f / Math.max(20f, v.heartRate);
        float phase = (t % period) / period;
        // P wave, QRS complex, T wave
        float value = bump(phase, 0.12f, 0.035f) * 0.15f;
        value += bump(phase, 0.24f, 0.008f) * -0.2f;
        value += bump(phase, 0.26f, 0.012f) * 1f;
        value += bump(phase, 0.28f, 0.008f) * -0.35f;
        value += bump(phase, 0.5f, 0.05f) * 0.3f;
        if (v.fibrillation > 15f) value += noise(t) * v.fibrillation / 200f;
        return Math.max(-1f, Math.min(1f, value));
    }

    private static float bump(float phase, float center, float width) {
        float d = (phase - center) / width;
        return (float) Math.exp(-d * d);
    }

    private static float noise(float t) {
        return (float) (Math.sin(t * 37.1) * 0.5 + Math.sin(t * 23.7 + 1.3) * 0.3 + Math.sin(t * 61.3 + 2.1) * 0.2);
    }
}
