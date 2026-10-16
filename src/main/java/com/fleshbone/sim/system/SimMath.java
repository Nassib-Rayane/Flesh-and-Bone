package com.fleshbone.sim.system;

/** Small numeric helpers shared by the systems. */
public final class SimMath {
    private SimMath() {}

    public static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    /** Moves {@code value} toward {@code target} by at most {@code maxStep}. */
    public static float approach(float value, float target, float maxStep) {
        if (value < target) return Math.min(target, value + maxStep);
        return Math.max(target, value - maxStep);
    }
}
