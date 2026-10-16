package com.fleshbone.sim.system;

import java.util.random.RandomGenerator;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyEvent;
import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.Chemistry;
import com.fleshbone.sim.Environment;
import com.fleshbone.sim.Needs;
import com.fleshbone.sim.PartState;
import com.fleshbone.sim.Vitals;
import com.fleshbone.sim.WeightClass;

/**
 * Heart, vessels and blood pressure, after Casualties: Unknown.
 * <ul>
 *   <li>Pressure: 120/80 shifted by blood volume (±0.66/0.44 per 25 mL), viscosity, vessel tone, sepsis, heat, body
 *       weight, thirst, tiredness and stimulants, plus what the heart rate pumps, scaled down by opioids.</li>
 *   <li>Heart rate: 70 + baroreflex + adrenaline ×0.55 + pain + missing stamina ×0.6 (up to 60) + |T − 37| ×0.5
 *       + fibrillation − opioids ×0.2 + withdrawal ×0.125 − viscosity ×0.05.</li>
 *   <li>Baroreflex: the heart adds up to 80 bpm (or removes 30) to bring the pressure back to 120, over a few seconds.
 *       Effort resets it: the rise in pressure from exertion is not fought.</li>
 *   <li>Fibrillation: started by a racing heart (&gt;200), low pressure (&lt;78), thick blood, deep cold, hypoxia,
 *       extreme weight, water intoxication or severe withdrawal; kept going below 88 mmHg; rises about 1 %/s and falls
 *       0.75 %/s. At 100 the heart stops.</li>
 * </ul>
 */
public final class CirculationSystem {
    /** Below this systolic pressure an existing fibrillation keeps growing. */
    public static final float FIBRILLATION_PRESSURE = 88f;
    /** Below this systolic pressure the heart starts to fibrillate. */
    public static final float FIBRILLATION_TRIGGER = 78f;
    public static final float COLLAPSE_PRESSURE = 60f;
    /** Viscosity above which a clot can reach the lungs. */
    public static final float EMBOLISM_VISCOSITY = 90f;
    /** Systolic pressure above which a stroke becomes possible. */
    public static final float STROKE_PRESSURE = 180f;
    public static final float BARO_MIN = -30f;
    public static final float BARO_MAX = 80f;
    /** Baroreflex bpm wanted per mmHg below 120 (before what the heart rate itself pumps). */
    static final float BARO_GAIN = 1.6f;
    /** bpm per second the baroreflex moves. */
    static final float BARO_RATE = 1.5f;
    public static final float FIBRILLATION_RISE = 1f;
    public static final float FIBRILLATION_FALL = 0.75f;
    public static final float WATER_INTOXICATION_RISE = 1.66f;
    /** Chance per second for epinephrine to restart a stopped heart that blood still reaches (CPR). */
    public static final float EPINEPHRINE_RESTART = 0.03f;
    /** Heart rate below which the heart gives up. */
    public static final float ARREST_RATE = 20f;

    private CirculationSystem() {}

    public static void tick(Body body, Environment env, float dt) {
        tick(body, env, dt, null);
    }

