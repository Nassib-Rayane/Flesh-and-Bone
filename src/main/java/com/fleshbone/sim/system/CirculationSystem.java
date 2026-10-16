package com.fleshbone.sim.system;

import java.util.random.RandomGenerator;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyEvent;
import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.PartState;
import com.fleshbone.sim.Chemistry;
import com.fleshbone.sim.DeathCause;
import com.fleshbone.sim.Environment;
import com.fleshbone.sim.Vitals;

/**
 * Blood pressure, heart rate, fibrillation and cardiac arrest.
 * Blood loss lowers the target pressure (−0.66 systolic per 25 mL) and the heart speeds up to compensate.
 * Sustained low pressure makes the heart fibrillate; at 100 it stops.
 */
public final class CirculationSystem {
    public static final float FIBRILLATION_PRESSURE = 88f;
    public static final float COLLAPSE_PRESSURE = 60f;
    /** Systolic mmHg recovered per bpm above 70. */
    private static final float HR_COMPENSATION = 0.25f;

    private CirculationSystem() {}

    /** Viscosity above which a clot can reach the lungs. */
    public static final float EMBOLISM_VISCOSITY = 90f;
    /** Systolic pressure above which a stroke becomes possible. */
    public static final float STROKE_PRESSURE = 180f;

    public static void tick(Body body, Environment env, float dt) {
        tick(body, env, dt, null);
    }

    public static void tick(Body body, Environment env, float dt, RandomGenerator rng) {
        Vitals v = body.vitals;
        Chemistry c = body.chem;
        updateViscosity(body, dt, rng);
        if (v.cardiacArrest) {
            v.arrestTime += dt;
            v.heartRate = 0f;
            v.systolic = 35f * v.cprPerfusion;
            v.diastolic = 15f * v.cprPerfusion;
            v.cprPerfusion = Math.max(0f, v.cprPerfusion - 0.25f * dt);
            return;
        }

        float deficit = Math.max(0f, Vitals.NORMAL_BLOOD_VOLUME - v.bloodVolume);
        float excess = Math.max(0f, v.bloodVolume - Vitals.NORMAL_BLOOD_VOLUME);
        float pain = body.derived.totalPain;
        float baseSystolic = 120f
                - deficit / 0.025f * 0.66f
                + excess / 0.025f * 0.3f
                - opiateEffect(c)
                - v.sepsis * 0.5f
                + Math.min(pain, 60f) * 0.15f
                + (v.adrenaline + c.epinephrine) * 0.15f
                + c.caffeine * 0.08f
                + v.viscosity * 0.3f
                + temperatureEffect(v.bodyTemp)
                + thirstEffect(body.needs.thirst);

        float hrTarget = 70f
                + SimMath.clamp((120f - baseSystolic) * 1.5f, -20f, 100f)
                + v.adrenaline * 0.4f + c.epinephrine * 0.6f
                + Math.min(pain, 80f) * 0.25f
                + c.caffeine * 0.15f
                + exertion(env)
                + Math.max(0f, v.bodyTemp - 37.5f) * 10f
                - c.opiate * 0.15f
                - c.antiarrhythmic * 0.1f
                + ChemistrySystem.withdrawal(c) * 0.4f
                - (env.sleeping ? 10f : 0f);
        hrTarget = SimMath.clamp(hrTarget, 35f, 190f);
        v.heartRate = SimMath.approach(v.heartRate, hrTarget, (hrTarget > v.heartRate ? 3f : 1.5f) * dt);

        float systolic = Math.max(0f, baseSystolic + (v.heartRate - 70f) * HR_COMPENSATION);
        v.systolic = SimMath.approach(v.systolic, systolic, 6f * dt);
        v.diastolic = v.systolic * (2f / 3f);

        float gain = 0f;
        if (v.systolic < FIBRILLATION_PRESSURE) gain += (FIBRILLATION_PRESSURE - v.systolic) * 0.02f;
        if (v.systolic < COLLAPSE_PRESSURE) gain += 0.8f;
        if (v.viscosity > 80f) gain += (v.viscosity - 80f) * 0.05f;
        if (v.bodyTemp < 30f) gain += (30f - v.bodyTemp) * 0.15f;
        if (v.heartRate > 180f) gain += (v.heartRate - 180f) * 0.03f;
        if (v.spo2 < 70f) gain += (70f - v.spo2) * 0.02f;
        if (v.sepsis > 80f) gain += (v.sepsis - 80f) * 0.05f;
        gain *= 1f - Math.min(0.8f, c.antiarrhythmic / 100f);
        if (gain > 0f) {
            v.fibrillation = Math.min(100f, v.fibrillation + gain * dt);
        } else {
            v.fibrillation = Math.max(0f, v.fibrillation - (0.4f + c.antiarrhythmic * 0.02f) * dt);
        }
        if (v.fibrillation >= 100f) stopHeart(body);

        // Hypertensive crisis: risk of a stroke
        if (rng != null && v.systolic > STROKE_PRESSURE && rng.nextFloat() < 0.005f * dt) {
            PartState head = body.part(BodyPart.HEAD);
            v.brainHealth = Math.max(0f, v.brainHealth - 15f);
            v.consciousness = Math.max(0f, v.consciousness - 40f);
            head.pain = Math.min(150f, head.pain + 40f);
            body.emit(BodyEvent.Type.STROKE, null);
        }
    }

