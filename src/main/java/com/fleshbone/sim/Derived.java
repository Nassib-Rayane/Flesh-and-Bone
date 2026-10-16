package com.fleshbone.sim;

import com.fleshbone.sim.system.BleedingSystem;
import com.fleshbone.sim.system.ConsciousnessSystem;
import com.fleshbone.sim.system.Encumbrance;
import com.fleshbone.sim.system.ImmunitySystem;
import com.fleshbone.sim.system.PainSystem;
import com.fleshbone.sim.system.StaminaSystem;

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
    /** Worst part pain before resilience, adrenaline and drugs. */
    public float rawPain;
    public float totalPain;
    public float consciousnessCap = 100f;
    /** Immune strength in percent (100 = average), recomputed from the whole body. */
    public float immunity = 100f;
    /** Stamina cannot go above the blood oxygen level, nor below this cap of 70. */
    public float staminaCap = 100f;
    /** Strength left in the muscles from stamina, 0–1 (cubic: tiredness only bites when deep). */
    public float strength = 1f;
    /** Kilograms the body can carry before being slowed. */
    public float capacity = Encumbrance.BASE_CAPACITY;
    /** 0 within capacity, 1 at twice the capacity. */
    public float overload;
    public WeightClass weightClass = WeightClass.HEALTHY;

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
        immunity = ImmunitySystem.compute(body);
        staminaCap = StaminaSystem.cap(body);
        strength = StaminaSystem.strength(body.needs.stamina);
        weightClass = WeightClass.of(body.needs.weight);
        capacity = Encumbrance.capacity(body);
        overload = Encumbrance.overload(body.needs.carried, capacity);
        consciousnessCap = ConsciousnessSystem.cap(body, null);
    }
}
