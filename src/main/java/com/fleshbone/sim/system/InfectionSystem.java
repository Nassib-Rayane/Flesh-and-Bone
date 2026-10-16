package com.fleshbone.sim.system;

import java.util.random.RandomGenerator;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyEvent;
import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.Environment;
import com.fleshbone.sim.PartState;
import com.fleshbone.sim.Vitals;

/**
 * Wound infection, its spread, sepsis and the immune system.
 * <ul>
 *   <li>Open wounds collect germs (the injury itself, dirt on the body, rags, soaked dressings) and may get infected.</li>
 *   <li>Infection grows unless the wound is disinfected or antibiotics are taken; strong immunity can clear it.</li>
 *   <li>Stages: &gt;0 stops regeneration, &gt;40 painful, &gt;60 severe (amputation possible), &gt;75 necrosis,
 *       &gt;90 spreads to adjacent parts.</li>
 *   <li>Total infection above 100 feeds sepsis (lethal around 93, through circulatory collapse).</li>
 * </ul>
 */
public final class InfectionSystem {
    /** Visible in the health panel and moodles above this. */
    public static final float NOTICEABLE = 25f;
    public static final float AMPUTATION_THRESHOLD = 60f;
    public static final float NECROSIS = 75f;
    public static final float SPREAD = 90f;
    /** Infection points per second at normal immunity (0 → 100 in about 33 minutes). */
    public static final float GROWTH = 0.05f;
    /** Infection seeding hazard per second for a fully contaminated, skinless wound at normal immunity factor. */
    public static final float SEED_RATE = 0.04f;
    public static final float SEPSIS_GROWTH = 0.0277f;
    public static final float SEPSIS_RECOVERY = 0.07f;
    /** Immunity at which infections clear on their own. */
    public static final float SELF_CLEARING_IMMUNITY = 196f;

    private InfectionSystem() {}

    public static void tick(Body body, Environment env, float dt, RandomGenerator rng) {
        Vitals v = body.vitals;
        float dirt = body.needs.dirtiness;
        float immunityFactor = SimMath.clamp(1.5f - v.immunity / 100f, 0.25f, 2f);
        float antibiotic = body.chem.antibiotic;
        float total = 0f;

        for (BodyPart part : BodyPart.VALUES) {
            PartState s = body.part(part);
            if (s.amputated) {
                s.infection = 0f;
                continue;
            }
            boolean open = s.hasOpenWound();
            // Germs reaching the wound
            if (s.disinfected > 0f) {
                s.contamination = Math.max(0f, s.contamination - 0.5f * dt);
            } else if (open || s.isBandaged()) {
                float gain = s.bandage.contamination;
                if (s.isBandaged() && s.bandageSoak > 0.8f) gain += 0.02f;
                if (open) gain += dirt * 0.002f;
                s.contamination = Math.min(100f, s.contamination + gain * dt);
            }

            float before = s.infection;
            if (s.infection <= 0f) {
                if (open && s.disinfected <= 0f && s.contamination > 0f) {
                    float chance = s.contamination / 100f * (1f - s.skin / 100f) * SEED_RATE * immunityFactor
                            * (dirt > 50f ? 1.5f : 1f) * (1f - Math.min(0.9f, antibiotic / 100f));
                    if (rng.nextFloat() < chance * dt) s.infection = 1f;
                }
            } else {
                float growth;
                if (s.disinfected > 0f && v.immunity >= 55f) {
                    growth = s.infection < 40f ? -0.02f : 0f;
                } else {
                    growth = GROWTH * immunityFactor * (1f - Math.min(0.9f, antibiotic / 100f * 0.9f));
                }
                if (antibiotic > 30f) growth -= 0.03f * antibiotic / 100f;
                if (v.immunity >= SELF_CLEARING_IMMUNITY) growth -= 0.05f;
                s.infection = SimMath.clamp(s.infection + growth * dt, 0f, 100f);
                if (s.infection > NECROSIS) s.muscle = Math.max(0f, s.muscle - 0.2f * dt);
                if (s.infection > SPREAD) {
                    for (BodyPart neighbour : part.adjacent()) {
                        PartState n = body.part(neighbour);
                        if (!n.amputated && n.infection < 5f && rng.nextFloat() < 0.01f * dt) n.infection = 5f;
                    }
                }
            }
            if (before < NOTICEABLE && s.infection >= NOTICEABLE) body.emit(BodyEvent.Type.INFECTION_NOTICED, part);
            total += s.infection;
        }

        float sepsisBefore = v.sepsis;
        if (total > 100f) {
            float rate = SEPSIS_GROWTH * (total > 200f ? 2f : 1f) * (1f - Math.min(0.7f, antibiotic / 100f * 0.7f));
            v.sepsis = Math.min(100f, v.sepsis + rate * dt);
        } else {
            v.sepsis = Math.max(0f, v.sepsis - SEPSIS_RECOVERY * dt);
        }
        if (sepsisBefore < 10f && v.sepsis >= 10f) body.emit(BodyEvent.Type.SEPSIS, null);

        v.immunity = SimMath.approach(v.immunity, immunityTarget(body, env), 0.05f * dt);
    }

    /** Hunger, exhaustion, filth, sepsis and cold weaken the immune system; being well fed and rested helps. */
    static float immunityTarget(Body body, Environment env) {
        float target = 100f;
        if (env.foodLevel < 6) target -= 25f;
        else if (env.foodLevel >= 18 && body.needs.energy > 60f) target += 10f;
        if (body.needs.energy < 25f) target -= 15f;
        if (body.needs.dirtiness > 80f) target -= 15f;
        if (body.vitals.bodyTemp < 34f) target -= 15f;
        target -= body.vitals.sepsis * 0.4f;
        return SimMath.clamp(target, 0f, 200f);
    }

    /** Fever: infected parts push the temperature setpoint up, sepsis even more. */
    public static float feverDrive(Body body) {
        float drive = 0f;
        for (BodyPart part : BodyPart.VALUES) {
            float infection = body.part(part).infection;
            if (infection > 20f) drive += infection / 100f;
        }
        return Math.min(3.5f, drive * 1.2f + body.vitals.sepsis / 100f * 1.5f);
    }

    public static float totalInfection(Body body) {
        float total = 0f;
        for (BodyPart part : BodyPart.VALUES) total += body.part(part).infection;
        return total;
    }
}
