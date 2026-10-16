package com.fleshbone.sim.system;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyEvent;
import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.Environment;
import com.fleshbone.sim.Needs;
import com.fleshbone.sim.PartState;
import com.fleshbone.sim.SimSettings;
import com.fleshbone.sim.injury.InjuryApplier;

/**
 * Natural regeneration of skin and muscle, bone and joint healing, and the timers of devices (tourniquet, ice pack,
 * disinfection). Healing is built from food: it costs hunger, slows when hungry or dehydrated and stops when starving.
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
    /** Hunger spent per point of skin and of muscle regrown, and to knit a whole bone. */
    private static final float HUNGER_PER_SKIN = 0.06f;
    private static final float HUNGER_PER_MUSCLE = 0.1f;
    private static final float HUNGER_PER_FRACTURE = 25f;

    private HealingSystem() {}

    public static void tick(Body body, Environment env, SimSettings settings, float dt) {
        Needs n = body.needs;
        boolean fed = settings.hunger();
        float nutrition = (fed ? nutrition(n) : 1f) * hydration(body);
        float sleep = body.isAsleep(env) ? SLEEP_BONUS : 1f;
        float speed = body.vitals.cardiacArrest ? 0f : settings.healingSpeed() * nutrition * sleep;
        float cost = 0f;

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
                cost += gain * HUNGER_PER_SKIN;
            }
            if (s.sutured && s.skin >= 99f) s.sutured = false;

            float maxMuscle = s.maxMuscle();
            if (s.muscle < maxMuscle && mult > 0f && !(fed && n.hunger <= 0f)) {
                float rate = (s.muscle < 10f && part != BodyPart.HEAD ? MUSCLE_REGEN_LOW : MUSCLE_REGEN) * mult;
                if (s.chilled > 0f) rate *= 3f;
                float gain = Math.min(maxMuscle - s.muscle, rate * dt);
                s.muscle += gain;
                cost += gain * HUNGER_PER_MUSCLE;
            }

            if (s.isFractured() && speed > 0f) {
                float rate = (s.splinted ? 1f + (SPLINT_FRACTURE_BONUS - 1f) * s.splintQuality : 1f) * nutrition * sleep / settings.fractureHealSeconds();
                float step = Math.min(s.fracture, rate * dt);
                s.fracture -= step;
                cost += step * HUNGER_PER_FRACTURE;
                if (s.fracture <= 0f) {
                    s.fracture = 0f;
                    body.mind.addMood(5f);
                    body.emit(BodyEvent.Type.FRACTURE_HEALED, part);
                }
            }
            if (s.isDislocated() && speed > 0f) {
                float rate = (s.splinted ? 1f + (SPLINT_DISLOCATION_BONUS - 1f) * s.splintQuality : 1f) * nutrition * sleep / settings.dislocationHealSeconds();
                s.dislocation = Math.max(0f, s.dislocation - rate * dt);
                if (s.dislocation <= 0f) {
                    body.mind.addMood(3f);
                    body.emit(BodyEvent.Type.DISLOCATION_HEALED, part);
                }
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
        if (fed && cost > 0f) n.hunger = Math.max(com.fleshbone.sim.Needs.MIN_HUNGER, n.hunger - cost);
    }

    /** Regeneration speed allowed by food: full from 30 hunger, a third below, none when starving. */
    public static float nutrition(Needs needs) {
        if (needs.hunger >= 30f) return 1f;
        return needs.hunger > 0f ? 0.35f : 0f;
    }

    /** Severe dehydration slows healing like hunger does. */
    static float hydration(Body body) {
        float thirst = body.needs.thirst;
        if (thirst >= 20f) return 1f;
        return thirst > 0f ? 0.35f : 0f;
    }
}
