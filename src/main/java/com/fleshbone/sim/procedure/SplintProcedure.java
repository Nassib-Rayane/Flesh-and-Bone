package com.fleshbone.sim.procedure;

/**
 * Splinting a fracture:
 * <ol>
 *   <li><b>Align</b>: drag the lower part of the limb back in line with the upper part, slowly (moving a broken bone
 *       fast hurts), and let go once straight.</li>
 *   <li><b>Strap</b>: tie the splint once above and once below the break, never on the break itself.</li>
 * </ol>
 * The result sets how well the splint holds (a crooked or badly strapped splint helps the bone less).
 */
public final class SplintProcedure extends Procedure {
    private static final String[] STEPS = {"align", "strap"};
    public static final float PIVOT_X = 0.5f;
    public static final float LIMB_Y = 0.5f;
    public static final float BREAK_HALF = 0.05f;

    private float angle;
    private float lastAngle;
    private float lastT;
    private float lastHurt = -10f;
    private boolean grabbed;
    private float residual;
    private boolean strapAbove;
    private boolean strapBelow;
    private int badStraps;

    /** @param startAngle how crooked the limb is, in degrees */
    public SplintProcedure(float startAngle) {
        angle = startAngle;
        lastAngle = startAngle;
    }

    @Override
    public String[] steps() {
        return STEPS;
    }

    public float angle() { return angle; }

    public boolean strapAbove() { return strapAbove; }

    public boolean strapBelow() { return strapBelow; }

    /** Position of the end of the lower segment. */
    public float[] distalEnd() {
        double a = Math.toRadians(angle);
        return new float[] {PIVOT_X + 0.32f * (float) Math.cos(a), LIMB_Y + 0.32f * (float) Math.sin(a)};
    }

    @Override
    protected void onPress(float x, float y, float t) {
        if (stepKey().equals("align")) {
            float[] end = distalEnd();
            grabbed = dist(x, y, end[0], end[1]) < 0.08f;
            lastT = t;
            lastAngle = angle;
        } else if (stepKey().equals("strap")) {
            if (Math.abs(y - LIMB_Y) > 0.08f) return;
            if (Math.abs(x - PIVOT_X) < BREAK_HALF) {
                badStraps++;
                mistakes.pain++;
            } else if (x < PIVOT_X && x > PIVOT_X - 0.32f) {
                strapAbove = true;
            } else if (x > PIVOT_X && x < PIVOT_X + 0.32f) {
                strapBelow = true;
            }
            if (strapAbove && strapBelow) advance(t);
        }
    }

    @Override
    protected void onDrag(float x, float y, float t) {
        if (!stepKey().equals("align") || !grabbed) return;
        angle = (float) Math.toDegrees(Math.atan2(y - LIMB_Y, Math.max(0.01f, x - PIVOT_X)));
        float dt = t - lastT;
        if (dt > 0.03f) {
            float speed = Math.abs(angle - lastAngle) / dt;
            if (speed > 90f && t - lastHurt > 0.5f) {
                lastHurt = t;
                mistakes.pain++;
            }
            lastAngle = angle;
            lastT = t;
        }
    }

    @Override
    protected void onRelease(float x, float y, float t) {
        if (stepKey().equals("align") && grabbed) {
            grabbed = false;
            if (Math.abs(angle) <= 5f) {
                residual = Math.abs(angle);
                advance(t);
            }
        }
    }

    @Override
    public float quality() {
        if (!succeeded()) return 0f;
        return clamp01(1f - residual / 10f * 0.3f - 0.08f * mistakes.pain - 0.1f * badStraps);
    }
}
