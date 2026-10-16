package com.fleshbone.client.hud;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.Vitals;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Small vitals monitor (when the UNI-HEALTH readout is hidden): scrolling ECG, heart rate, blood pressure, SpO2 and
 * temperature.
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
        EcgTrace trace = EcgTrace.local();
        trace.update(v);
        trace.draw(g, x + 2, y + 3, WIDTH - 4, TRACE_H, traceColor);
        String hr = v.cardiacArrest ? "---" : String.valueOf(Math.round(v.heartRate));
        g.text(font, "♥" + hr, x + 3, y + HEIGHT - 17, traceColor, false);
        g.text(font, Math.round(v.systolic) + "/" + Math.round(v.diastolic), x + 36, y + HEIGHT - 17, 0xFFE0E0E0, false);
        g.text(font, "O2 " + Math.round(v.spo2) + "%", x + 3, y + HEIGHT - 9, v.spo2 < 85f ? 0xFFFF6050 : 0xFF70C0FF, false);
        g.text(font, String.format(java.util.Locale.ROOT, "%.1f°", v.bodyTemp), x + 44, y + HEIGHT - 9, 0xFFE0C080, false);
    }
}
