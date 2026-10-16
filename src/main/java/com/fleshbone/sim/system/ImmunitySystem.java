package com.fleshbone.sim.system;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.Needs;
import com.fleshbone.sim.Vitals;

/**
 * Immune strength, recomputed from the whole body as in Casualties: Unknown: 100 % plus
 * 0.75 % per point of hunger above 70, 0.3 % per point of thirst above 60, 0.2 % per point of energy above 60,
 * 8 % per °C above 37 (a fever helps), 0.2 % per 25 mL of blood above 5 L, minus 1 % per point of dirt above 50 and
 * 0.8 % per point of nausea, plus up to 70 % from antibiotics. Each term also works downward.
 */
public final class ImmunitySystem {
    public static final float MAX = 200f;
    /** Immunity table: infection change in % per minute (Casualties: Unknown). */
    private static final float[] TABLE_IMMUNITY = {0f, 100f, 196f, 200f};
    private static final float[] TABLE_RATE = {18f, 7.2f, -0.288f, -5.76f};
    /** Infection change in % per minute on a disinfected wound at 100 % immunity. */
    public static final float DISINFECTED_RATE = -5.04f;

    private ImmunitySystem() {}

    public static float compute(Body body) {
        Needs n = body.needs;
        Vitals v = body.vitals;
        float immunity = 100f
                + 0.75f * (n.hunger - 70f)
                + 0.3f * (n.thirst - 60f)
                + 0.2f * (n.energy - 60f)
                + 8f * (v.bodyTemp - 37f)
                + 0.2f * (v.bloodVolume - Vitals.NORMAL_BLOOD_VOLUME) / 0.025f
                - Math.max(0f, n.dirtiness - 50f)
                - 0.8f * n.sickness
                + Math.min(70f, body.chem.antibiotic * 1.4f);
        return SimMath.clamp(immunity, 0f, MAX);
    }

    /** Infection growth in % per minute at this immunity (negative = the body is winning). */
    public static float infectionRate(float immunity) {
        float x = SimMath.clamp(immunity, 0f, MAX);
        for (int i = 1; i < TABLE_IMMUNITY.length; i++) {
            if (x <= TABLE_IMMUNITY[i]) {
                float t = (x - TABLE_IMMUNITY[i - 1]) / (TABLE_IMMUNITY[i] - TABLE_IMMUNITY[i - 1]);
                return TABLE_RATE[i - 1] + t * (TABLE_RATE[i] - TABLE_RATE[i - 1]);
            }
        }
        return TABLE_RATE[TABLE_RATE.length - 1];
    }

    /** Healing of a disinfected wound in % per minute, scaled by immunity. */
    public static float disinfectedRate(float immunity) {
        return DISINFECTED_RATE * SimMath.clamp(immunity / 100f, 0.3f, 2f);
    }
}
