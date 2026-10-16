package com.fleshbone.sim.system;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.Environment;
import com.fleshbone.sim.PartState;
import com.fleshbone.sim.Vitals;

/** External and internal bleeding, clotting, hemothorax and blood regeneration. Rates are in litres per minute. */
public final class BleedingSystem {
    /** External: bleed 100 = 1.8 L/min. */
    public static final float LPM_PER_BLEED = 0.018f;
    public static final float MAX_EXTERNAL_LPM = 1.8f;
    /** Internal: internalBleed 100 = 0.45 L/min. */
    public static final float LPM_PER_INTERNAL = 0.0045f;
    public static final float MINOR_BLEEDING_LPM = 0.001f;
    public static final float BLEEDING_LPM = 0.09f;
    public static final float HEAVY_BLEEDING_LPM = 0.225f;
    public static final float CATASTROPHIC_BLEEDING_LPM = 0.45f;
    public static final float INTERNAL_BLEEDING_LPM = 0.04275f;
    /** Regeneration at a full food bar, scaled down with hunger. */
    public static final float BLOOD_REGEN_LPM = 0.0525f;
    /** Vanilla food exhaustion per litre regenerated (4 exhaustion = 1 food point). */
    public static final float EXHAUSTION_PER_LITRE = 16f;

    private BleedingSystem() {}

    /** External bleeding of one part after the skin cap, tourniquets, stitches, dressings, viscosity and pressure. */
    public static float externalRate(Body body, BodyPart part) {
        return externalRate(body, part, true);
    }

    static float externalRate(Body body, BodyPart part, boolean withDressing) {
        PartState s = body.part(part);
        if (s.amputated || s.skin >= 100f || s.bleed <= 0f) return 0f;
        if (body.isTourniquetted(part)) return 0f;
        float rate = Math.min(s.bleed * LPM_PER_BLEED, MAX_EXTERNAL_LPM * (1f - s.skin / 100f));
        if (s.sutured) rate *= 0.05f;
        if (withDressing && s.isBandaged()) {
            rate *= 1f - s.bandage.stopFactor * s.bandageQuality * (1f - 0.7f * s.bandageSoak);
        }
        return rate * pressureFactor(body) * viscosityFactor(body);
    }

    public static float internalRate(Body body, BodyPart part) {
        PartState s = body.part(part);
        if (!part.canBleedInternally() || s.internalBleed <= 0f) return 0f;
        return s.internalBleed * LPM_PER_INTERNAL * pressureFactor(body) * viscosityFactor(body);
    }

    /** Low blood pressure slows bleeding; a stopped heart barely bleeds. */
    static float pressureFactor(Body body) {
        Vitals v = body.vitals;
        if (v.cardiacArrest) return 0.1f + 0.4f * v.cprPerfusion;
        return SimMath.clamp(v.systolic / 120f, 0.25f, 1.3f);
    }

    /** Thin blood (negative viscosity) bleeds more: −50 → ×1.5. Thick blood bleeds less: +50 → ×0.67. */
    static float viscosityFactor(Body body) {
        float visc = body.vitals.viscosity;
        return visc < 0f ? 1f - visc / 100f : 1f / (1f + visc / 100f);
    }

    /** Clotting speed: C0 × (1 + viscosity / 100), boosted by procoagulants. */
    static float clotMultiplier(Body body) {
        float m = Math.max(0.1f, 1f + body.vitals.viscosity / 100f);
        return m * (1f + body.chem.procoagulant / 40f);
    }

    public static void tick(Body body, Environment env, float dt) {
        Vitals v = body.vitals;
        float clot = clotMultiplier(body);
        float lostLitres = 0f;
        for (BodyPart part : BodyPart.VALUES) {
            PartState s = body.part(part);
            if (s.amputated) continue;
            float external = externalRate(body, part, true);
            lostLitres += (external + internalRate(body, part)) * dt / 60f;

            if (s.isBandaged() && s.bleed > 0f) {
                float raw = externalRate(body, part, false);
                s.bandageSoak = Math.min(1f, s.bandageSoak + raw * s.bandage.soakPerLitre * dt / 60f);
            }
            if (s.bleed > 0f) {
                // Embedded fragments keep the wound open unless it is dressed
                boolean blocked = s.shrapnel > 0 && !s.isBandaged();
                if (!blocked) {
                    float rate = (0.02f + 0.004f * s.bleed) * clot;
                    if (s.isBandaged()) rate *= 1f + 2f * s.bandageQuality * (1f - s.bandageSoak);
                    if (s.sutured) rate *= 4f;
                    if (body.isTourniquetted(part)) rate *= 2f;
                    s.bleed = Math.max(0f, s.bleed - rate * dt);
                }
            }
            if (s.internalBleed > 0f) {
                float rate = (0.004f + 0.0008f * s.internalBleed) * clot;
                s.internalBleed = Math.max(0f, s.internalBleed - rate * dt);
            }
        }

        // A torn abdomen keeps bleeding inside
        PartState abdomen = body.part(BodyPart.ABDOMEN);
        if (abdomen.muscle < 4f) abdomen.internalBleed = Math.min(100f, abdomen.internalBleed + 0.05f * dt);

        // Blood pooling around the lungs
        float thoracic = internalRate(body, BodyPart.THORAX);
        if (thoracic >= INTERNAL_BLEEDING_LPM) {
            v.hemothorax += 2.64f / 60f * (thoracic / INTERNAL_BLEEDING_LPM) * dt;
        } else {
            v.hemothorax = Math.max(0f, v.hemothorax - 2.16f / 60f * dt);
        }

        v.bloodVolume = Math.max(0f, v.bloodVolume - lostLitres);
        if (v.bloodVolume < Vitals.NORMAL_BLOOD_VOLUME) {
            if (!v.cardiacArrest) {
                float regen = BLOOD_REGEN_LPM / 60f * (env.foodLevel / 20f) * dt;
                regen = Math.min(regen, Vitals.NORMAL_BLOOD_VOLUME - v.bloodVolume);
                v.bloodVolume += regen;
                body.exhaustionDebt += regen * EXHAUSTION_PER_LITRE;
            }
        } else if (v.bloodVolume > Vitals.NORMAL_BLOOD_VOLUME) {
            v.bloodVolume = Math.max(Vitals.NORMAL_BLOOD_VOLUME, v.bloodVolume - 0.03f / 60f * dt);
        }
    }
}
