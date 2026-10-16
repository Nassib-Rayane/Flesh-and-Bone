package com.fleshbone.sim;

import com.fleshbone.sim.system.BleedingSystem;
import com.fleshbone.sim.system.ConsciousnessSystem;
import com.fleshbone.sim.system.PainSystem;

/**
 * Values computed from the saved state (never saved themselves). Recomputed every tick on the server, and on the
 * client after each sync so the HUD shows exactly what the server simulates.
 */
public final class Derived {
    /** External bleeding per part, L/min. */
    public final float[] partBleed = new float[BodyPart.COUNT];
    /** Internal bleeding per part, L/min. */
    public final float[] partInternal = new float[BodyPart.COUNT];
    public final float[] partPain = new float[BodyPart.COUNT];
    public float externalBleed;
    public float internalBleed;
    public float rawPain;
    public float totalPain;
    public float consciousnessCap = 100f;

    public float totalBleed() {
        return externalBleed + internalBleed;
    }

    public void compute(Body body) {
        externalBleed = 0f;
        internalBleed = 0f;
        for (BodyPart part : BodyPart.VALUES) {
            int i = part.ordinal();
            partBleed[i] = BleedingSystem.externalRate(body, part);
            partInternal[i] = BleedingSystem.internalRate(body, part);
            partPain[i] = PainSystem.partPain(body, part);
            externalBleed += partBleed[i];
            internalBleed += partInternal[i];
        }
        rawPain = PainSystem.rawTotal(body);
        totalPain = PainSystem.total(body, rawPain);
        consciousnessCap = ConsciousnessSystem.cap(body, null);
    }
}
