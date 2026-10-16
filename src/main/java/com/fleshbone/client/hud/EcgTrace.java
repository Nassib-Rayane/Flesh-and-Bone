package com.fleshbone.client.hud;

import java.util.HashMap;
import java.util.Map;

import com.fleshbone.sim.Vitals;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

/**
 * A scrolling ECG strip that behaves like a real monitor: the beat advances with the heart rate (a new rate never makes
 * the trace jump), samples are written at a fixed rate into a ring buffer, and each pixel column shows the extremes of
 * its samples so the QRS spike is never skipped.
 */
public final class EcgTrace {
    private static final float SAMPLE_RATE = 125f;
    private static final int SIZE = 1024;
    /** Seconds of signal across the strip. */
    private static final float WINDOW = 2.6f;
    private static final EcgTrace LOCAL = new EcgTrace();
    private static final Map<Integer, EcgTrace> PATIENTS = new HashMap<>();

    private final float[] samples = new float[SIZE];
    private int head;
    private float phase;
    private float clock;
    private float pending;
    private long lastMillis = -1;

    /** The local player's strip. */
    public static EcgTrace local() {
        return LOCAL;
    }

    /** The strip of an examined patient (entity id). */
    public static EcgTrace patient(int id) {
        if (PATIENTS.size() > 16) PATIENTS.clear();
        return PATIENTS.computeIfAbsent(id, key -> new EcgTrace());
    }

    /** Advances the strip to now with the current vitals. */
    public void update(Vitals v) {
        long now = Util.getMillis();
        if (lastMillis < 0 || now - lastMillis > 2000) {
            lastMillis = now;
            return;
        }
        pending += (now - lastMillis) / 1000f * SAMPLE_RATE;
        lastMillis = now;
        float dt = 1f / SAMPLE_RATE;
        while (pending >= 1f) {
            pending -= 1f;
            clock += dt;
            samples[head] = next(v, dt);
            head = (head + 1) % SIZE;
        }
    }

    private float next(Vitals v, float dt) {
        if (v.cardiacArrest) {
            if (v.cprPerfusion > 0.3f) {
                // Compression artefacts at about 110 a minute
                phase = (phase + dt * 110f / 60f) % 1f;
                return (float) Math.sin(phase * 2 * Math.PI) * 0.6f;
            }
            // Fibrillation for the first minute, fading to a flat line (asystole)
            return v.arrestTime < 60f ? noise(clock) * 0.7f * (1f - v.arrestTime / 60f) : 0f;
        }
        if (v.fibrillation > 75f) return noise(clock) * 0.8f;
        float rate = Math.max(20f, v.heartRate);
        phase += dt * rate / 60f;
        if (phase >= 1f) phase -= (float) Math.floor(phase);
        if (v.fibrillation > 50f) return (float) Math.sin(phase * 2 * Math.PI) * 0.85f;
        float period = 60f / rate;
        float t = phase * period;
        // Waves at fixed durations, squeezed only when the heart races
        float k = Math.min(1f, period / 0.6f);
        float value = bump(t, 0.10f * k, 0.035f * k) * 0.15f
                + bump(t, 0.20f * k, 0.010f) * -0.2f
                + bump(t, 0.22f * k, 0.012f) * 1f
                + bump(t, 0.245f * k, 0.010f) * -0.35f
                + bump(t, 0.42f * k, 0.055f * k) * 0.3f;
        if (v.fibrillation > 15f) value += noise(clock) * v.fibrillation / 200f;
        return Math.max(-1f, Math.min(1f, value));
    }

    /** Draws the last seconds of signal. */
    public void draw(GuiGraphicsExtractor g, int x, int y, int width, int height, int color) {
        if (width <= 0) return;
        int count = Math.min(SIZE - 1, Math.round(WINDOW * SAMPLE_RATE));
        float perColumn = count / (float) width;
        int mid = y + height / 2;
        float half = height / 2f - 1f;
        int previous = mid;
        for (int col = 0; col < width; col++) {
            int from = Math.round(col * perColumn);
            int to = Math.max(from + 1, Math.round((col + 1) * perColumn));
            float min = 1f;
            float max = -1f;
            for (int s = from; s < to; s++) {
                float value = samples[Math.floorMod(head - count + s, SIZE)];
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
            int top = mid - Math.round(max * half);
            int bottom = mid - Math.round(min * half);
            int y0 = Math.min(Math.min(top, bottom), previous);
            int y1 = Math.max(Math.max(top, bottom), previous);
            g.fill(x + col, y0, x + col + 1, y1 + 1, color);
            previous = mid - Math.round((min + max) * 0.5f * half);
        }
    }

    private static float bump(float t, float center, float width) {
        float d = (t - center) / width;
        return (float) Math.exp(-d * d);
    }

    private static float noise(float t) {
        return (float) (Math.sin(t * 37.1) * 0.5 + Math.sin(t * 23.7 + 1.3) * 0.3 + Math.sin(t * 61.3 + 2.1) * 0.2);
    }
}
