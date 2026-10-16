package com.fleshbone.sim.system;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyEvent;
import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.Environment;
import com.fleshbone.sim.Vitals;
import com.fleshbone.sim.injury.InjuryApplier;

/**
 * Consciousness rises 3 %/s toward a cap set by the worst of: head muscle, oxygen, blood pressure, pain, brain
 * health, sleepiness, nausea and temperature. It drops straight to the cap.
 */
public final class ConsciousnessSystem {
    public static final float CONFUSED = 90f;
    public static final float VERY_CONFUSED = 72f;
    public static final float DARKENING = 70f;
    public static final float FAINTING = 55f;
    public static final float INCAPACITATED = 30f;
    public static final float UNCONSCIOUS = 20f;
    public static final float RECOVERY_PER_SECOND = 3f;

    private ConsciousnessSystem() {}

    /** @param env may be {@code null} on the client, where sleep is unknown */
    public static float cap(Body body, Environment env) {
        Vitals v = body.vitals;
        float headMuscle = body.part(BodyPart.HEAD).muscle;
        float cap = 100f;
        cap = Math.min(cap, 2.1f * headMuscle);
        if (headMuscle < InjuryApplier.CONCUSSION_MUSCLE) cap = Math.min(cap, 25f);
        cap = Math.min(cap, 1.2f * v.spo2);
        if (v.systolic < 110f) cap -= 1.4f * (110f - v.systolic);
        else if (v.systolic > 140f) cap -= 1.167f * (v.systolic - 140f);
        if (v.systolic < CirculationSystem.COLLAPSE_PRESSURE) cap = 0f;
        float pain = body.derived.totalPain;
        cap = Math.min(cap, 150f - pain);
        if (pain >= PainSystem.COLLAPSE) cap = Math.min(cap, 25f);
        if (v.shock >= PainSystem.SHOCK_UNCONSCIOUS) cap = Math.min(cap, 10f);
        cap = Math.min(cap, v.brainHealth);
        cap = Math.min(cap, energyCap(body.needs.energy));
        cap = Math.min(cap, 150f - body.needs.sickness);
        cap = Math.min(cap, temperatureCap(v.bodyTemp));
        float opioid = body.chem.opiate - body.chem.opiateTolerance * 0.4f;
        if (opioid > 50f) cap = Math.min(cap, 150f - opioid);
        if ((env != null && env.sleeping) || body.needs.passedOut) cap = Math.min(cap, 10f);
        if (v.cardiacArrest) cap = 0f;
        return SimMath.clamp(cap, 0f, 100f);
    }

    /** Sleepiness: no cap above 24 energy, then 4.2 × energy with a floor of 31 %. */
    static float energyCap(float energy) {
        return energy >= 23.8f ? 100f : Math.max(31f, 4.2f * energy);
    }

    /** No cap between 33 and 40.7 °C. */
    static float temperatureCap(float temp) {
        if (temp < 33f) return 100f - (33f - temp) * 14f;
        if (temp > 40.7f) return 100f - (temp - 40.7f) * 15f;
        return 100f;
    }

    public static void tick(Body body, Environment env, float dt) {
        Vitals v = body.vitals;
        float before = v.consciousness;
        float cap = cap(body, env);
        body.derived.consciousnessCap = cap;
        if (v.cardiacArrest) {
            v.consciousness = Math.max(0f, v.consciousness - 15f * dt);
        } else if (v.consciousness > cap) {
            v.consciousness = Math.max(cap, v.consciousness - 30f * dt);
        } else {
            float stimulants = body.chem.epinephrine * 0.03f + body.chem.caffeine * 0.01f;
            v.consciousness = Math.min(cap, v.consciousness + (RECOVERY_PER_SECOND + stimulants) * dt);
        }
        if (env != null && env.sleeping) return;
        if (before >= UNCONSCIOUS && v.consciousness < UNCONSCIOUS) body.emit(BodyEvent.Type.FAINTED, null);
        else if (before < UNCONSCIOUS && v.consciousness >= UNCONSCIOUS) body.emit(BodyEvent.Type.WOKE_UP, null);
    }
}
