package com.fleshbone.sim.procedure;

import java.util.Random;

/**
 * Searing a wound shut: hold the flame on each bleeding point until it is sealed (about a second). Lingering past
 * that burns deep; moving away too early leaves it bleeding.
 */
public final class CauterizeProcedure extends Procedure {
    private static final String[] STEPS = {"sear"};
    public static final float SEAL = 1f;
    public static final float DEEP_BURN = 1.6f;

    /** x, y, heat. */
    public final float[][] points;
    private final boolean[] burnt;
    private int deepBurns;
    private float cursorX;
    private float cursorY;

    /** @param bleed bleeding intensity: more points the worse it bleeds */
    public CauterizeProcedure(float bleed, long seed) {
        Random rng = new Random(seed);
        int count = Math.max(2, Math.min(4, 1 + Math.round(bleed / 25f)));
        points = new float[count][];
        burnt = new boolean[count];
        for (int i = 0; i < count; i++) points[i] = new float[] {0.25f + rng.nextFloat() * 0.5f, 0.3f + rng.nextFloat() * 0.4f, 0f};
    }

    @Override
    public String[] steps() {
        return STEPS;
    }

    @Override
    protected void onPress(float x, float y, float t) {
        cursorX = x;
        cursorY = y;
    }

    @Override
    protected void onDrag(float x, float y, float t) {
        cursorX = x;
        cursorY = y;
    }

    @Override
    protected void onTick(float t, float dt) {
        boolean all = true;
        for (int i = 0; i < points.length; i++) {
            float[] p = points[i];
            boolean onIt = holding && dist(cursorX, cursorY, p[0], p[1]) < 0.045f;
            if (onIt) {
                p[2] += dt;
                if (p[2] > DEEP_BURN && !burnt[i]) {
                    burnt[i] = true;
                    deepBurns++;
                    mistakes.pain++;
                    mistakes.tear++;
                }
            } else if (p[2] < SEAL) {
                p[2] = Math.max(0f, p[2] - 0.5f * dt);
            }
            if (p[2] < SEAL) all = false;
        }
        if (all && !holding) advance(t);
    }

    @Override
    public float quality() {
        return Math.max(0.2f, 1f - 0.15f * deepBurns);
    }
}