    public static void tick(Body body, Environment env, float dt, RandomGenerator rng) {
        Vitals v = body.vitals;
        Chemistry c = body.chem;
        updateViscosity(body, dt, rng);
        updateTone(body, dt);
        if (v.cardiacArrest) {
            v.arrestTime += dt;
            v.heartRate = 0f;
            v.baroreflex = SimMath.approach(v.baroreflex, BARO_MAX, BARO_RATE * dt);
            v.systolic = 35f * v.cprPerfusion;
            v.diastolic = 15f * v.cprPerfusion;
            boolean perfused = v.cprPerfusion > 0.3f;
            v.cprPerfusion = Math.max(0f, v.cprPerfusion - 0.25f * dt);
            if (rng != null && c.epinephrine > 20f && rng.nextFloat() < EPINEPHRINE_RESTART * (perfused ? 1f : 0.25f) * dt) {
                restartHeart(body);
            }
            return;
        }

        float base = baseSystolic(body);
        float resting = base - exertionPressure(body);
        v.baroreflex = SimMath.approach(v.baroreflex, SimMath.clamp((120f - resting) * BARO_GAIN, BARO_MIN, BARO_MAX), BARO_RATE * dt);
        float hrTarget = heartRateTarget(body, env);
        v.heartRate = SimMath.approach(v.heartRate, hrTarget, (hrTarget > v.heartRate ? 4f : 2f) * dt);

        float systolic = base + cardiacOutput(v.heartRate) - 0.4f * Math.max(0f, v.fibrillation - 30f);
        systolic *= 1f - 0.0025f * Math.min(100f, ChemistrySystem.relief(c));
        v.systolic = SimMath.approach(v.systolic, Math.max(0f, systolic), 6f * dt);
        v.diastolic = v.systolic * (2f / 3f);

        updateFibrillation(body, dt);
        if (!v.cardiacArrest && v.heartRate < ARREST_RATE && v.lastStand <= 0f) stopHeart(body);

        // Hypertensive crisis: risk of a stroke
        if (rng != null && v.systolic > STROKE_PRESSURE && rng.nextFloat() < 0.005f * dt) {
            PartState head = body.part(BodyPart.HEAD);
            v.brainHealth = Math.max(0f, v.brainHealth - 15f);
            v.consciousness = Math.max(0f, v.consciousness - 40f);
            head.pain = Math.min(150f, head.pain + 40f);
            body.emit(BodyEvent.Type.STROKE, null);
        }
    }

    /** Systolic pressure from everything but the heart rate. */
    public static float baseSystolic(Body body) {
        Vitals v = body.vitals;
        Needs n = body.needs;
        Chemistry c = body.chem;
        float volume = v.bloodVolume - Vitals.NORMAL_BLOOD_VOLUME;
        float p = 120f
                + (volume < 0f ? volume / 0.025f * 0.66f : volume / 0.025f * 0.3f)
                + 0.6f * v.viscosity
                + 30f * v.vesselTone
                - 0.6f * v.sepsis
                - 3f * Math.max(0f, v.bodyTemp - 37.5f)
                + body.derived.weightClass.pressureEffect()
                - 0.25f * Math.max(0f, 40f - n.thirst)
                + 0.1f * Math.max(0f, n.thirst - ThirstSystem.SLAKED)
                + exertionPressure(body)
                + 0.08f * c.caffeine
                + 0.2f * c.epinephrine
                + 0.15f * ChemistrySystem.withdrawal(c)
                - 0.1f * c.antiarrhythmic
                - 0.8f * c.anaphylaxis;
        return p;
    }

    /** Systolic mmHg added by effort: 0.2 per missing point of stamina. */
    static float exertionPressure(Body body) {
        return 0.2f * (100f - body.needs.stamina);
    }

    /** Systolic mmHg the heart rate adds: falls off when it races past 180, and quickly when it drops below 70. */
    static float cardiacOutput(float heartRate) {
        if (heartRate < 70f) return (heartRate - 70f) * 0.5f;
        return (heartRate - 70f) * 0.25f - Math.max(0f, heartRate - 180f) * 0.6f;
    }

    public static float heartRateTarget(Body body, Environment env) {
        Vitals v = body.vitals;
        Needs n = body.needs;
        Chemistry c = body.chem;
        float hr = 70f + v.baroreflex
                + (v.adrenaline + c.epinephrine) * 0.55f
                + body.derived.totalPain
                + Math.min(60f, (100f - n.stamina) * 0.6f)
                + Math.abs(v.bodyTemp - 37f) * 0.5f
                + fibrillationDrive(v.fibrillation)
                - ChemistrySystem.relief(c) * 0.2f
                + ChemistrySystem.withdrawal(c) * 0.4f
                + c.anaphylaxis * 0.3f
                - SimMath.clamp(v.viscosity * 0.05f, -20f, 20f)
                + c.caffeine * 0.15f
                - c.antiarrhythmic * 0.1f
                - (body.isAsleep(env) ? 8f : 0f)
                + (n.thirst <= 0f ? 15f : 0f);
        // A fever keeps the heart going fast
        if (v.bodyTemp > 38.5f) hr = Math.max(hr, 90f + (v.bodyTemp - 38.5f) * 10f);
        if (v.lastStand > 0f) hr = Math.max(hr, 90f);
        return SimMath.clamp(hr, 0f, 250f);
    }

