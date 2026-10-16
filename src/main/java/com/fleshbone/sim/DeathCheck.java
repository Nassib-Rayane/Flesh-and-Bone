package com.fleshbone.sim;

/** Decides whether the body is dead and why. Everything else leads here through brain damage. */
public final class DeathCheck {
    /** Below this blood volume death is immediate, whatever the heart does. */
    public static final float EXSANGUINATION_LITRES = 0.75f;

    private DeathCheck() {}

    /** @return the cause of death, or {@code null} while alive */
    public static DeathCause check(Body body) {
        Vitals v = body.vitals;
        if (v.headDestroyed) return DeathCause.BRAIN_TRAUMA;
        if (v.bloodVolume <= EXSANGUINATION_LITRES) return DeathCause.HEMORRHAGE;
        if (v.brainHealth <= 0f) return v.pendingCause;
        return null;
    }
}
