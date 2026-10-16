package com.fleshbone.sim.injury;

/**
 * How a kind of trauma converts severity into damage. Severity S is vanilla damage multiplied by the damage scale
 * (a zombie hit is about 15).
 *
 * <ul>
 *   <li>skin / muscle: health removed per point of severity</li>
 *   <li>bleed: external bleeding added per point (only once the skin is broken)</li>
 *   <li>pain: acute pain added per point</li>
 *   <li>internal: internal bleeding per point on the head and torso</li>
 *   <li>fracture chance = (S − fractureAt) / fractureSpan, same for dislocations</li>
 *   <li>shrapnel: chance to leave a fragment in the wound</li>
 *   <li>contamination: germs pushed into the wound</li>
 * </ul>
 */
public enum InjuryKind {
    //        skin  muscle bleed  pain  intern fracAt fracSpan dislAt dislSpan shrap contam
    BLUNT    (0.30f, 1.00f, 0.15f, 1.2f, 0.40f, 15f, 40f, 10f, 60f, 0.00f, 2f),
    CUT      (1.20f, 0.40f, 1.00f, 0.8f, 0.00f, NONE(), 0f, NONE(), 0f, 0.00f, 8f),
    PIERCE   (0.50f, 0.80f, 0.80f, 1.0f, 0.80f, 30f, 60f, NONE(), 0f, 0.40f, 10f),
    BALLISTIC(0.60f, 1.20f, 1.20f, 1.2f, 1.00f, 20f, 40f, NONE(), 0f, 0.50f, 6f),
    EXPLOSION(0.80f, 0.80f, 0.60f, 1.0f, 0.60f, 20f, 50f, 15f, 60f, 0.60f, 12f),
    BURN     (1.50f, 0.20f, 0.00f, 1.5f, 0.00f, NONE(), 0f, NONE(), 0f, 0.00f, 6f),
    FALL     (0.20f, 0.80f, 0.05f, 1.3f, 0.30f, 12f, 35f, 8f, 50f, 0.00f, 1f),
    CRUSH    (0.40f, 1.20f, 0.20f, 1.2f, 0.80f, 10f, 30f, 10f, 50f, 0.00f, 1f),
    BITE     (1.00f, 0.60f, 0.70f, 1.0f, 0.20f, 35f, 60f, NONE(), 0f, 0.00f, 25f),
    ELECTRIC (0.50f, 0.30f, 0.00f, 1.0f, 0.00f, NONE(), 0f, NONE(), 0f, 0.00f, 0f),
    FREEZE   (0.60f, 0.10f, 0.00f, 0.6f, 0.00f, NONE(), 0f, NONE(), 0f, 0.00f, 0f),
    SYSTEMIC (0.00f, 0.60f, 0.00f, 0.4f, 0.00f, NONE(), 0f, NONE(), 0f, 0.00f, 0f);

    public final float skin;
    public final float muscle;
    public final float bleed;
    public final float pain;
    public final float internal;
    public final float fractureAt;
    public final float fractureSpan;
    public final float dislocationAt;
    public final float dislocationSpan;
    public final float shrapnel;
    public final float contamination;

    InjuryKind(float skin, float muscle, float bleed, float pain, float internal,
               float fractureAt, float fractureSpan, float dislocationAt, float dislocationSpan,
               float shrapnel, float contamination) {
        this.skin = skin;
        this.muscle = muscle;
        this.bleed = bleed;
        this.pain = pain;
        this.internal = internal;
        this.fractureAt = fractureAt;
        this.fractureSpan = fractureSpan;
        this.dislocationAt = dislocationAt;
        this.dislocationSpan = dislocationSpan;
        this.shrapnel = shrapnel;
        this.contamination = contamination;
    }

    private static float NONE() {
        return Float.POSITIVE_INFINITY;
    }

    /** Blunt-type trauma that can knock the head and concuss. */
    public boolean concussive() {
        return this == BLUNT || this == FALL || this == CRUSH || this == EXPLOSION || this == BALLISTIC || this == PIERCE;
    }

    public float fractureChance(float severity) {
        return chance(severity, fractureAt, fractureSpan);
    }

    public float dislocationChance(float severity) {
        return chance(severity, dislocationAt, dislocationSpan);
    }

    private static float chance(float severity, float at, float span) {
        if (Float.isInfinite(at) || severity <= at) return 0f;
        return Math.min(0.95f, (severity - at) / span);
    }
}