    static float fibrillationDrive(float fibrillation) {
        return fibrillation + (fibrillation > 75f ? 4f : 0f) + (fibrillation > 95f ? 30f : 0f);
    }

    /**
     * Vessels tighten with exertion (stamina below 50), adrenaline above 30, cold and severe dehydration; they relax
     * with heat and opioids. The tone follows its target over about 20 seconds.
     */
    static void updateTone(Body body, float dt) {
        Vitals v = body.vitals;
        Needs n = body.needs;
        float t = v.bodyTemp;
        float target = 0f;
        if (t < 33f) target += 0.9f;
        else if (t < 34.5f) target += 0.6f;
        else if (t < 35.5f) target += 0.3f;
        else if (t < 36.5f) target += 0.15f;
        if (n.stamina < 50f) target += 0.3f * (50f - n.stamina) / 50f;
        float adrenaline = v.adrenaline + body.chem.epinephrine;
        if (adrenaline > 30f) target += 0.3f * Math.min(1f, (adrenaline - 30f) / 40f);
        if (n.thirst <= 0f) target += 0.3f;
        if (t > 37.5f) target -= 0.2f * (t - 37.5f);
        target -= 0.004f * ChemistrySystem.relief(body.chem);
        v.vesselTone = SimMath.approach(v.vesselTone, SimMath.clamp(target, -1f, 1f), 0.05f * dt);
    }

    static void updateFibrillation(Body body, float dt) {
        Vitals v = body.vitals;
        Needs n = body.needs;
        Chemistry c = body.chem;
        boolean trigger = v.heartRate > 200f
                || v.systolic < FIBRILLATION_TRIGGER
                || v.viscosity > 95f
                || v.bodyTemp < TemperatureSystem.FIBRILLATION_COLD
                || v.spo2 < 50f
                || WeightClass.fibrillates(n.weight)
                || ChemistrySystem.withdrawal(c) > ChemistrySystem.SEVERE_WITHDRAWAL
                || v.sepsis > 80f
                || c.epinephrine > 80f;
        boolean sustain = v.systolic < FIBRILLATION_PRESSURE || v.viscosity > 80f || v.spo2 < 60f || v.heartRate > 180f
                || v.bodyTemp < 30f;
        float rise = 0f;
        if (n.thirst > ThirstSystem.WATER_INTOXICATION) rise = WATER_INTOXICATION_RISE;
        else if (trigger || (sustain && v.fibrillation > 0f)) rise = FIBRILLATION_RISE;
        if (v.lastStand > 0f) rise = 0f;
        if (rise > 0f) {
            v.fibrillation = Math.min(100f, v.fibrillation + rise * (1f - Math.min(0.8f, c.antiarrhythmic / 100f)) * dt);
        } else {
            v.fibrillation = Math.max(0f, v.fibrillation - (FIBRILLATION_FALL + c.antiarrhythmic * 0.02f) * dt);
        }
        if (v.fibrillation >= 100f) stopHeart(body);
    }

    /**
     * Blood viscosity follows procoagulants and thrombolytics, thickens with dehydration and thins with venom;
     * otherwise it slowly returns to normal. Very thick blood can throw a clot into the lungs (pulmonary embolism),
     * which strangles the chest until thinned.
     */
    static void updateViscosity(Body body, float dt, RandomGenerator rng) {
        Vitals v = body.vitals;
        Chemistry c = body.chem;
        float drugTarget = Math.min(100f, c.procoagulant * 1.2f) - c.thrombolytic * 0.9f;
        float bodyTarget = Math.max(0f, 20f - body.needs.thirst) - Math.max(0f, c.venom - 30f) * 0.5f;
        float rate = Math.abs(drugTarget) > 1f ? 2f : Math.abs(bodyTarget) > 1f ? 0.2f : 0.05f;
        v.viscosity = SimMath.approach(v.viscosity, SimMath.clamp(drugTarget + bodyTarget, -100f, 100f), rate * dt);
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
        v.pendingCause = com.fleshbone.sim.DeathCheck.classify(body);
        v.cardiacArrest = true;
        v.arrestTime = 0f;
        v.fibrillation = 100f;
        v.heartRate = 0f;
        body.emit(BodyEvent.Type.CARDIAC_ARREST, null);
    }

    /** Return of spontaneous circulation (defibrillation, epinephrine, a lucky compression, a last stand). */
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
}
