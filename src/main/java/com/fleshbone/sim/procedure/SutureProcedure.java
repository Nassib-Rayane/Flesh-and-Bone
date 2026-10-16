package com.fleshbone.sim.procedure;

/**
 * Stitching a wound shut. The wound runs across the work area; each stitch is three gestures:
 * <ol>
 *   <li><b>Pierce</b> the upper edge, next to the guide mark (stabbing beside the edge tears the skin).</li>
 *   <li><b>Pass</b> the needle under the wound and come out on the lower edge, straight across (hold the button):
 *       too fast and the stitch is shallow and will not hold, too slow and it goes too deep and hurts; coming out
 *       anywhere else tears the skin and the stitch starts over.</li>
 *   <li><b>Pull</b> the thread to close the edges: pull up and release in the right tension band; too hard tears the
 *       skin, too soft leaves a gap.</li>
 * </ol>
 * Then a <b>knot</b>: three tugs at an even pace.
 */
public final class SutureProcedure extends Procedure {
    private static final String[] STEPS = {"pierce", "pass", "pull", "knot"};
    public static final float START = 0.2f;
    public static final float END = 0.8f;
    public static final float EDGE = 0.07f;
    public static final float EDGE_TOLERANCE = 0.035f;
    public static final float SHALLOW_SECONDS = 0.25f;
    public static final float DEEP_SECONDS = 1.3f;

    public final int stitchCount;
    private int stitch;
    private final float[] stitchQuality;
    private float spacingError;
    private float entryX;
    private float passStart;
    private boolean shallow;
    private float pullStartY;
    private float tension;
    private int knotTugs;
    private float lastTug = -10f;
    private boolean knotBad;

    /** @param skin skin health: the more torn, the more stitches */
    public SutureProcedure(float skin) {
        stitchCount = Math.max(3, Math.min(7, Math.round((100f - skin) / 15f)));
        stitchQuality = new float[stitchCount];
    }

    @Override
    public String[] steps() {
        return STEPS;
    }

    /** The wound line, slightly curved. */
    public static float woundY(float x) {
        return 0.5f + 0.05f * (float) Math.sin((x - START) / (END - START) * Math.PI * 1.3);
    }

    /** Ideal position of stitch {@code i}. */
    public float stitchX(int i) {
        return START + (i + 0.5f) * (END - START) / stitchCount;
    }

    public int stitch() { return stitch; }

    public float entryX() { return entryX; }

    public float tension() { return tension; }

    public int knotTugs() { return knotTugs; }

    public float stitchQuality(int i) { return stitchQuality[i]; }

    @Override
    protected void onPress(float x, float y, float t) {
        switch (stepKey()) {
            case "pierce" -> {
                if (Math.abs(y - (woundY(x) - EDGE)) > EDGE_TOLERANCE) {
                    mistakes.tear++;
                    return;
                }
                entryX = x;
                spacingError += Math.abs(x - stitchX(stitch));
                passStart = t;
                advance(t);
            }
            case "pull" -> {
                pullStartY = y;
                tension = 0f;
            }
            case "knot" -> {
                if (knotTugs > 0) {
                    float interval = t - lastTug;
                    if (interval < 0.25f || interval > 0.9f) knotBad = true;
                }
                lastTug = t;
                if (++knotTugs >= 3) advance(t);
            }
            default -> { }
        }
    }

    @Override
    protected void onDrag(float x, float y, float t) {
        if (stepKey().equals("pull")) tension = Math.max(0f, (pullStartY - y) / 0.25f);
    }

    @Override
    protected void onRelease(float x, float y, float t) {
        switch (stepKey()) {
            case "pass" -> {
                boolean onEdge = Math.abs(y - (woundY(x) + EDGE)) <= EDGE_TOLERANCE;
                boolean straight = Math.abs(x - entryX) <= 0.04f;
                if (!onEdge || !straight) {
                    mistakes.tear++;
                    goTo(0, t);
                    return;
                }
                float duration = t - passStart;
                shallow = duration < SHALLOW_SECONDS;
                if (duration > DEEP_SECONDS) mistakes.pain++;
                advance(t);
            }
            case "pull" -> {
                float q = shallow ? 0.6f : 1f;
                if (tension > 1.05f) {
                    mistakes.tear++;
                    q *= 0.5f;
                } else if (tension < 0.4f) {
                    q *= 0.6f;
                } else if (tension < 0.55f || tension > 0.95f) {
                    q *= 0.85f;
                }
                stitchQuality[stitch] = q;
                stitch++;
                tension = 0f;
                if (stitch < stitchCount) goTo(0, t);
                else advance(t);
            }
            default -> { }
        }
    }

    @Override
    public float quality() {
        float sum = 0f;
        for (int i = 0; i < stitch; i++) sum += stitchQuality[i];
        float stitches = sum / stitchCount;
        float spacing = 1f - Math.min(0.4f, spacingError / Math.max(1, stitch) / 0.05f * 0.3f);
        return clamp01(stitches * spacing * (knotTugs >= 3 && !knotBad ? 1f : 0.9f));
    }
}
