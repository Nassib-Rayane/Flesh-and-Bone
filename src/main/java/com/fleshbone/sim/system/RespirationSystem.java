package com.fleshbone.sim.system;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyEvent;
import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.Chemistry;
import com.fleshbone.sim.Environment;
import com.fleshbone.sim.Needs;
import com.fleshbone.sim.Vitals;

/**
 * Breathing and blood oxygen, after Casualties: Unknown.
 * <ul>
 *   <li>Breathing rate: 25/min at rest, +0.1 per missing point of stamina, +0.06 per point of pain, −0.25 per point of
 *       opioid reception (collapsing past 80), slowed by high pressure, blood around the lungs and fibrillation.
 *       It changes by at most 2 breaths/min each second.</li>
 *   <li>SpO2 changes by 0.04 %/s per breath minus 0.5 %/s (so 12.5 breaths/min hold it steady), minus what
 *       exertion uses, up to a maximum lowered by blood loss below 3 L, abnormal viscosity, hemothorax and venom.</li>
 * </ul>
 * SpO2 here also stands for oxygen reaching the brain, so it falls when the heart stops.
 */
public final class RespirationSystem {
    /** Thorax muscle below which the lungs fail. */
    public static final float LUNG_FAILURE_MUSCLE = 5f;
    public static final float RESPIRATORY_ARREST_RATE = 2.5f;
    /** SpO2 lost per second when no air reaches the lungs. */
    public static final float APNEA_DESATURATION = 1.5f;
    /** SpO2 lost per second when blood stops circulating. */
    public static final float ARREST_DESATURATION = 2.5f;
    public static final float BASE_DECLINE = 0.5f;
    public static final float PER_BREATH = 0.04f;
    /** SpO2 used per second at zero stamina. */
    public static final float EXERTION_USE = 0.35f;
    /** Breaths/min the rate can change per second. */
    public static final float RATE_INERTIA = 2f;

    private RespirationSystem() {}

    public static void tick(Body body, Environment env, float dt) {
        Vitals v = body.vitals;
        Needs n = body.needs;
        boolean lungFailure = body.part(BodyPart.THORAX).muscle < LUNG_FAILURE_MUSCLE;
        boolean wasBreathing = v.respRate >= RESPIRATORY_ARREST_RATE;

        float target = targetRate(body, env);
        if (lungFailure || (v.cardiacArrest && v.arrestTime > 20f)) target = 0f;
        if (v.lastStand > 0f) target = Math.max(target, 18f);
        v.respRate = SimMath.approach(v.respRate, SimMath.clamp(target, 0f, 40f), RATE_INERTIA * dt);
        boolean breathing = v.respRate >= RESPIRATORY_ARREST_RATE;
        if (wasBreathing && !breathing) body.emit(BodyEvent.Type.RESPIRATORY_ARREST, null);

        // Rescue breaths during CPR ventilate a body that no longer breathes on its own
        boolean rescued = v.cprPerfusion > 0.3f;
        float ventilation = rescued ? Math.max(v.respRate, 16f) : v.respRate;
        float max = maxSaturation(body);
        if (v.cardiacArrest) max = Math.min(max, 70f * v.cprPerfusion);

        if (env.breathingBlocked || (!breathing && !rescued)) {
            v.spo2 = Math.max(0f, v.spo2 - APNEA_DESATURATION * dt);
        } else if (v.cardiacArrest && v.spo2 > max) {
            v.spo2 = Math.max(max, v.spo2 - ARREST_DESATURATION * dt);
        } else if (v.spo2 > max) {
            v.spo2 = Math.max(max, v.spo2 - 2f * dt);
        } else {
            float delta = PER_BREATH * ventilation - BASE_DECLINE - EXERTION_USE * (100f - n.stamina) / 100f;
            v.spo2 = SimMath.clamp(v.spo2 + delta * dt, 0f, max);
        }
    }

    public static float targetRate(Body body, Environment env) {
        Vitals v = body.vitals;
        Needs n = body.needs;
        Chemistry c = body.chem;
        float relief = ChemistrySystem.relief(c);
        float rate = 25f
                + 0.1f * (100f - n.stamina)
                + 0.06f * body.derived.totalPain
                + 0.05f * (v.adrenaline + c.epinephrine)
                - 0.25f * relief
                - (relief > ChemistrySystem.OVERDOSE ? 20f + (relief - ChemistrySystem.OVERDOSE) * 0.5f : 0f)
                - 0.1f * Math.max(0f, v.systolic - 140f)
                - 0.125f * v.hemothorax
                - 0.1f * v.fibrillation
                - 0.12f * c.anaphylaxis;
        // Air hunger: low oxygen makes the body breathe harder
        if (v.spo2 < 90f) rate += (90f - v.spo2) * 0.2f;
        if (v.bodyTemp > 38f) rate += (v.bodyTemp - 38f) * 2f;
        else if (v.bodyTemp < 35f) rate -= (35f - v.bodyTemp) * 1.5f;
        if (body.isAsleep(env)) rate -= 3f;
        if (body.part(BodyPart.THORAX).isFractured()) rate -= 4f;
        return rate;
    }

    /** Highest SpO2 the blood can carry now. */
    public static float maxSaturation(Body body) {
        Vitals v = body.vitals;
        float max = 100f
                - (v.bloodVolume < 3f ? (3f - v.bloodVolume) / 0.025f * 0.8f : 0f)
                - Math.max(0f, Math.abs(v.viscosity) - 40f) * 0.4f
                - v.hemothorax * 0.3f
                - body.chem.venom * 0.5f
                - body.chem.anaphylaxis * 0.25f
                - (v.embolism ? 25f : 0f);
        return SimMath.clamp(max, 0f, 100f);
    }
}