    /**
     * Blood viscosity follows procoagulants and thrombolytics and otherwise returns to normal (3 points a minute).
     * Very thick blood can throw a clot into the lungs (pulmonary embolism), which strangles the chest until thinned.
     */
    static void updateViscosity(Body body, float dt, RandomGenerator rng) {
        Vitals v = body.vitals;
        Chemistry c = body.chem;
        float drugTarget = Math.min(100f, c.procoagulant * 1.2f) - c.thrombolytic * 0.9f;
        if (Math.abs(drugTarget) > 1f) v.viscosity = SimMath.approach(v.viscosity, drugTarget, 2f * dt);
        else v.viscosity = SimMath.approach(v.viscosity, 0f, 0.05f * dt);
        if (!v.embolism && rng != null && v.viscosity > EMBOLISM_VISCOSITY && rng.nextFloat() < 0.01667f * dt) {
            v.embolism = true;
        }
        if (v.embolism) {
            PartState thorax = body.part(BodyPart.THORAX);
            thorax.muscle = Math.max(0f, thorax.muscle - 0.6f * dt);
            if ((v.viscosity < 50f && c.thrombolytic > 20f) || v.viscosity < 0f) v.embolism = false;
        }
    }

    /** Cardiac arrest: the heart stops, pressure drops to zero. */
    public static void stopHeart(Body body) {
        Vitals v = body.vitals;
        if (v.cardiacArrest) return;
        v.cardiacArrest = true;
        v.arrestTime = 0f;
        v.fibrillation = 100f;
        v.heartRate = 0f;
        v.pendingCause = classifyArrest(body);
        body.emit(BodyEvent.Type.CARDIAC_ARREST, null);
    }

    /** Return of spontaneous circulation (after a successful defibrillation). */
    public static void restartHeart(Body body) {
        Vitals v = body.vitals;
        if (!v.cardiacArrest) return;
        v.cardiacArrest = false;
        v.arrestTime = 0f;
        v.fibrillation = 40f;
        v.heartRate = 110f;
        v.systolic = 80f;
        v.diastolic = 53f;
        body.emit(BodyEvent.Type.HEART_RESTARTED, null);
    }

    /** What most likely stopped the heart, for the death message. */
    public static DeathCause classifyArrest(Body body) {
        Vitals v = body.vitals;
        if (v.bloodVolume < 3.3f) return DeathCause.HEMORRHAGE;
        if (v.sepsis > 75f) return DeathCause.SEPSIS;
        if (v.bodyTemp < 31f) return DeathCause.HYPOTHERMIA;
        if (v.bodyTemp > 41.5f) return DeathCause.HEATSTROKE;
        if (body.chem.opiate > 80f) return DeathCause.OVERDOSE;
        if (body.needs.thirst <= 0f) return DeathCause.DEHYDRATION;
        if (body.needs.starvation > 600f) return DeathCause.STARVATION;
        if (v.spo2 < 50f) return DeathCause.ASPHYXIA;
        return DeathCause.CARDIAC_ARREST;
    }

    /** Opioids lower blood pressure; withdrawal raises it. */
    static float opiateEffect(Chemistry c) {
        float withdrawal = Math.max(0f, c.opiateDependence - c.opiate) * 0.3f;
        return Math.min(c.opiate, 100f) * 0.3f - withdrawal;
    }

    /** Heat dilates vessels (pressure drops); deep cold eventually drops it too. */
    static float temperatureEffect(float temp) {
        if (temp > 37.5f) return -3f * (temp - 37.5f);
        if (temp < 35f) return -2f * (35f - temp);
        return 0f;
    }

    /** Dehydration shrinks blood volume; overhydration raises pressure. */
    static float thirstEffect(float thirst) {
        if (thirst < 40f) return -(40f - thirst) * 0.4f;
        if (thirst > 100f) return (thirst - 100f) * 0.2f;
        return 0f;
    }

    static float exertion(Environment env) {
        if (env.sprinting) return 35f;
        if (env.swimming) return 25f;
        return env.moveSpeed > 1f ? 10f : 0f;
    }
}
