package com.fleshbone.sim.system;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyEvent;
import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.Chemistry;
import com.fleshbone.sim.Environment;
import com.fleshbone.sim.Vitals;

/**
 * Breathing rate and blood oxygen. SpO2 here stands for oxygen actually delivered to the brain, so it also falls
 * when the heart stops.
 */
public final class RespirationSystem {
    public static final float LUNG_FAILURE_MUSCLE = 4f;
    public static final float RESPIRATORY_ARREST_RATE = 2.5f;
    /** SpO2 lost per second when no air reaches the lungs. */
    public static final float APNEA_DESATURATION = 1.5f;
    /** SpO2 lost per second when blood stops circulating. */
    public static final float ARREST_DESATURATION = 2.5f;

    private RespirationSystem() {}

    public static void tick(Body body, Environment env, float dt) {
        Vitals v = body.vitals;
        Chemistry c = body.chem;
        boolean lungFailure = body.part(BodyPart.THORAX).muscle < LUNG_FAILURE_MUSCLE;
        boolean wasBreathing = v.respRate >= RESPIRATORY_ARREST_RATE;

        float target = 16f
                + (env.sprinting ? 10f : env.swimming ? 6f : env.moveSpeed > 1f ? 3f : 0f)
                + Math.max(0f, 95f - v.spo2) * 0.4f
                + v.adrenaline * 0.05f
                + Math.min(body.derived.totalPain, 80f) * 0.05f
                - opiateDepression(c)
                - v.hemothorax * 0.125f
                - (env.sleeping ? 3f : 0f);
        if (body.part(BodyPart.THORAX).isFractured()) target -= 3f;
        if (lungFailure || (v.cardiacArrest && v.arrestTime > 20f)) target = 0f;
        target = SimMath.clamp(target, 0f, 40f);
        v.respRate = SimMath.approach(v.respRate, target, 4f * dt);
        boolean breathing = v.respRate >= RESPIRATORY_ARREST_RATE;
        if (wasBreathing && !breathing) body.emit(BodyEvent.Type.RESPIRATORY_ARREST, null);

        // Rescue breaths during CPR ventilate a body that no longer breathes on its own
        boolean ventilated = breathing || v.cprPerfusion > 0.3f;
        float ventilation = v.cprPerfusion > 0.3f ? Math.max(v.respRate, 12f) : v.respRate;
        float spo2Target = 98f
                - v.hemothorax * 0.3f
                - (ventilation < 12f ? (12f - ventilation) * 5f : 0f)
                - Math.max(0f, Math.abs(v.viscosity) - 40f) * 0.4f
                - (v.embolism ? 25f : 0f)
                - Math.max(0f, 3.5f - v.bloodVolume) * 15f;
        if (v.cardiacArrest) spo2Target = Math.min(spo2Target, 70f * v.cprPerfusion);

        if (env.breathingBlocked || !ventilated) {
            v.spo2 = Math.max(0f, v.spo2 - APNEA_DESATURATION * dt);
        } else if (v.cardiacArrest && v.spo2 > spo2Target) {
            v.spo2 = Math.max(spo2Target, v.spo2 - ARREST_DESATURATION * dt);
        } else {
            v.spo2 = SimMath.approach(v.spo2, SimMath.clamp(spo2Target, 0f, 100f), (v.spo2 > spo2Target ? 2f : 1.5f) * dt);
        }
    }

    /** Opioids slow breathing: about 14/min fewer breaths at an overdose level of 80. */
    static float opiateDepression(Chemistry c) {
        float effective = Math.max(0f, c.opiate - c.opiateTolerance * 0.4f);
        float depression = effective * 0.12f;
        if (effective > 80f) depression += (effective - 80f) * 0.6f;
        return depression;
    }
}
