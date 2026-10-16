package com.fleshbone.sim.procedure;

/**
 * Taking a limb off:
 * <ol>
 *   <li><b>Check</b> (only without a tourniquet above the cut): go on anyway, knowing it will bleed hard.</li>
 *   <li><b>Incise</b>: cut all the way round the limb, along the line (straying off it shreds the skin).</li>
 *   <li><b>Saw</b>: long back-and-forth strokes along the cut line; wandering off it splinters the bone and hurts.
 *       Strength saws deeper per stroke; every stroke tires the surgeon.</li>
 *   <li><b>Ligate</b>: tie each spurting vessel (hold on it); any left open keeps bleeding.</li>
 * </ol>
 */
public final class AmputationProcedure extends Procedure {
    private static final String[] WITH_TOURNIQUET = {"incise", "saw", "ligate"};
    private static final String[] WITHOUT_TOURNIQUET = {"check", "incise", "saw", "ligate"};
    public static final float CX = 0.5f;
    public static final float CY = 0.5f;
    public static final float RADIUS = 0.28f;
    public static final float LINE_TOLERANCE = 0.045f;
    public static final float SAW_LINE = 0.5f;
    public static final float SAW_DEPTH = 3f;
    public static final float LIGATE_SECONDS = 0.6f;
    public static final float LIGATE_TIME_LIMIT = 15f;

    private final String[] steps;
    private final float strength;
    private float incised;
    private float lastIncisionAngle = Float.NaN;
    private float offLine;
    private int shredded;
    private float sawX = Float.NaN;
    private float strokeStart;
    private int direction;
    private boolean splinteredThisStroke;
    private int strokes;
    private int splinters;
    private float depth;
    /** x, y, tie progress (≥ 1 tied). */
    public final float[][] vessels = {{0.42f, 0.45f, 0f}, {0.58f, 0.47f, 0f}, {0.5f, 0.6f, 0f}};
    private int untied;
    private float cursorX;
    private float cursorY;

    public AmputationProcedure(boolean tourniquet, float strength) {
        steps = tourniquet ? WITH_TOURNIQUET : WITHOUT_TOURNIQUET;
        this.strength = Math.max(0.5f, strength);
        if (!tourniquet) mistakes.tear += 2;
    }

    @Override
    public String[] steps() {
        return steps;
    }

    public float incised() { return incised / 340f; }

    public float depth() { return depth / SAW_DEPTH; }

    public int strokes() { return strokes; }

    @Override
    protected void onChoose(int option, float t) {
        if (stepKey().equals("check")) advance(t);
    }

    @Override
    protected void onPress(float x, float y, float t) {
        cursorX = x;
        cursorY = y;
        lastIncisionAngle = Float.NaN;
        sawX = Float.NaN;
    }

    @Override
    protected void onDrag(float x, float y, float t) {
        cursorX = x;
        cursorY = y;
        switch (stepKey()) {
            case "incise" -> {
                float r = dist(x, y, CX, CY);
                float a = (float) Math.toDegrees(Math.atan2(y - CY, x - CX));
                if (Math.abs(r - RADIUS) <= LINE_TOLERANCE) {
                    if (!Float.isNaN(lastIncisionAngle)) {
                        float d = Math.abs(a - lastIncisionAngle);
                        if (d > 180f) d = 360f - d;
                        if (d < 30f) incised += d;
                    }
                    lastIncisionAngle = a;
                } else {
                    lastIncisionAngle = Float.NaN;
                }
                if (incised >= 340f) advance(t);
            }
            case "saw" -> saw(x, y, t);
            default -> { }
        }
    }

    private void saw(float x, float y, float t) {
        if (Math.abs(y - SAW_LINE) > 0.06f && !splinteredThisStroke) {
            splinteredThisStroke = true;
            splinters++;
            mistakes.pain++;
        }
        if (Float.isNaN(sawX)) {
            sawX = x;
            strokeStart = x;
            direction = 0;
            return;
        }
        int dir = x > sawX + 0.002f ? 1 : x < sawX - 0.002f ? -1 : direction;
        if (direction != 0 && dir != direction) {
            float length = Math.abs(sawX - strokeStart);
            if (length >= 0.2f) {
                strokes++;
                depth += length * 0.9f * strength;
                splinteredThisStroke = false;
            }
            strokeStart = sawX;
        }
        direction = dir;
        sawX = x;
        if (depth >= SAW_DEPTH) advance(t);
    }

    @Override
    protected void onTick(float t, float dt) {
        if (stepKey().equals("incise") && holding && Math.abs(dist(cursorX, cursorY, CX, CY) - RADIUS) > LINE_TOLERANCE * 1.5f) {
            offLine += dt;
            if (offLine > 0.5f) {
                offLine = 0f;
                shredded++;
                mistakes.tear++;
            }
        }
        if (stepKey().equals("ligate")) {
            boolean all = true;
            for (float[] v : vessels) {
                if (v[2] < 1f && holding && dist(cursorX, cursorY, v[0], v[1]) < 0.05f) v[2] += dt / LIGATE_SECONDS;
                if (v[2] < 1f) all = false;
            }
            if (all) {
                advance(t);
            } else if (stepTime(t) > LIGATE_TIME_LIMIT) {
                for (float[] v : vessels) if (v[2] < 1f) untied++;
                mistakes.tear += untied * 2;
                advance(t);
            }
        }
    }

    @Override
    public int amount() {
        return strokes;
    }

    @Override
    public float quality() {
        return Math.max(0.1f, 1f - 0.1f * (shredded + splinters) - 0.2f * untied);
    }
}
