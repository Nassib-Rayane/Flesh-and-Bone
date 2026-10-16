package com.fleshbone.sim.procedure;

import java.util.ArrayList;
import java.util.List;

/**
 * Dressing a wound in three steps:
 * <ol>
 *   <li><b>Press</b>: hold pressure on the wound for 2.5 s, keeping the hand on it despite the tremor.</li>
 *   <li><b>Wrap</b>: each turn goes down across the limb and back up; turns must overlap by 30–70 % and cover the
 *       whole wound. Gaps and bunching weaken the dressing; snapping a turn too fast pulls it tight and hurts.</li>
 *   <li><b>Tuck</b>: tuck the end in within a few seconds, or the dressing loosens.</li>
 * </ol>
 * The limb lies horizontally; the wound spans {@link #woundStart}–{@link #woundEnd} along it.
 */
public final class BandageProcedure extends Procedure {
    private static final String[] STEPS = {"press", "wrap", "tuck"};
    public static final float WOUND_Y = 0.5f;
    public static final float PRESS_RADIUS = 0.1f;
    public static final float PRESS_SECONDS = 2.5f;
    public static final float LIMB_TOP = 0.38f;
    public static final float LIMB_BOTTOM = 0.62f;
    /** Width of one bandage turn along the limb. */
    public static final float STRIPE = 0.08f;
    /** A turn faster than this across the limb is pulled too tight. */
    public static final float TIGHT_SECONDS = 0.12f;
    public static final float TUCK_SECONDS = 4f;

    public final float woundStart;
    public final float woundEnd;
    public final float woundCenter;
    private final int maxTurns;
    private float pressure;
    private float pressTime;
    private float insideTime;
    private boolean inside;
    private final List<Float> stripes = new ArrayList<>();
    private boolean goingDown = true;
    private boolean pastTop;
    private float crossStart = -1f;
    private int gaps;
    private int bunches;
    private boolean tucked;
    private float cursorX;
    private float cursorY;

    /**
     * @param skin     skin health of the part: the more torn, the longer the wound
     * @param maxTurns turns left on the roll
     */
    public BandageProcedure(float skin, int maxTurns) {
        float length = 0.15f + 0.35f * clamp01(1f - skin / 100f);
        woundCenter = 0.5f;
        woundStart = woundCenter - length / 2f;
        woundEnd = woundCenter + length / 2f;
        this.maxTurns = Math.max(1, maxTurns);
    }

    @Override
    public String[] steps() {
        return STEPS;
    }

    public float pressure() { return pressure; }

    public List<Float> stripes() { return stripes; }

    public boolean goingDown() { return goingDown; }

    public int turnsLeft() { return maxTurns - stripes.size(); }

    /** Where the next turn should go. */
    public float nextTurnX() {
        return stripes.isEmpty() ? woundStart : stripes.get(stripes.size() - 1) + STRIPE * 0.5f;
    }

    @Override
    protected void onPress(float x, float y, float t) {
        cursorX = x;
        cursorY = y;
        if (stepKey().equals("tuck") && !stripes.isEmpty() && dist(x, y, stripes.get(stripes.size() - 1), LIMB_BOTTOM + 0.06f) < 0.06f) {
            tucked = true;
            advance(t);
        }
    }

    @Override
    protected void onDrag(float x, float y, float t) {
        cursorX = x;
        cursorY = y;
        if (!stepKey().equals("wrap")) return;
        if (goingDown) {
            if (y < LIMB_TOP) {
                pastTop = true;
            } else if (pastTop && crossStart < 0f) {
                crossStart = t;
            }
            if (pastTop && y > LIMB_BOTTOM) {
                if (crossStart >= 0f && t - crossStart < TIGHT_SECONDS) mistakes.pain++;
                goingDown = false;
                pastTop = false;
                crossStart = -1f;
            }
        } else if (y < LIMB_TOP) {
            turn(Math.max(0f, Math.min(1f, x)), t);
            goingDown = true;
            pastTop = true;
            crossStart = -1f;
        }
    }

    private void turn(float x, float t) {
        if (!stripes.isEmpty()) {
            float spacing = x - stripes.get(stripes.size() - 1);
            if (spacing > STRIPE) gaps++;
            else if (spacing < STRIPE * 0.2f) bunches++;
        } else if (Math.abs(x - woundStart) > STRIPE) {
            gaps++;
        }
        stripes.add(x);
        if (coverage() >= 0.98f || stripes.size() >= maxTurns) advance(t);
    }

    @Override
    protected void onTick(float t, float dt) {
        if (stepKey().equals("press")) {
            inside = holding && dist(cursorX, cursorY, woundCenter, WOUND_Y) <= PRESS_RADIUS;
            if (holding) {
                pressTime += dt;
                if (inside) {
                    insideTime += dt;
                    pressure += dt / PRESS_SECONDS;
                }
            }
            if (pressure >= 1f) {
                crossStart = -1f;
                advance(t);
            }
        } else if (stepKey().equals("tuck") && stepTime(t) > TUCK_SECONDS) {
            advance(t);
        }
    }

    /** Fraction of the wound covered by the turns. */
    public float coverage() {
        float covered = 0f;
        float reach = woundStart;
        List<Float> sorted = new ArrayList<>(stripes);
        sorted.sort(Float::compare);
        for (float s : sorted) {
            float from = Math.max(reach, Math.max(woundStart, s - STRIPE / 2f));
            float to = Math.min(woundEnd, s + STRIPE / 2f);
            if (to > from) {
                covered += to - from;
                reach = to;
            }
        }
        return covered / (woundEnd - woundStart);
    }

    @Override
    public float quality() {
        float press = pressTime > 0f ? insideTime / pressTime : 0f;
        float regularity = Math.max(0.3f, 1f - 0.12f * (gaps + bunches));
        return clamp01(0.2f * press + 0.6f * coverage() * regularity + 0.2f * (tucked ? 1f : 0.3f) - 0.05f * mistakes.pain);
    }

    @Override
    public int amount() {
        return Math.max(1, stripes.size());
    }
}
