package com.fleshbone.sim.procedure;

/**
 * Setting up a drip (blood or saline):
 * <ol>
 *   <li><b>Tourniquet</b>: tighten the band on the upper arm.</li>
 *   <li><b>Palpate</b>: feel along the forearm for the vein (the closer, the stronger the throb) and mark it.</li>
 *   <li><b>Needle</b>: seen from the side, drag the needle in from the mark at a shallow 15–30° angle and deep enough.
 *       Too steep goes through the vein (a bruise, painful); too flat stays under the skin. Three tries.</li>
 *   <li><b>Release</b> the tourniquet: the drip runs.</li>
 * </ol>
 */
public final class InfusionProcedure extends Procedure {
    private static final String[] STEPS = {"tourniquet", "palpate", "needle", "release"};
    public static final float[] BAND = {0.12f, 0.5f};
    public static final float ARM_TOP = 0.3f;
    public static final float ARM_BOTTOM = 0.7f;
    /** Skin line in the side view of the needle step. */
    public static final float SKIN_Y = 0.4f;
    public static final int TRIES = 3;

    public final float veinY;
    private float feel;
    private float markX;
    private float markY;
    private boolean inserting;
    private float needleAngle;
    private float needleDepth;
    private int failures;

    public InfusionProcedure(long seed) {
        veinY = 0.42f + new java.util.Random(seed).nextFloat() * 0.16f;
    }

    @Override
    public String[] steps() {
        return STEPS;
    }

    /** 0–1, how strongly the vein throbs under the finger. */
    public float feel() { return feel; }

    public float markX() { return markX; }

    public float markY() { return markY; }

    public float needleAngle() { return needleAngle; }

    public float needleDepth() { return needleDepth; }

    public int failures() { return failures; }

    @Override
    protected void onMove(float x, float y, float t) {
        if (stepKey().equals("palpate")) feel = x > 0.2f && x < 0.85f ? clamp01(1f - Math.abs(y - veinY) / 0.15f) : 0f;
    }

    @Override
    protected void onPress(float x, float y, float t) {
        switch (stepKey()) {
            case "tourniquet", "release" -> {
                if (dist(x, y, BAND[0], BAND[1]) < 0.08f) advance(t);
            }
            case "palpate" -> {
                onMove(x, y, t);
                if (Math.abs(y - veinY) <= 0.035f && x > 0.2f && x < 0.85f) {
                    markX = x;
                    markY = y;
                    advance(t);
                }
            }
            case "needle" -> inserting = dist(x, y, 0.3f, SKIN_Y) < 0.08f;
            default -> { }
        }
    }

    @Override
    protected void onDrag(float x, float y, float t) {
        if (!stepKey().equals("needle") || !inserting) return;
        float dx = x - 0.3f;
        float dy = y - SKIN_Y;
        needleAngle = dx > 0.001f ? (float) Math.toDegrees(Math.atan2(dy, dx)) : 90f;
        needleDepth = dist(x, y, 0.3f, SKIN_Y);
    }

    @Override
    protected void onRelease(float x, float y, float t) {
        if (!stepKey().equals("needle") || !inserting) return;
        inserting = false;
        if (needleDepth < 0.12f) return;
        if (needleAngle >= 12f && needleAngle <= 32f) {
            advance(t);
            return;
        }
        failures++;
        mistakes.pain++;
        if (needleAngle > 40f) mistakes.tear++;
        needleDepth = 0f;
        if (failures >= TRIES) abort();
    }

    @Override
    public float quality() {
        if (!succeeded()) return 0f;
        return Math.max(0.4f, 1f - 0.2f * failures);
    }
}
