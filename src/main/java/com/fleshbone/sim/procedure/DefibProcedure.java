package com.fleshbone.sim.procedure;

/**
 * Defibrillation, like a manual defibrillator:
 * <ol>
 *   <li><b>Pads</b>: drag one pad below the right collarbone, the other on the left side under the chest.</li>
 *   <li><b>Analyze</b>: read the rhythm and decide (choice 0 = shock, 1 = no shock). Fibrillation and ventricular
 *       tachycardia need a shock; a flat line (asystole) does not respond to one; shocking a normal rhythm throws it
 *       into fibrillation.</li>
 *   <li><b>Energy</b>: choose 150, 200 or 360 J (choice 0–2): more works better and burns more.</li>
 *   <li><b>Charge</b>: hold the charge button until full.</li>
 *   <li><b>Shock</b>: clear the patient and press shock. In ventricular tachycardia, shocking right on an R wave
 *       (synchronized) works better.</li>
 * </ol>
 */
public final class DefibProcedure extends Procedure {
    public enum Rhythm {
        SINUS(false), TACHYCARDIA(true), FIBRILLATION(true), ASYSTOLE(false);

        public final boolean shockable;

        Rhythm(boolean shockable) {
            this.shockable = shockable;
        }
    }

    private static final String[] STEPS = {"pads", "analyze", "energy", "charge", "shock"};
    public static final float[] PAD_TARGET_A = {0.38f, 0.3f};
    public static final float[] PAD_TARGET_B = {0.64f, 0.64f};
    public static final float CHARGE_SECONDS = 1.5f;
    public static final float[] CHARGE_BUTTON = {0.5f, 0.92f};

    public final Rhythm rhythm;
    private final float heartRate;
    /** Pad positions; a pad is placed once released on the chest. */
    public final float[] padA = {0.15f, 0.85f};
    public final float[] padB = {0.85f, 0.85f};
    private boolean placedA;
    private boolean placedB;
    private float[] dragging;
    private boolean decidedShock;
    private boolean correctDecision;
    private int energy = 1;
    private float charge;
    private boolean synced;

    /** @param heartRate for the synchronized shock window */
    public DefibProcedure(Rhythm rhythm, float heartRate) {
        this.rhythm = rhythm;
        this.heartRate = Math.max(40f, heartRate);
    }

    @Override
    public String[] steps() {
        return STEPS;
    }

    public boolean placedA() { return placedA; }

    public boolean placedB() { return placedB; }

    public float charge() { return charge / CHARGE_SECONDS; }

    /** 1 = 150 J, 2 = 200 J, 3 = 360 J. */
    public int energy() { return energy; }

    /** The medic decided against shocking. */
    public boolean noShock() {
        return done() && !decidedShock;
    }

    public boolean correctDecision() { return correctDecision; }

    @Override
    protected void onPress(float x, float y, float t) {
        if (stepKey().equals("pads")) {
            if (dist(x, y, padA[0], padA[1]) < 0.07f) dragging = padA;
            else if (dist(x, y, padB[0], padB[1]) < 0.07f) dragging = padB;
        } else if (stepKey().equals("shock") && dist(x, y, CHARGE_BUTTON[0], CHARGE_BUTTON[1]) < 0.09f) {
            if (rhythm == Rhythm.TACHYCARDIA) {
                float period = 60f / heartRate;
                float phase = (stepTime(t) % period) / period;
                synced = phase < 0.1f || phase > 0.92f;
            }
            advance(t);
        }
    }

    @Override
    protected void onDrag(float x, float y, float t) {
        if (dragging != null) {
            dragging[0] = clamp01(x);
            dragging[1] = clamp01(y);
        }
    }

    @Override
    protected void onRelease(float x, float y, float t) {
        if (dragging == null) return;
        boolean onChest = y < 0.8f && x > 0.2f && x < 0.8f;
        if (dragging == padA) placedA = onChest;
        else placedB = onChest;
        dragging = null;
        if (placedA && placedB) advance(t);
    }

    @Override
    protected void onChoose(int option, float t) {
        switch (stepKey()) {
            case "analyze" -> {
                decidedShock = option == 0;
                correctDecision = decidedShock == rhythm.shockable;
                if (decidedShock) advance(t);
                else finish();
            }
            case "energy" -> {
                energy = Math.max(1, Math.min(3, option + 1));
                advance(t);
            }
            default -> { }
        }
    }

    @Override
    protected void onTick(float t, float dt) {
        if (!stepKey().equals("charge")) return;
        if (holding) charge += dt;
        else charge = Math.max(0f, charge - dt * 2f);
        if (charge >= CHARGE_SECONDS) advance(t);
    }

    /** Pad placement accuracy, 0–1. */
    public float padAccuracy() {
        float error = (dist(padA[0], padA[1], PAD_TARGET_A[0], PAD_TARGET_A[1]) + dist(padB[0], padB[1], PAD_TARGET_B[0], PAD_TARGET_B[1])) / 2f;
        return clamp01(1f - error / 0.2f);
    }

    @Override
    public float quality() {
        float q = 0.4f + 0.6f * padAccuracy();
        if (rhythm == Rhythm.TACHYCARDIA) q *= synced ? 1f : 0.8f;
        return clamp01(q);
    }

    @Override
    public int amount() {
        return energy;
    }
}
