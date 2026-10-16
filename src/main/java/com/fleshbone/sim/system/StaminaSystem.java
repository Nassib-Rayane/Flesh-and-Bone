package com.fleshbone.sim.system;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyEvent;
import com.fleshbone.sim.Environment;
import com.fleshbone.sim.Needs;
import com.fleshbone.sim.SimSettings;
import com.fleshbone.sim.Skills;

/**
 * Stamina, after Casualties: Unknown: spent by every effort (×(1 + overload) when carrying too much), recovered at
 * 1.4 %/s standing still, never above the blood oxygen level (with a floor of 70). Muscle strength follows a cubic
 * curve of stamina, so tiredness only bites when deep. Running out of stamina mid-effort makes the body collapse.
 */
public final class StaminaSystem {
    public static final float RECOVERY = 1.4f;
    public static final float WALK_COST = 0.1f;
    public static final float SPRINT_COST = 1f;
    public static final float SWIM_COST = 0.8f;
    public static final float WADE_COST = 0.3f;
    public static final float PUSHUPS_COST = 1.2f;
    public static final float SQUATS_COST = 0.9f;
    public static final float PLANK_COST = 1.05f;
    public static final float JUMP_COST = 1f;
    public static final float PUNCH_COST = 1f;
    public static final float SWING_COST = 3f;
    public static final float MINE_COST = 0.5f;
    /** Stamina below which sprinting stops. */
    public static final float SPRINT_MIN = 10f;
    public static final float COLLAPSE_SECONDS = 4f;
    /** The oxygen cap never goes below this. */
    public static final float MIN_CAP = 70f;

    private StaminaSystem() {}

    public static void tick(Body body, Environment env, SimSettings settings, float dt) {
        Needs n = body.needs;
        if (n.collapsed > 0f) n.collapsed = Math.max(0f, n.collapsed - dt);
        float cap = body.derived.staminaCap;
        if (!settings.stamina()) {
            n.stamina = cap;
            return;
        }
        float over = body.derived.overload;
        float cost = exertion(env) * (1f + over);
        float regen = 0f;
        if (cost <= WALK_COST * 2f) {
            regen = RECOVERY * (cost > 0f ? 0.65f : 1f) * recoveryFactor(body, env) / (1f + over);
        }
        float before = n.stamina;
        if (n.stamina > cap) {
            // Oxygen debt: the cap pulls stamina down quickly
            n.stamina = Math.max(cap, n.stamina - (5f + cost) * dt);
        } else {
            n.stamina = SimMath.clamp(n.stamina + (regen - cost) * dt, 0f, cap);
        }
        if (before > 0f && n.stamina <= 0f && cost > 0f) collapse(body);
    }

    /** Stamina spent per second by what the body is doing. */
    public static float exertion(Environment env) {
        switch (env.exercise) {
            case PUSHUPS: return PUSHUPS_COST;
            case SQUATS: return SQUATS_COST;
            case PLANK: return PLANK_COST;
            default: break;
        }
        if (env.swimming) return SWIM_COST;
        if (env.sprinting) return SPRINT_COST;
        if (env.moveSpeed > 0.5f) return env.inWater ? WADE_COST : WALK_COST;
        return 0f;
    }

    /** Resilience (±2 % per level from 10), sleepiness, hunger, thirst and blood loss slow the recovery. */
    static float recoveryFactor(Body body, Environment env) {
        Needs n = body.needs;
        float f = 1f + 0.02f * (body.skills.resilience() - Skills.START_LEVEL);
        if (n.energy < 10f) f *= 0.35f;
        else if (n.energy < 25f) f *= 0.6f;
        if (n.hunger <= 0f) f *= 0.5f;
        if (n.thirst <= 0f) f *= 0.5f;
        f *= SimMath.clamp(body.vitals.bloodVolume / 5f, 0.3f, 1f);
        if (n.vomiting > 0f) f = 0f;
        if (body.isAsleep(env)) f *= 1.2f;
        return Math.max(0f, f);
    }

    /** A burst of effort (jump, swing, block broken), heavier when overloaded. */
    public static void exert(Body body, float amount) {
        Needs n = body.needs;
        if (n.stamina <= 0f) return;
        n.stamina = Math.max(0f, n.stamina - amount * (1f + body.derived.overload));
        if (n.stamina <= 0f) collapse(body);
    }

    static void collapse(Body body) {
        if (body.needs.collapsed > 0f) return;
        body.needs.collapsed = COLLAPSE_SECONDS;
        body.emit(BodyEvent.Type.COLLAPSED);
    }

    /** Casualties: Unknown's stamina strength: 100 → 100 %, 70 → 97.6 %, 35 → 73 %, 15 → 39 %, 0 → 0. */
    public static float strength(float stamina) {
        float missing = 1f - SimMath.clamp(stamina, 0f, 100f) / 100f;
        return 1f - missing * missing * missing;
    }

    /** Stamina cannot exceed the blood oxygen level, with a floor of 70. */
    public static float cap(Body body) {
        return SimMath.clamp(Math.max(MIN_CAP, body.vitals.spo2), 0f, 100f);
    }
}
