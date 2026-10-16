package com.fleshbone.sim.system;

import java.util.random.RandomGenerator;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyEvent;
import com.fleshbone.sim.Needs;
import com.fleshbone.sim.Vitals;

/**
 * One chance per life, as in Casualties: Unknown: when the brain is about to give out (below 15 %), the body rolls for
 * a last stand. The odds depend on mood (impossible below −30, certain above 70). It restarts the heart, clears the
 * lungs, warms or cools the body back toward normal, and ignores pain for a minute: enough to fix what is killing it.
 */
public final class LastStand {
    public static final float TRIGGER_BRAIN = 15f;
    public static final float DURATION = 60f;

    private LastStand() {}

    public static void tick(Body body, float dt, RandomGenerator rng) {
        Vitals v = body.vitals;
        if (v.lastStand > 0f) v.lastStand = Math.max(0f, v.lastStand - dt);
        if (v.lastStandUsed || v.brainHealth >= TRIGGER_BRAIN || v.brainHealth <= 0f || v.headDestroyed) return;
        v.lastStandUsed = true;
        if (rng.nextFloat() < chance(body.mind.mood)) pullThrough(body);
    }

    /** −30 mood or less: no chance; 70 or more: certain. */
    public static float chance(float mood) {
        return SimMath.clamp((mood + 30f) / 100f, 0f, 1f);
    }

    static void pullThrough(Body body) {
        Vitals v = body.vitals;
        Needs n = body.needs;
        CirculationSystem.restartHeart(body);
        v.fibrillation = 0f;
        v.spo2 = Math.max(v.spo2, 95f);
        v.respRate = Math.max(v.respRate, 20f);
        v.bodyTemp = SimMath.clamp(v.bodyTemp, 35f, 39f);
        v.shock = 0f;
        v.consciousness = 100f;
        v.lastStand = DURATION;
        n.energy = Math.max(n.energy, 50f);
        n.stamina = Math.max(n.stamina, 80f);
        n.collapsed = 0f;
        n.passedOut = false;
        n.asleep = false;
        n.vomiting = 0f;
        body.emit(BodyEvent.Type.LAST_STAND);
    }
}
