package com.fleshbone.sim.system;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyEvent;
import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.Environment;
import com.fleshbone.sim.PartState;
import com.fleshbone.sim.SimSettings;
import com.fleshbone.sim.injury.InjuryApplier;

/**
 * Natural regeneration of skin and muscle, bone and joint healing, and the timers of devices (tourniquet, ice pack,
 * disinfection). Healing costs food: it adds vanilla exhaustion and stops when starving.
 */
public final class HealingSystem {
    public static final float SKIN_REGEN = 0.0694f;
    public static final float SKIN_REGEN_LOW = 0.0137f;
    public static final float MUSCLE_REGEN = 0.10126f;
    public static final float MUSCLE_REGEN_LOW = 0.0172f;
    public static final float SLEEP_BONUS = 1.4f;
    public static final float SPLINT_FRACTURE_BONUS = 2.5f;
    public static final float SPLINT_DISLOCATION_BONUS = 2f;
    /** Seconds before a tourniquet starts killing the muscle below it. */
    public static final float TOURNIQUET_SAFE_TIME = 180f;
    public static final float TOURNIQUET_WARNING_TIME = 120f;
    private static final float EXHAUSTION_PER_SKIN = 0.05f;
    private static final float EXHAUSTION_PER_MUSCLE = 0.08f;
    /** Total exhaustion to knit a bone (5 food points). */
    private static final float EXHAUSTION_PER_FRACTURE = 20f;

    private HealingSystem() {}

    public static void tick(Body body, Environment env, SimSettings settings, float dt) {
        float nutrition = env.nutrition() * hydration(body);
        float sleep = env.sleeping ? SLEEP_BONUS : 1f;
        float speed = body.vitals.cardiacArrest ? 0f : settings.healingSpeed() * nutrition * sleep;

        for (BodyPart part : BodyPart.VALUES) {
            PartState s = body.part(part);
            if (s.amputated) continue;
            boolean concussed = part == BodyPart.HEAD && s.muscle < InjuryApplier.CONCUSSION_MUSCLE;
            boolean blocked = (s.shrapnel > 0 && !concussed) || (s.infection > 0f && part != BodyPart.HEAD);
            float mult = blocked ? 0f : speed;
            if (body.isTourniquetted(part)) mult *= 0.1f;

            if (s.skin < 100f && mult > 0f) {
                float rate = (s.skin < 10f ? SKIN_REGEN_LOW : SKIN_REGEN) * mult;
                if (s.sutured) rate *= 2f;
                if (s.isBandaged() && s.bandageSoak < 0.8f) rate *= 1.25f;
                float gain = Math.min(100f - s.skin, rate * dt);
                s.skin += gain;
                body.exhaustionDebt += gain * EXHAUSTION_PER_SKIN;
            }
            if (s.sutured && s.skin >= 99f) s.sutured = false;

            float maxMuscle = s.maxMuscle();
            if (s.muscle < maxMuscle && mult > 0f) {
                float rate = (s.muscle < 10f && part != BodyPart.HEAD ? MUSCLE_REGEN_LOW : MUSCLE_REGEN) * mult;
                if (s.chilled > 0f) rate *= 3f;
                float gain = Math.min(maxMuscle - s.muscle, rate * dt);
                s.muscle += gain;
                body.exhaustionDebt += gain * EXHAUSTION_PER_MUSCLE;
            }

            if (s.isFractured() && speed > 0f) {
                float rate = (s.splinted ? SPLINT_FRACTURE_BONUS : 1f) * nutrition * sleep / settings.fractureHealSeconds();
                float step = Math.min(s.fracture, rate * dt);
                s.fracture -= step;
                body.exhaustionDebt += step * EXHAUSTION_PER_FRACTURE;
                if (s.fracture <= 0f) {
                    s.fracture = 0f;
                    body.mind.moodBonus += 5f;
                    body.emit(BodyEvent.Type.FRACTURE_HEALED, part);
                }
            }
            if (s.isDislocated() && speed > 0f) {
                float rate = (s.splinted ? SPLINT_DISLOCATION_BONUS : 1f) * nutrition * sleep / settings.dislocationHealSeconds();
                s.dislocation = Math.max(0f, s.dislocation - rate * dt);
                if (s.dislocation <= 0f) body.emit(BodyEvent.Type.DISLOCATION_HEALED, part);
            }
            if (s.splinted && !s.isFractured() && !s.isDislocated()) s.splinted = false;

            if (s.tourniquet) {
                float before = s.tourniquetTime;
                s.tourniquetTime += dt;
                if (before < TOURNIQUET_WARNING_TIME && s.tourniquetTime >= TOURNIQUET_WARNING_TIME) {
                    body.emit(BodyEvent.Type.LIMB_NUMB, part);
                }
                if (s.tourniquetTime > TOURNIQUET_SAFE_TIME) {
                    for (BodyPart below : part.distal()) {
                        PartState d = body.part(below);
                        d.muscle = Math.max(0f, d.muscle - 0.2f * dt);
                    }
                }
            }
            if (s.chilled > 0f) s.chilled = Math.max(0f, s.chilled - dt);
            if (s.disinfected > 0f) s.disinfected = Math.max(0f, s.disinfected - dt);
            if (!s.hasOpenWound()) s.contamination = Math.max(0f, s.contamination - 0.05f * dt);
        }
    }

    /** Severe dehydration slows healing like hunger does. */
    static float hydration(Body body) {
        float thirst = body.needs.thirst;
        if (thirst >= 20f) return 1f;
        return thirst > 0f ? 0.35f : 0f;
    }
}
