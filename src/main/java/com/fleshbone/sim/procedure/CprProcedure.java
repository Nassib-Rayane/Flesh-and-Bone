package com.fleshbone.sim.procedure;

/**
 * CPR in cycles of 30 compressions and 2 breaths. A compression is a press dragged down then released: the drag
 * depth should land in the 5–6 cm band (0.5–0.6 of the gauge), at 100–120 a minute. After 30, tilt the head (press
 * on it) and give two breaths (hold on the mouth about a second each until the chest rises). The blood kept moving
 * (perfusion, 0–1) follows the quality of recent compressions and fades during pauses. Runs until the patient
 * recovers or the medic stops.
 */
public final class CprProcedure extends Procedure {
    private static final String[] STEPS = {"compress", "tilt", "breathe"};
    public static final int COMPRESSIONS = 30;
    public static final float DEPTH_MIN = 0.5f;
    public static final float DEPTH_MAX = 0.6f;
    public static final float[] HEAD = {0.5f, 0.12f};
    public static final float BREATH_SECONDS = 1f;

    private float pressY;
    private float depth;
    private int count;
    private float lastCompression = -10f;
    private float recentScore;
    private float perfusion;
    private int breaths;
    private float breathHold;
    private float lastScore;

    @Override
    public String[] steps() {
        return STEPS;
    }

    public float depth() { return depth; }

    public int count() { return count; }

    public int breaths() { return breaths; }

    public float breathHold() { return breathHold / BREATH_SECONDS; }

    public float perfusion() { return perfusion; }

    public float lastScore() { return lastScore; }

    @Override
    protected void onPress(float x, float y, float t) {
        switch (stepKey()) {
            case "compress" -> {
                pressY = y;
                depth = 0f;
            }
            case "tilt" -> {
                if (dist(x, y, HEAD[0], HEAD[1]) < 0.1f) advance(t);
            }
            default -> breathHold = 0f;
        }
    }

    @Override
    protected void onDrag(float x, float y, float t) {
        if (stepKey().equals("compress")) depth = Math.max(0f, (y - pressY) / 0.4f);
    }

    @Override
    protected void onRelease(float x, float y, float t) {
        switch (stepKey()) {
            case "compress" -> compression(t);
            case "breathe" -> {
                if (breathHold >= BREATH_SECONDS * 0.7f) breaths++;
                breathHold = 0f;
                if (breaths >= 2) {
                    breaths = 0;
                    count = 0;
                    goTo(0, t);
                }
            }
            default -> { }
        }
    }

    private void compression(float t) {
        float depthScore = depth >= DEPTH_MIN && depth <= DEPTH_MAX ? 1f
                : depth < DEPTH_MIN ? clamp01(depth / DEPTH_MIN) * 0.8f : clamp01(1f - (depth - DEPTH_MAX) / 0.3f);
        float interval = t - lastCompression;
        float rate = interval > 0f ? 60f / interval : 0f;
        float rateScore = rate >= 100f && rate <= 120f ? 1f : rate < 100f ? clamp01(rate / 100f) * 0.7f : clamp01(1f - (rate - 120f) / 60f);
        if (interval > 2f) rateScore = 0.5f;
        lastScore = depthScore * rateScore;
        if (depth > 0.75f) mistakes.tear++; // Cracking ribs
        recentScore = recentScore * 0.75f + lastScore * 0.25f;
        lastCompression = t;
        depth = 0f;
        if (++count >= COMPRESSIONS) advance(t);
    }

    @Override
    protected void onTick(float t, float dt) {
        if (stepKey().equals("breathe") && holding) breathHold += dt;
        boolean pumping = stepKey().equals("compress") && t - lastCompression < 1.5f;
        float target = pumping ? recentScore : 0f;
        perfusion += (target - perfusion) * Math.min(1f, dt * (pumping ? 2f : 0.25f));
    }

    @Override
    public float quality() {
        return perfusion;
    }
}
