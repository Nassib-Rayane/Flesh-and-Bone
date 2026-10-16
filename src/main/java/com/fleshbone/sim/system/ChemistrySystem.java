package com.fleshbone.sim.system;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.Chemistry;

/**
 * Drug elimination, opioid tolerance and dependence. Dependence lingers after the drug is gone: the gap between the
 * two is withdrawal (pain, nausea, racing heart, misery). Naloxone flushes opioids out.
 */
public final class ChemistrySystem {
    private ChemistrySystem() {}

    public static void tick(Body body, float dt, boolean dependence) {
        Chemistry c = body.chem;
        c.decay(dt);
        if (c.naloxone > 10f) c.opiate = Math.max(0f, c.opiate - 2f * dt);
        if (!dependence) {
            c.opiateTolerance = 0f;
            c.opiateDependence = 0f;
            return;
        }
        if (c.opiate > 1f) c.opiateDependence = Math.min(100f, c.opiateDependence + c.opiate * 0.0005f * dt);
        c.opiateTolerance = Math.max(0f, c.opiateTolerance - 0.01f * dt);
        c.opiateDependence = Math.max(0f, c.opiateDependence - 0.004f * dt);
    }

    /** Withdrawal intensity (0 when not dependent or still medicated). */
    public static float withdrawal(Chemistry c) {
        return Math.max(0f, c.opiateDependence - c.opiate);
    }
}
