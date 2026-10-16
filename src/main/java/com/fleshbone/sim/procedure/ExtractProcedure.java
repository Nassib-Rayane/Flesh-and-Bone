package com.fleshbone.sim.procedure;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Pulling a fragment out of a wound, seen from above:
 * <ol>
 *   <li><b>Clear</b>: dab away the pools of blood hiding the bottom of the wound.</li>
 *   <li><b>Probe</b>: hold the tip and guide it down the wound channel to the fragment without touching the walls
 *       (each touch hurts, makes the patient flinch, and tears tissue every other time).</li>
 *   <li><b>Grip</b>: hold steady on the fragment until it is firmly gripped.</li>
 *   <li><b>Withdraw</b>: bring it back up the same way, slowly: a fast pull can make it slip back to the bottom.</li>
 * </ol>
 * Bare fingers are fatter (less room in the channel) and grip worse than tweezers.
 *
 * <p>The pulling speed is the progress along the channel, smoothed over about a tenth of a second: shaking sideways
 * or back and forth does not count as pulling, only bringing the fragment up does. Above {@link #MAX_SPEED} every bit
 * of channel pulled may let the fragment slip, the more likely the faster the pull: a yank almost always loses it,
 * a pull just over the limit sometimes does, and the odds do not depend on the frame rate.
 */
public final class ExtractProcedure extends Procedure {
    private static final String[] STEPS = {"clear", "probe", "grip", "withdraw"};
    public static final float GRIP_SECONDS = 0.8f;
    /** Pulling faster than this along the channel (work-area units per second) risks a slip. */
    public static final float MAX_SPEED = 0.6f;
    /** Time constant of the speed smoothing, in seconds. */
    private static final float SMOOTHING = 0.12f;
    /** Shortest interval between two speed samples: closer samples only measure the jitter of the cursor. */
    private static final float SAMPLE_INTERVAL = 0.01f;
    /** Slip hazard per unit of channel pulled at twice the safe speed: tweezers hold better than fingers. */
    private static final float SLIP_TWEEZERS = 3f;
    private static final float SLIP_FINGERS = 5f;

    public final boolean tweezers;
    public final float halfWidth;
    /** Channel centerline from the opening (first point) to the fragment (last point). */
    private final float[][] channel;
    private final float length;
    private final List<float[]> blood = new ArrayList<>();
    private final Random rng;
    private float tipX;
    private float tipY;
    private boolean guiding;
    private boolean touching;
    private float lastTouch = -10f;
    private int touches;
    private int slips;
    private float grip;
    private float lastX;
    private float lastY;
    private boolean pulling;
    private float pullPosition;
    private float pullTime;
    private float pullSpeed;

    public ExtractProcedure(boolean tweezers, long seed) {
        this.tweezers = tweezers;
        this.halfWidth = tweezers ? 0.065f : 0.05f;
        rng = new Random(seed);
        channel = new float[5][];
        float x = 0.5f;
        for (int i = 0; i < channel.length; i++) {
            float y = 0.12f + i * 0.17f;
            if (i > 0) x = Math.max(0.25f, Math.min(0.75f, x + (rng.nextFloat() - 0.5f) * 0.3f));
            channel[i] = new float[] {x, y};
        }
        float total = 0f;
        for (int i = 0; i + 1 < channel.length; i++) total += dist(channel[i][0], channel[i][1], channel[i + 1][0], channel[i + 1][1]);
        length = total;
        for (int i = 0; i < 3; i++) {
            float[] p = pointAlong(0.45f + i * 0.2f);
            blood.add(new float[] {p[0] + (rng.nextFloat() - 0.5f) * 0.06f, p[1]});
        }
        tipX = channel[0][0];
        tipY = channel[0][1];
    }

    @Override
    public String[] steps() {
        return STEPS;
    }

    public float[][] channel() { return channel; }

    public List<float[]> blood() { return blood; }

    public float tipX() { return tipX; }

    public float tipY() { return tipY; }

    public boolean touching() { return touching; }

    public float grip() { return grip; }

    /** How many times the fragment slipped back down. */
    public int slips() { return slips; }

    /** Smoothed speed at which the fragment is coming up the channel (0 when still or pushed back down). */
    public float pullSpeed() {
        return Math.max(0f, pullSpeed);
    }

    /** 0 at the bottom of the channel, 1 at the opening. */
    public float withdrawn() {
        return clamp01(1f - depth(tipX, tipY) / length);
    }

    public float fragmentX() {
        return stepKey().equals("withdraw") ? tipX : channel[channel.length - 1][0];
    }

    public float fragmentY() {
        return stepKey().equals("withdraw") ? tipY : channel[channel.length - 1][1];
    }

    /** Point at fraction {@code f} (0 = opening, 1 = fragment) along the channel. */
    public float[] pointAlong(float f) {
        float scaled = Math.max(0f, Math.min(0.9999f, f)) * (channel.length - 1);
        int i = (int) scaled;
        float local = scaled - i;
        return new float[] {channel[i][0] + (channel[i + 1][0] - channel[i][0]) * local,
                channel[i][1] + (channel[i + 1][1] - channel[i][1]) * local};
    }

    /** Distance from a point to the channel centerline. */
    public float distanceToChannel(float x, float y) {
        float best = Float.MAX_VALUE;
        for (int i = 0; i + 1 < channel.length; i++) {
            float[] p = closestOnSegment(i, x, y);
            best = Math.min(best, dist(x, y, p[0], p[1]));
        }
        return best;
    }

    /** How far down the channel a point is: distance along the centerline from the opening to its closest point. */
    public float depth(float x, float y) {
        float best = Float.MAX_VALUE;
        float depth = 0f;
        float start = 0f;
        for (int i = 0; i + 1 < channel.length; i++) {
            float[] p = closestOnSegment(i, x, y);
            float d = dist(x, y, p[0], p[1]);
            if (d < best) {
                best = d;
                depth = start + dist(channel[i][0], channel[i][1], p[0], p[1]);
            }
            start += dist(channel[i][0], channel[i][1], channel[i + 1][0], channel[i + 1][1]);
        }
        return depth;
    }

    private float[] closestOnSegment(int i, float x, float y) {
        float ax = channel[i][0];
        float ay = channel[i][1];
        float bx = channel[i + 1][0];
        float by = channel[i + 1][1];
        float len2 = (bx - ax) * (bx - ax) + (by - ay) * (by - ay);
        float u = len2 == 0f ? 0f : Math.max(0f, Math.min(1f, ((x - ax) * (bx - ax) + (y - ay) * (by - ay)) / len2));
        return new float[] {ax + u * (bx - ax), ay + u * (by - ay)};
    }

    @Override
    protected void onPress(float x, float y, float t) {
        switch (stepKey()) {
            case "clear" -> {
                blood.removeIf(b -> dist(x, y, b[0], b[1]) < 0.06f);
                if (blood.isEmpty()) advance(t);
            }
            case "probe", "withdraw" -> guiding = dist(x, y, tipX, tipY) < 0.07f;
            default -> { }
        }
        pulling = false;
        lastX = x;
        lastY = y;
    }

    @Override
    protected void onDrag(float x, float y, float t) {
        lastX = x;
        lastY = y;
        String step = stepKey();
        if ((!step.equals("probe") && !step.equals("withdraw")) || !guiding) return;
        tipX = x;
        tipY = y;
        touching = distanceToChannel(x, y) > halfWidth;
        if (touching && t - lastTouch > 0.5f) {
            lastTouch = t;
            touches++;
            mistakes.pain++;
            if (touches % 2 == 0) mistakes.tear++;
        }
        if (step.equals("probe")) {
            if (dist(x, y, channel[channel.length - 1][0], channel[channel.length - 1][1]) < 0.045f) advance(t);
            return;
        }
        if (pulled(x, y, t)) {
            slip(t);
        } else if (dist(x, y, channel[0][0], channel[0][1]) < 0.05f) {
            advance(t);
        }
    }

    /** Measures the pull and rolls for a slip; true if the fragment slipped. */
    private boolean pulled(float x, float y, float t) {
        float position = depth(x, y);
        if (!pulling) {
            pulling = true;
            pullPosition = position;
            pullTime = t;
            return false;
        }
        float dt = t - pullTime;
        if (dt < SAMPLE_INTERVAL) return false;
        float moved = pullPosition - position;
        pullSpeed += (moved / dt - pullSpeed) * (1f - (float) Math.exp(-dt / SMOOTHING));
        pullPosition = position;
        pullTime = t;
        if (pullSpeed <= MAX_SPEED || moved <= 0f) return false;
        float hazard = (pullSpeed / MAX_SPEED - 1f) * (tweezers ? SLIP_TWEEZERS : SLIP_FINGERS) * moved;
        return rng.nextFloat() < 1f - (float) Math.exp(-hazard);
    }

    /** The fragment slips back down to the bottom: grip it again. */
    private void slip(float t) {
        slips++;
        mistakes.tear++;
        tipX = channel[channel.length - 1][0];
        tipY = channel[channel.length - 1][1];
        grip = 0f;
        guiding = false;
        pulling = false;
        pullSpeed = 0f;
        goTo(2, t);
    }

    @Override
    protected void onRelease(float x, float y, float t) {
        guiding = false;
        pulling = false;
        pullSpeed = 0f;
        if (stepKey().equals("grip") && grip < 1f) grip = 0f;
    }

    @Override
    protected void onTick(float t, float dt) {
        if (stepKey().equals("grip")) {
            boolean onIt = holding && dist(lastX, lastY, channel[channel.length - 1][0], channel[channel.length - 1][1]) < 0.06f;
            if (onIt) grip += dt / (GRIP_SECONDS * (tweezers ? 1f : 1.5f));
            if (grip >= 1f) {
                guiding = true;
                pulling = false;
                pullSpeed = 0f;
                advance(t);
            }
        }
    }

    @Override
    protected void onMove(float x, float y, float t) {
        lastX = x;
        lastY = y;
    }

    @Override
    public float quality() {
        if (!succeeded()) return 0f;
        return Math.max(0.6f, 1f - 0.06f * touches - 0.1f * slips);
    }
}
