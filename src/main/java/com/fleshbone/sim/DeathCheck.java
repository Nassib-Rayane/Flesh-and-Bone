package com.fleshbone.sim;

import com.fleshbone.sim.system.ChemistrySystem;
import com.fleshbone.sim.system.ThirstSystem;

/** Decides whether the body is dead and why. Everything else leads here through brain damage. */
public final class DeathCheck {
    /** Below this blood volume death is immediate, whatever the heart does. */
    public static final float EXSANGUINATION_LITRES = 0.75f;

    private DeathCheck() {}

    /** @return the cause of death, or {@code null} while alive */
    public static DeathCause check(Body body) {
        Vitals v = body.vitals;
        if (v.headDestroyed) return DeathCause.BRAIN_TRAUMA;
        if (v.bloodVolume <= EXSANGUINATION_LITRES) return DeathCause.HEMORRHAGE;
        if (v.brainHealth <= 0f) return v.pendingCause;
        return null;
    }

    /** The process most likely to be killing the body right now, most specific first. */
    public static DeathCause classify(Body body) {
        Vitals v = body.vitals;
        Needs n = body.needs;
        Chemistry c = body.chem;
        if (v.headDestroyed) return DeathCause.BRAIN_TRAUMA;
        if (v.bloodVolume < 2.5f) return DeathCause.HEMORRHAGE;
        if (v.bodyTemp > 41.5f) return DeathCause.HEATSTROKE;
        if (v.bodyTemp < 30f) return DeathCause.HYPOTHERMIA;
        if (ChemistrySystem.relief(c) > ChemistrySystem.OVERDOSE) return DeathCause.OVERDOSE;
        if (v.sepsis > 75f) return DeathCause.SEPSIS;
        if (n.thirst > ThirstSystem.WATER_INTOXICATION) return DeathCause.WATER_INTOXICATION;
        if (c.venom > 40f) return DeathCause.ENVENOMATION;
        if (n.thirst <= 0f) return DeathCause.DEHYDRATION;
        if (n.hunger <= 0f) return DeathCause.STARVATION;
        if (v.spo2 < 60f) return DeathCause.ASPHYXIA;
        if (v.bloodVolume < 3.3f) return DeathCause.HEMORRHAGE;
        return DeathCause.CARDIAC_ARREST;
    }
}
