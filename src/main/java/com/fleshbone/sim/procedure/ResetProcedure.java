package com.fleshbone.sim.procedure;

/**
 * Putting a dislocated joint back:
 * <ol>
 *   <li><b>Traction</b>: hold the button to pull (force rises while held, eases when released) and keep the force in
 *       the band where the muscles let go, which wanders with the spasms. Pain narrows the band, painkillers widen
 *       it, strength pulls faster. Yanking well past it hurts.</li>
 *   <li><b>Rotate</b>: keep pulling (button held) and swing the limb along the arc to the mark, smoothly, then let go
 *       on it: "clunk". Jerks hurt; overshooting risks a fracture. Letting go too early loses the traction.</li>
 * </ol>
 */
public final class ResetProcedure extends Procedure {
    private static final String[] STEPS = {"traction", "rotate"};
    public static final float HOLD_SECONDS = 2f;
    public static final float PIVOT_X = 0.3f;
    public static final float PIVOT_Y = 0.6f;
    public static final float TARGET_DEGREES = 70f;

    private final float halfWidth;
    private final float pullRate;
    private float force;
    private float inBand;
    private float overTime;
    private float lastOver = -10f;
    private int overforce;
    private int jerks;
    private int overshoots;
    private float angle;
    private float lastAngle;
    private float lastAngleT;
    private float lastJerk = -10f;
    private boolean overshot;

    /**
     * @param pain     felt pain: narrows the relaxation band
     * @param relief   painkiller effect: widens it
     * @param strength hand strength multiplier (1 = average)
     */
    public ResetProcedure(float pain, float relief, float strength) {
        halfWidth = Math.max(0.06f, Math.min(0.2f, 0.16f - pain / 800f + relief / 600f));
        pullRate = 0.45f * Math.max(0.5f, strength);
    }

    @Override
    public String[] steps() {
        return STEPS;
    }

    public float force() { return force; }

    public float halfWidth() { return halfWidth; }

    public float inBand() { return inBand / HOLD_SECONDS; }

    public float angle() { return angle; }

    /** Center of the relaxation band at time {@code t}. */
    public static float bandCenter(float t) {
        return 0.55f + 0.12f * (float) Math.sin(1.7 * t) + 0.05f * (float) Math.sin(4.3 * t + 1f);
    }

    @Override
    protected void onTick(float t, float dt) {
        force = Math.max(0f, Math.min(1.25f, force + (holding ? pullRate : -0.6f) * dt));
        float center = bandCenter(t);
        if (stepKey().equals("traction")) {
            if (Math.abs(force - center) < halfWidth) inBand += dt;
            if (force > center + halfWidth + 0.12f) {
                overTime += dt;
                if (overTime > 0.4f && t - lastOver > 1f) {
                    lastOver = t;
                    overforce++;
                    mistakes.pain++;
                }
            } else {
                overTime = 0f;
            }
            if (inBand >= HOLD_SECONDS) {
                lastAngle = 0f;
                lastAngleT = t;
                advance(t);
            }
        } else if (force < center - halfWidth - 0.1f) {
            // The traction is lost: the joint slips back
            inBand = 0f;
            angle = 0f;
            goTo(0, t);
        }
    }

    @Override
    protected void onDrag(float x, float y, float t) {
        if (!stepKey().equals("rotate")) return;
        float degrees = (float) Math.toDegrees(Math.atan2(PIVOT_Y - y, x - PIVOT_X));
        angle = Math.max(-10f, Math.min(TARGET_DEGREES + 30f, degrees));
        float dt = t - lastAngleT;
        if (dt > 0.02f) {
            float speed = Math.abs(angle - lastAngle) / dt;
            if (speed > 160f && t - lastJerk > 0.5f) {
                lastJerk = t;
                jerks++;
                mistakes.pain++;
            }
            lastAngle = angle;
            lastAngleT = t;
        }
        if (angle > TARGET_DEGREES + 15f && !overshot) {
            overshot = true;
            overshoots++;
            mistakes.tear++;
        } else if (angle < TARGET_DEGREES + 10f) {
            overshot = false;
        }
    }

    @Override
    protected void onRelease(float x, float y, float t) {
        if (stepKey().equals("rotate") && Math.abs(angle - TARGET_DEGREES) <= 10f) advance(t);
    }

    /** Overshooting the arc risks breaking the bone (read by the server). */
    public int overshoots() {
        return overshoots;
    }

    @Override
    public float quality() {
        if (!succeeded()) return 0f;
        return Math.max(0.1f, 1f - 0.12f * overforce - 0.1f * jerks - 0.15f * overshoots);
    }
}
