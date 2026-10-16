package com.fleshbone.sim.minigame;

/**
 * Amputation minigame: saw back and forth across the cut line. Each long enough stroke cuts deeper; straying from the
 * line makes a ragged, bloodier cut.
 */
public final class SawTracker {
    private final float lineY;
    private final float minStroke;
    private final int strokesNeeded;
    private int strokes;
    private float strokeStartX = Float.NaN;
    private float lastX = Float.NaN;
    private int direction;
    private float deviation;
    private int samples;

    public SawTracker(float lineY, float minStroke, int strokesNeeded) {
        this.lineY = lineY;
        this.minStroke = minStroke;
        this.strokesNeeded = strokesNeeded;
    }

    public void sample(float x, float y) {
        deviation += Math.abs(y - lineY);
        samples++;
        if (Float.isNaN(lastX)) {
            lastX = x;
            strokeStartX = x;
            return;
        }
        int dir = Float.compare(x, lastX);
        if (dir != 0 && direction != 0 && dir != direction) {
            // Turning around ends a stroke
            if (Math.abs(lastX - strokeStartX) >= minStroke) strokes++;
            strokeStartX = lastX;
        }
        if (dir != 0) direction = dir;
        lastX = x;
    }

    public int strokes() {
        return strokes;
    }

    public float progress() {
        return Math.min(1f, strokes / (float) strokesNeeded);
    }

    public boolean done() {
        return strokes >= strokesNeeded;
    }

    /** Average distance from the line, in pixels. */
    public float averageDeviation() {
        return samples == 0 ? 0f : deviation / samples;
    }

    public float quality() {
        return Math.max(0.3f, Math.min(1f, 1f - averageDeviation() / 20f));
    }
}
