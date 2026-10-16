package com.fleshbone.sim.procedure;

/**
 * Placing a chest drain, seen from the side of the chest:
 * <ol>
 *   <li><b>Palpate</b>: count the ribs down (hover a rib to feel which one it is) and mark the 5th space, between ribs
 *       5 and 6, in the middle column (mid-axillary line). One space off hurts; further off injures an organ.</li>
 *   <li><b>Incise</b>: a short cut at the mark.</li>
 *   <li><b>Insert</b>: push the tube in, staying just above the lower rib: drifting up under the upper rib tears its
 *       vessels; pushing too far toward the middle bruises the lung.</li>
 * </ol>
 */
public final class ChestDrainProcedure extends Procedure {
    private static final String[] STEPS = {"palpate", "incise", "insert"};
    public static final int RIBS = 8;
    public static final float FIRST_RIB = 0.14f;
    public static final float RIB_SPACING = 0.095f;
    public static final float RIB_THICKNESS = 0.035f;
    public static final float COLUMN_MIN = 0.42f;
    public static final float COLUMN_MAX = 0.62f;
    /** Index (0-based) of the space below rib 5. */
    public static final int TARGET_SPACE = 4;

    private float markX;
    private float markY;
    private float position = 1f;
    private float cutStartX;
    private float cutStartY;
    private boolean cutting;
    private boolean inserting;
    private int hovered = -1;
    private float hoverTime;
    private int tears;

    @Override
    public String[] steps() {
        return STEPS;
    }

    /** Top of rib {@code i} (0-based). */
    public static float ribTop(int i) {
        return FIRST_RIB + i * RIB_SPACING;
    }

    /** The rib under a height, or -1. */
    public static int ribAt(float y) {
        for (int i = 0; i < RIBS; i++) {
            if (y >= ribTop(i) && y <= ribTop(i) + RIB_THICKNESS) return i;
        }
        return -1;
    }

    /** The intercostal space under a height (between rib i and i + 1), or -1. */
    public static int spaceAt(float y) {
        for (int i = 0; i + 1 < RIBS; i++) {
            if (y > ribTop(i) + RIB_THICKNESS && y < ribTop(i + 1)) return i;
        }
        return -1;
    }

    /** The rib felt under the finger after a moment, or -1. */
    public int feltRib() {
        return hoverTime > 0.4f ? hovered : -1;
    }

    public float markX() { return markX; }

    public float markY() { return markY; }

    @Override
    protected void onMove(float x, float y, float t) {
        int rib = ribAt(y);
        if (rib != hovered) {
            hovered = rib;
            hoverTime = 0f;
        }
    }

    @Override
    protected void onTick(float t, float dt) {
        if (hovered >= 0) hoverTime += dt;
    }

    @Override
    protected void onPress(float x, float y, float t) {
        switch (stepKey()) {
            case "palpate" -> {
                int space = spaceAt(y);
                if (space < 0) return;
                markX = x;
                markY = y;
                int off = Math.abs(space - TARGET_SPACE);
                boolean column = x >= COLUMN_MIN && x <= COLUMN_MAX;
                position = off == 0 && column ? 1f : off <= 1 ? 0.6f : 0.3f;
                if (off == 1 || !column) mistakes.pain++;
                if (off > 1) mistakes.tear++;
                advance(t);
            }
            case "incise" -> {
                cutting = dist(x, y, markX, markY) < 0.06f;
                cutStartX = x;
                cutStartY = y;
            }
            case "insert" -> inserting = dist(x, y, markX, markY) < 0.07f;
            default -> { }
        }
    }

    @Override
    protected void onDrag(float x, float y, float t) {
        if (stepKey().equals("incise") && cutting && dist(x, y, cutStartX, cutStartY) >= 0.05f) {
            cutting = false;
            advance(t);
        } else if (stepKey().equals("insert") && inserting) {
            int space = spaceAt(markY);
            if (space >= 0 && y < ribTop(space) + RIB_THICKNESS + 0.01f && tears < 3) {
                // Up under the rib above: its vessels
                tears++;
                mistakes.tear++;
                inserting = false;
                return;
            }
            if (x < markX - 0.3f) {
                mistakes.pain++;
                inserting = false;
                return;
            }
            if (dist(x, y, markX, markY) >= 0.15f) advance(t);
        }
    }

    @Override
    protected void onRelease(float x, float y, float t) {
        cutting = false;
        inserting = false;
    }

    @Override
    public float quality() {
        if (!succeeded()) return 0f;
        return Math.max(0.2f, position - 0.15f * tears);
    }
}
