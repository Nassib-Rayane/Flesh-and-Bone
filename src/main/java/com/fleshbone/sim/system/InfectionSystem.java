package com.fleshbone.sim.system;

import java.util.random.RandomGenerator;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyEvent;
import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.Environment;
import com.fleshbone.sim.PartState;
import com.fleshbone.sim.SimSettings;
import com.fleshbone.sim.Vitals;

/**
 * Wound infection, its spread and sepsis.
 * <ul>
 *   <li>Open wounds collect germs (the injury itself, dirt on the body, rags, soaked dressings) and may get infected;
 *       weak immunity makes it likelier.</li>
 *   <li>An infection grows or shrinks by Casualties: Unknown's immunity table: +18 %/min at 0 % immunity,
 *       +7.2 %/min at 100 %, about zero at 196 %, −5.76 %/min at 200 %. A disinfected wound heals at about
 *       5 %/min. Antibiotics act through immunity.</li>
 *   <li>Stages: &gt;0 stops regeneration, &gt;40 painful, &gt;60 severe (amputation possible), &gt;75 necrosis,
 *       &gt;90 spreads to adjacent parts.</li>
 *   <li>Total infection above 100 feeds sepsis, which dilates the vessels until the pressure collapses.</li>
 * </ul>
 */
public final class InfectionSystem {
    /** Visible in the health panel and moodles above this. */
    public static final float NOTICEABLE = 25f;
    public static final float AMPUTATION_THRESHOLD = 60f;
    public static final float NECROSIS = 75f;
    public static final float SPREAD = 90f;
    /** Infection seeding hazard per second for a fully contaminated, skinless wound at average immunity. */
    public static final float SEED_RATE = 0.04f;
    public static final float SEPSIS_GROWTH = 0.0277f;
    public static final float SEPSIS_RECOVERY = 0.07f;
    /** Highest temperature infections alone can push the body to. */
    public static final float FEVER_CEILING = 40.5f;

    private InfectionSystem() {}

    public static void tick(Body body, Environment env, SimSettings settings, float dt, RandomGenerator rng) {
        Vitals v = body.vitals;
        float dirt = body.needs.dirtiness;
        float immunity = body.derived.immunity;
        float speed = settings.infectionSpeed();
        // Antibiotics kill bacteria directly as well as boosting immunity: −4 %/min at a full course
        float growth = (ImmunitySystem.infectionRate(immunity) - body.chem.antibiotic * 0.06f) / 60f * speed;
        float healing = ImmunitySystem.disinfectedRate(immunity) / 60f * speed;
        float seedFactor = SimMath.clamp(2f - immunity / 100f, 0.2f, 2.5f);
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
                    float chance = s.contamination / 100f * (1f - s.skin / 100f) * SEED_RATE * seedFactor * speed
                            * (dirt > 50f ? 1.5f : 1f);
                    if (rng.nextFloat() < chance * dt) s.infection = 1f;
                }
            } else {
                float rate = s.disinfected > 0f ? Math.min(healing, growth) : growth;
                s.infection = SimMath.clamp(s.infection + rate * dt, 0f, 100f);
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
            float rate = SEPSIS_GROWTH * (total > 200f ? 2f : 1f) * (immunity >= 150f ? 0.3f : 1f);
            v.sepsis = Math.min(100f, v.sepsis + rate * dt);
        } else {
            v.sepsis = Math.max(0f, v.sepsis - SEPSIS_RECOVERY * dt);
        }
        if (sepsisBefore < 10f && v.sepsis >= 10f) body.emit(BodyEvent.Type.SEPSIS, null);
    }

    /** Fever: infected parts push the temperature setpoint up, sepsis even more (never past 40.5 °C). */
    public static float feverDrive(Body body) {
        float drive = 0f;
        for (BodyPart part : BodyPart.VALUES) {
            float infection = body.part(part).infection;
            if (infection > 20f) drive += infection / 100f;
        }
        return Math.min(FEVER_CEILING - TemperatureSystem.NORMAL,
                drive * 1.2f + body.vitals.sepsis / 100f * 1.5f + body.chem.foodPoisoning / 100f * 4f);
    }

    public static float totalInfection(Body body) {
        float total = 0f;
        for (BodyPart part : BodyPart.VALUES) total += body.part(part).infection;
        return total;
    }
}
