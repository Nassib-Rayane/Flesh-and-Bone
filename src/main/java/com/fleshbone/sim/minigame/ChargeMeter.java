package com.fleshbone.sim.minigame;

/**
 * Defibrillator: hold to charge, release to shock. Releasing in the top band (85–100 %) gives a full-energy shock;
 * holding too long overcharges and the capacitor bleeds off, ruining the shock.
 */
public final class ChargeMeter {
    private final float chargeSeconds;
    private float startTime = Float.NaN;

    public ChargeMeter(float chargeSeconds) {
        this.chargeSeconds = chargeSeconds;
    }

    public void press(float time) {
        startTime = time;
    }

    public boolean charging() {
        return !Float.isNaN(startTime);
    }

    /** Charge level 0–1.3 (above 1 is overcharge). */
    public float charge(float time) {
        if (!charging()) return 0f;
        return Math.min(1.3f, (time - startTime) / chargeSeconds);
    }

    /** Quality of a release at {@code time}. */
    public float release(float time) {
        float c = charge(time);
        startTime = Float.NaN;
        if (c >= 0.85f && c <= 1f) return 1f;
        if (c > 1f) return Math.max(0f, 1f - (c - 1f) * 3f);
        return Math.max(0f, c / 0.85f * 0.7f);
    }
}
