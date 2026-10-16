package com.fleshbone.sim.minigame;

/**
 * Suture minigame: stitch points alternate on both sides of the wound; click them in order. Precise clicks make a
 * clean suture, misplaced ones hurt and weaken it.
 */
public final class StitchSequence {
    private final float[] xs;
    private final float[] ys;
    private final float tolerance;
    private int next;
    private float accuracy;
    private int mistakes;

    /**
     * @param x0      left end of the wound
     * @param x1      right end of the wound
     * @param y       wound line height
     * @param spread  distance of the stitch points from the line
     */
    public StitchSequence(float x0, float x1, float y, float spread, int stitches, float tolerance) {
        int points = stitches * 2;
        xs = new float[points];
        ys = new float[points];
        for (int i = 0; i < points; i++) {
            xs[i] = x0 + (x1 - x0) * (i / 2 + 0.5f) / stitches + (i % 2 == 0 ? -2f : 2f);
            ys[i] = y + (i % 2 == 0 ? -spread : spread);
        }
        this.tolerance = tolerance;
    }

    /** @return whether the click landed on the next point */
    public boolean click(float x, float y) {
        if (done()) return false;
        float dx = x - xs[next];
        float dy = y - ys[next];
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        if (distance <= tolerance) {
            accuracy += 1f - 0.5f * (distance / tolerance);
            next++;
            return true;
        }
        mistakes++;
        return false;
    }

    public boolean done() {
        return next >= xs.length;
    }

    public int next() {
        return next;
    }

    public int points() {
        return xs.length;
    }

    public float x(int i) {
        return xs[i];
    }

    public float y(int i) {
        return ys[i];
    }

    public int mistakes() {
        return mistakes;
    }

    public float quality() {
        if (xs.length == 0) return 0f;
        return Math.max(0f, Math.min(1f, accuracy / xs.length - mistakes * 0.1f));
    }
}
