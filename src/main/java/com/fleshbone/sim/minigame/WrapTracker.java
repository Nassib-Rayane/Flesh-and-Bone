package com.fleshbone.sim.minigame;

/**
 * Bandage minigame: the player circles the mouse around the limb. Each full turn is one wrap. Wraps that hug the
 * guide ring are neat; too few leave the wound bleeding, too many waste the roll.
 */
public final class WrapTracker {
    public static final int MIN_LENGTH = 1;
    public static final int MAX_LENGTH = 8;
    private final float centerX;
    private final float centerY;
    private final float targetRadius;
    private double lastAngle = Double.NaN;
    private double accumulated;
    private float errorSum;

    public WrapTracker(float centerX, float centerY, float targetRadius) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.targetRadius = targetRadius;
    }

    public void sample(float x, float y) {
        float dx = x - centerX;
        float dy = y - centerY;
        float r = (float) Math.sqrt(dx * dx + dy * dy);
        // Losing the roll in the middle of the wound breaks the motion
        if (r < targetRadius * 0.3f) {
            lastAngle = Double.NaN;
            return;
        }
        double angle = Math.atan2(dy, dx);
        if (!Double.isNaN(lastAngle)) {
            double delta = angle - lastAngle;
            if (delta > Math.PI) delta -= 2 * Math.PI;
            else if (delta < -Math.PI) delta += 2 * Math.PI;
            accumulated += delta;
            errorSum += Math.abs(delta) * Math.min(2f, Math.abs(r - targetRadius) / targetRadius);
        }
        lastAngle = angle;
    }

    public int wraps() {
        return (int) Math.floor(Math.abs(accumulated) / (2 * Math.PI));
    }

    /** 0–1 progress of the wrap in progress. */
    public float currentWrapProgress() {
        double turns = Math.abs(accumulated) / (2 * Math.PI);
        return (float) (turns - Math.floor(turns));
    }

    /** Average distance from the guide ring, relative to its radius (0 = perfect). */
    public float sloppiness() {
        double turned = Math.abs(accumulated);
        return turned < 1e-3 ? 0f : (float) (errorSum / turned);
    }

    public float quality() {
        int w = wraps();
        float count = switch (w) {
            case 0 -> 0f;
            case 1 -> 0.3f;
            case 2 -> 0.55f;
            case 3 -> 0.8f;
            case 4, 5, 6 -> 1f;
            default -> 0.9f;
        };
        float neatness = Math.max(0.3f, Math.min(1f, 1f - sloppiness() * 1.5f));
        return count * neatness;
    }

    /** Roll length consumed: one unit per wrap. */
    public int lengthUsed() {
        return Math.max(MIN_LENGTH, Math.min(MAX_LENGTH, wraps()));
    }
}
