package com.fleshbone.sim.system;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyEvent;
import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.Environment;
import com.fleshbone.sim.Skills;
import com.fleshbone.sim.Skills.Stat;

/**
 * Experience from what the body goes through, after Casualties: Unknown: enduring pain and squats toughen
 * (resilience), push-ups strengthen, planks do both, carrying too much trains both. The game adds the rest (blows
 * landed, kills, falls survived, first crafts, trades, new depths, treatments).
 */
public final class SkillSystem {
    /** XP per second of each workout. */
    public static final float PUSHUPS_STRENGTH = 0.5f;
    public static final float SQUATS_RESILIENCE = 0.4f;
    public static final float PLANK_EACH = 0.25f;
    /** Resilience XP per second per point of average part pain. */
    public static final float PAIN_RESILIENCE = 0.0125f;
    /** XP per second for each stat at twice the carrying capacity, on the move. */
    public static final float OVERLOAD_XP = 0.05f;

    private SkillSystem() {}

    public static void tick(Body body, Environment env, float dt) {
        float pain = 0f;
        for (BodyPart part : BodyPart.VALUES) pain += body.derived.partPain[part.ordinal()];
        pain /= BodyPart.COUNT;
        if (pain > 0.5f) gain(body, Stat.RESILIENCE, pain * PAIN_RESILIENCE * dt);
        switch (env.exercise) {
            case PUSHUPS -> gain(body, Stat.STRENGTH, PUSHUPS_STRENGTH * dt);
            case SQUATS -> gain(body, Stat.RESILIENCE, SQUATS_RESILIENCE * dt);
            case PLANK -> {
                gain(body, Stat.STRENGTH, PLANK_EACH * dt);
                gain(body, Stat.RESILIENCE, PLANK_EACH * dt);
            }
            case NONE -> { }
        }
        float over = body.derived.overload;
        if (over > 0f && env.moveSpeed > 0.5f) {
            gain(body, Stat.STRENGTH, OVERLOAD_XP * over * dt);
            gain(body, Stat.RESILIENCE, OVERLOAD_XP * over * dt);
        }
    }

    /** Adds experience; announces level-ups. */
    public static void gain(Body body, Stat stat, float xp) {
        if (body.skills.addXp(stat, xp) > 0) {
            body.emit(switch (stat) {
                case STRENGTH -> BodyEvent.Type.STRENGTH_UP;
                case RESILIENCE -> BodyEvent.Type.RESILIENCE_UP;
                case INTELLIGENCE -> BodyEvent.Type.INTELLIGENCE_UP;
            });
        }
    }

    /** Melee damage: ±3.34 % per strength level from 10. */
    public static float attackMultiplier(Skills skills) {
        return Math.max(0.3f, 1f + 0.0334f * (skills.strength() - Skills.START_LEVEL));
    }

    /** Hand speed in minigames: ±5 % per strength level from 10. */
    public static float handSpeed(Skills skills) {
        return SimMath.clamp(1f + 0.05f * (skills.strength() - Skills.START_LEVEL), 0.5f, 1.6f);
    }

    /** Hand tremor in minigames: −6 % per resilience level from 10 (none at 20 and above... almost). */
    public static float shakiness(Skills skills) {
        return SimMath.clamp(1f - 0.06f * (skills.resilience() - Skills.START_LEVEL), 0.3f, 1.6f);
    }

    /** Treatment quality bonus: +3 % per intelligence level above 10 (or a malus below). */
    public static float treatmentBonus(Skills skills) {
        return 0.03f * (skills.intelligence() - Skills.START_LEVEL);
    }
}
