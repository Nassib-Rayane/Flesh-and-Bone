package com.fleshbone.sim.system;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.Chemistry;
import com.fleshbone.sim.Environment;
import com.fleshbone.sim.Mind;
import com.fleshbone.sim.Needs;
import com.fleshbone.sim.SimSettings;
import com.fleshbone.sim.Vitals;

/**
 * Trauma builds up while in strong pain and fades slowly. Mood drifts toward a target made of everything that makes
 * life miserable (pain, trauma, blood loss, hunger, thirst, fatigue, cold, wet, dirt, darkness, withdrawal) or
 * bearable (company, daylight, a full stomach, a recent good event).
 */
public final class MindSystem {
    public static final float TRAUMA_DECAY = 1f / 35f;

    private MindSystem() {}

    public static void tick(Body body, Environment env, SimSettings settings, float dt) {
        Mind m = body.mind;
        float pain = body.derived.totalPain;
        if (pain > 50f && !body.isUnconscious()) {
            m.trauma = Math.min(100f, m.trauma + (pain > 80f ? 0.272f : pain > 55f ? 0.187f : 0.17f) * dt);
        }
        m.trauma = Math.max(0f, m.trauma - TRAUMA_DECAY * dt);

        if (env.light < 4 && !env.skyVisible) m.darkness += dt;
        else m.darkness = Math.max(0f, m.darkness - 3f * dt);

        m.moodBonus = SimMath.approach(m.moodBonus, 0f, 0.02f * dt);
        if (!settings.mood()) {
            m.mood = SimMath.approach(m.mood, 0f, 0.08f * dt);
            return;
        }
        m.mood = SimMath.approach(m.mood, target(body, env), 0.08f * dt);
    }

    static float target(Body body, Environment env) {
        Mind m = body.mind;
        Vitals v = body.vitals;
        Needs n = body.needs;
        Chemistry c = body.chem;
        float pain = body.derived.totalPain;
        float target = 0f;
        if (pain > 80f) target -= 32f;
        else if (pain > 55f) target -= 22f;
        else if (pain > 30f) target -= 12f;
        else if (pain > 10f) target -= 4f;
        target -= m.trauma * 0.525f;
        target -= Math.max(0f, 5f - v.bloodVolume) / 0.1f * 0.8f;
        target -= env.foodLevel < 6 ? 15f : env.foodLevel < 10 ? 5f : 0f;
        target -= n.thirst < 35f ? 15f : n.thirst < 55f ? 5f : 0f;
        target -= n.energy < 25f ? 12f : n.energy < 35f ? 4f : 0f;
        target -= v.bodyTemp < 35f ? 10f : v.bodyTemp > 38.5f ? 8f : 0f;
        if (n.wetness > 45f) target -= 6f;
        if (n.dirtiness > 80f) target -= 6f;
        if (m.darkness > 60f) target -= 10f;
        float withdrawal = c.opiateDependence - c.opiate;
        if (withdrawal > 5f) target -= Math.min(25f, withdrawal);
        for (BodyPart part : BodyPart.VALUES) {
            if (body.part(part).amputated) {
                target -= 10f;
                break;
            }
        }
        if (env.comfort) target += 10f;
        if (env.skyVisible && env.light >= 12) target += 4f;
        if (env.foodLevel >= 18) target += 4f;
        target += Math.min(15f, c.opiate * 0.2f);
        if (c.caffeine > 10f) target += 3f;
        target += m.moodBonus;
        return SimMath.clamp(target, -100f, 100f);
    }
}
