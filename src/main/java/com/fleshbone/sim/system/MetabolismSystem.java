package com.fleshbone.sim.system;

import java.util.random.RandomGenerator;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyEvent;
import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.Chemistry;
import com.fleshbone.sim.DeathCause;
import com.fleshbone.sim.Environment;
import com.fleshbone.sim.Needs;
import com.fleshbone.sim.SimSettings;
import com.fleshbone.sim.Vitals;
import com.fleshbone.sim.injury.InjuryApplier;

/** Thirst, sleepiness (and passing out), stamina, nausea and vomiting. */
public final class MetabolismSystem {
    /** Thirst lost per second at rest: a full bar lasts about 40 minutes. */
    public static final float THIRST_RATE = 100f / 2400f;
    /** Energy lost per second awake: about an hour of activity. */
    public static final float ENERGY_RATE = 100f / 3600f;
    public static final float SLEEP_RECOVERY = 1.5f;
    public static final float PASSED_OUT_RECOVERY = 0.6f;
    /** Energy needed to come round after passing out from exhaustion. */
    public static final float WAKE_ENERGY = 20f;
    public static final float VOMIT_THRESHOLD = 30f;

    private MetabolismSystem() {}

    public static void tick(Body body, Environment env, SimSettings settings, float dt, RandomGenerator rng) {
        Needs n = body.needs;
        Vitals v = body.vitals;
        Chemistry c = body.chem;

        if (settings.thirst()) {
            float rate = THIRST_RATE;
            if (env.sprinting) rate *= 2f;
            else if (env.moveSpeed > 1f) rate *= 1.2f;
            if (v.bodyTemp > 37.5f) rate *= 1f + (v.bodyTemp - 37.5f) * 0.5f;
            if (env.ambientTemp + env.heatSource > 30f) rate *= 1.3f;
            if (env.sleeping) rate *= 0.5f;
            if (n.thirst > 100f) rate *= 2f;
            n.thirst = Math.max(0f, n.thirst - rate * dt);
            if (n.thirst <= 0f) {
                // Organs shut down without water
                v.brainHealth = Math.max(0f, v.brainHealth - 0.04f * dt);
                v.pendingCause = DeathCause.DEHYDRATION;
            }
        }

        if (settings.energy()) {
            if (env.sleeping) {
                n.energy = Math.min(100f, n.energy + SLEEP_RECOVERY * dt);
            } else if (n.passedOut) {
                n.energy = Math.min(100f, n.energy + PASSED_OUT_RECOVERY * dt);
            } else {
                float drain = ENERGY_RATE * (env.sprinting ? 1.5f : 1f) * (1f - Math.min(0.6f, c.caffeine / 100f))
                        * (body.derived.totalPain > 55f ? 1.3f : 1f) * (body.mind.mood < -50f ? 1.3f : 1f);
                n.energy = Math.max(0f, n.energy - drain * dt);
            }
            if (!n.passedOut && n.energy <= 0f) {
                n.passedOut = true;
                body.emit(BodyEvent.Type.PASSED_OUT_TIRED, null);
            } else if (n.passedOut && n.energy >= WAKE_ENERGY) {
                n.passedOut = false;
            }
        } else {
            n.passedOut = false;
        }

        if (settings.stamina()) {
            float use = (env.sprinting ? 2.5f : 0f) + (env.swimming ? 2f : 0f);
            // No recovery while sprinting or swimming
            float regen = env.sprinting || env.swimming ? 0f : env.moveSpeed < 1f ? 6f : 3f;
            if (v.bloodVolume < 4f) regen *= 0.5f;
            if (v.spo2 < 90f) regen *= 0.5f;
            if (n.thirst < 20f) regen *= 0.5f;
            if (c.caffeine > 10f) regen *= 1.2f;
            float burden = 1f + env.armorWeight;
            n.stamina = SimMath.clamp(n.stamina + (regen / burden - use * burden) * dt, 0f, 100f);
        }

        // Nausea
        float sick = -0.05f;
        if (c.opiate > 30f) sick += 0.04f;
        if (v.bodyTemp > 39f) sick += 0.03f;
        if (body.part(BodyPart.HEAD).muscle < InjuryApplier.CONCUSSION_MUSCLE) sick += 0.05f;
        if (v.brainHealth < 80f) sick += 0.02f;
        float withdrawal = c.opiateDependence - c.opiate;
        if (withdrawal > 5f) sick += withdrawal * 0.0015f;
        n.sickness = SimMath.clamp(n.sickness + sick * dt, 0f, 100f);

        n.vomitCooldown = Math.max(0f, n.vomitCooldown - dt);
        if (n.sickness > VOMIT_THRESHOLD && n.vomitCooldown <= 0f && !body.isUnconscious()
                && rng.nextFloat() < 0.02f * (n.sickness - VOMIT_THRESHOLD) / 70f * dt) {
            vomit(body);
        }
    }

    /** Throwing up: relief from nausea, but water (and food, handled by the game) is lost. */
    public static void vomit(Body body) {
        Needs n = body.needs;
        n.sickness = Math.max(0f, n.sickness - 25f);
        n.thirst = Math.max(0f, n.thirst - 10f);
        n.vomitCooldown = 30f;
        body.vitals.consciousness = Math.max(0f, body.vitals.consciousness - 15f);
        body.emit(BodyEvent.Type.VOMITED, null);
    }

    /** Physical effort reported by the game: jumps, swings, mining. */
    public static void exert(Body body, float amount) {
        body.needs.stamina = Math.max(0f, body.needs.stamina - amount);
    }
}
