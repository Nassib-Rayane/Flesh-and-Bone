package com.fleshbone.sim.minigame;

/**
 * Dislocation reset: a needle sweeps a gauge back and forth; clicking inside the green zone pops the joint back.
 * The zone narrows with pain.
 */
public final class TimingGauge {
    private final float period;
    private final float zoneCenter;
    private final float zoneHalfWidth;

    /**
     * @param period     seconds for a full back-and-forth sweep
     * @param zoneCenter where the green zone is, 0–1
     * @param pain       total pain, narrows the zone
     */
    public TimingGauge(float period, float zoneCenter, float pain) {
        this.period = period;
        this.zoneCenter = zoneCenter;
        this.zoneHalfWidth = 0.12f * Math.max(0.35f, 1f - pain / 150f);
    }

    /** Needle position 0–1 at {@code time} seconds (triangle wave). */
    public float needle(float time) {
        float phase = (time / period) % 1f;
        return phase < 0.5f ? phase * 2f : 2f - phase * 2f;
    }

    public float zoneCenter() {
        return zoneCenter;
    }

    public float zoneHalfWidth() {
        return zoneHalfWidth;
    }

    /** 1 at the centre of the zone, fading to 0 just outside it. */
    public float quality(float time) {
        float distance = Math.abs(needle(time) - zoneCenter);
        if (distance <= zoneHalfWidth) return 1f - 0.3f * (distance / zoneHalfWidth);
        if (distance <= zoneHalfWidth * 2f) return 0.5f * (1f - (distance - zoneHalfWidth) / zoneHalfWidth);
        return 0f;
    }
}
