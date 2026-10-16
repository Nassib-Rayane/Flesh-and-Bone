package com.fleshbone.sim.minigame;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * CPR: compressions must come at 100–120 per minute. The perfusion delivered is how many good compressions landed in
 * the last two seconds, compared with the ideal.
 */
public final class RhythmTracker {
    public static final float TARGET_BPM = 110f;
    private static final float WINDOW = 2f;
    private final Deque<float[]> recent = new ArrayDeque<>();
    private float lastTime = Float.NaN;

    /** Records a compression; returns its timing accuracy 0–1. */
    public float compress(float time) {
        float accuracy = 0.5f;
        if (!Float.isNaN(lastTime)) {
            float interval = time - lastTime;
            float target = 60f / TARGET_BPM;
            float error = Math.abs(interval - target) / target;
            accuracy = Math.max(0f, 1f - error * 2.5f);
        }
        lastTime = time;
        recent.addLast(new float[] {time, accuracy});
        trim(time);
        return accuracy;
    }

    /** Perfusion 0–1 delivered by the recent compressions. */
    public float perfusion(float time) {
        trim(time);
        float expected = WINDOW * TARGET_BPM / 60f;
        float sum = 0f;
        for (float[] c : recent) sum += c[1];
        return Math.min(1f, sum / expected);
    }

    private void trim(float time) {
        while (!recent.isEmpty() && time - recent.peekFirst()[0] > WINDOW) recent.removeFirst();
    }
}
