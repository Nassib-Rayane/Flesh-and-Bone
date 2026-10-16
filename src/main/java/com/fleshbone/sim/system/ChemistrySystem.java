package com.fleshbone.sim.system;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.Chemistry;

/**
 * Drug elimination and opioid tolerance, after Casualties: Unknown's reception model: what the body feels is the
 * opioid level minus a tolerance that climbs toward it at 0.08 per second and falls back slowly. When the drug wears
 * off faster than the tolerance, reception turns negative: that is withdrawal. Naloxone flushes opioids out (and
 * throws a tolerant body straight into withdrawal); antivenom neutralizes venom.
 */
public final class ChemistrySystem {
    public static final float TOLERANCE_RISE = 0.08f;
    public static final float TOLERANCE_FALL = 0.02f;
    /** Opioid removed per second while naloxone is active. */
    public static final float NALOXONE_CLEARANCE = 8f;
    public static final float ANTIVENOM_CLEARANCE = 1.5f;
    /** Reception above which breathing collapses. */
    public static final float OVERDOSE = 80f;
    /** Withdrawal above which nausea builds up. */
    public static final float NAUSEOUS_WITHDRAWAL = 16f;
    /** Withdrawal above which the heart can fibrillate. */
    public static final float SEVERE_WITHDRAWAL = 34f;

    private ChemistrySystem() {}

    public static void tick(Body body, float dt, boolean tolerance) {
        Chemistry c = body.chem;
        c.decay(dt);
        if (c.naloxone > 10f) c.opiate = Math.max(0f, c.opiate - NALOXONE_CLEARANCE * dt);
        if (c.antivenom > 5f) c.venom = Math.max(0f, c.venom - ANTIVENOM_CLEARANCE * dt);
        if (!tolerance) {
            c.opiateTolerance = 0f;
            return;
        }
        if (c.opiate > c.opiateTolerance) {
            c.opiateTolerance = Math.min(c.opiate, c.opiateTolerance + TOLERANCE_RISE * dt);
        } else {
            c.opiateTolerance = Math.max(0f, c.opiateTolerance - TOLERANCE_FALL * dt);
        }
    }

    /** Opioid effect actually felt (0 when sober or in withdrawal). */
    public static float relief(Chemistry c) {
        return Math.max(0f, c.opiateReception());
    }

    /** Withdrawal intensity (0 when not dependent or still medicated). */
    public static float withdrawal(Chemistry c) {
        return Math.max(0f, -c.opiateReception());
    }
}
