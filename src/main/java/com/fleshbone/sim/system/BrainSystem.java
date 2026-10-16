package com.fleshbone.sim.system;

import java.util.random.RandomGenerator;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyEvent;
import com.fleshbone.sim.DeathCheck;
import com.fleshbone.sim.Environment;
import com.fleshbone.sim.Needs;
import com.fleshbone.sim.Vitals;

/**
 * Brain health, after Casualties: Unknown: lost to low oxygen (0.1 %/min per point of SpO2 below 80, much faster
 * near zero), heat above 41 °C (0.5 %/s above 42), water intoxication, severe dehydration and an unassisted cardiac
 * arrest. It regrows extremely slowly. Below 95 %, the damaged brain misfires every now and then.
 */
public final class BrainSystem {
    public static final float HYPOXIA_SPO2 = 80f;
    public static final float SEVERE_HYPOXIA_SPO2 = 30f;
    public static final float HEATSTROKE_TEMP = 41f;
    public static final float BRAIN_COOKING_TEMP = 42f;
    public static final float NATURAL_REGEN = 0.003f;
    /** Below this brain health, episodes (hallucinations, mirrored controls...) start. */
    public static final float EPISODES = 95f;
    public static final float EPISODE_INTERVAL = 30f;
    /** Speech slurs below this. */
    public static final float SLURRED = 82.5f;
    public static final float BLURRED = 80f;
    public static final float DISTORTED = 60f;
    /** No speech at all below this. */
    public static final float MUTE = 50f;

    private static final BodyEvent.Type[] EPISODE_TYPES = {
            BodyEvent.Type.BRAIN_HALLUCINATION, BodyEvent.Type.BRAIN_MIRRORED, BodyEvent.Type.BRAIN_DROP,
            BodyEvent.Type.BRAIN_SHAKE, BodyEvent.Type.BRAIN_FLASH, BodyEvent.Type.VOMITED};

    private BrainSystem() {}

    public static void tick(Body body, Environment env, float dt, RandomGenerator rng) {
        Vitals v = body.vitals;
        Needs n = body.needs;
        float damage = 0f;
        if (v.spo2 < HYPOXIA_SPO2) damage += (HYPOXIA_SPO2 - v.spo2) / 600f;
        if (v.spo2 < SEVERE_HYPOXIA_SPO2) damage += (SEVERE_HYPOXIA_SPO2 - v.spo2) * 0.02f;
        if (v.bodyTemp > BRAIN_COOKING_TEMP) damage += 0.5f;
        else if (v.bodyTemp > HEATSTROKE_TEMP) damage += 0.05f;
        if (n.thirst > ThirstSystem.WATER_INTOXICATION) damage += 0.05f;
        if (n.thirst <= Needs.MIN_THIRST) damage += 0.1f;
        else if (n.thirst <= 0f) damage += 0.02f;
        if (v.cardiacArrest && v.arrestTime > 10f && v.cprPerfusion < 0.3f) damage += 0.4f;
        if (v.lastStand > 0f) damage = 0f;

        if (damage > 0f) {
            v.brainHealth = Math.max(0f, v.brainHealth - damage * dt);
            // While the heart beats, the latest process is the likely cause; an arrest keeps what stopped the heart
            if (!v.cardiacArrest) v.pendingCause = DeathCheck.classify(body);
        } else if (v.spo2 > 90f && !v.cardiacArrest && v.brainHealth > 0f) {
            // Brain death is irreversible: no regrowth from zero
            v.brainHealth = Math.min(100f, v.brainHealth + NATURAL_REGEN * dt);
        }
        episodes(body, dt, rng);
    }

    /** Every 30 s below 95 % brain health, a chance (higher the worse the damage) of a misfire. */
    static void episodes(Body body, float dt, RandomGenerator rng) {
        Vitals v = body.vitals;
        if (v.brainHealth >= EPISODES || body.isUnconscious() || rng == null) {
            v.brainEventTimer = 0f;
            return;
        }
        v.brainEventTimer += dt;
        if (v.brainEventTimer < EPISODE_INTERVAL) return;
        v.brainEventTimer = 0f;
        float chance = 0.3f + 0.7f * (EPISODES - v.brainHealth) / EPISODES;
        if (rng.nextFloat() >= chance) return;
        BodyEvent.Type type = EPISODE_TYPES[rng.nextInt(EPISODE_TYPES.length)];
        if (type == BodyEvent.Type.VOMITED) SicknessSystem.startVomiting(body);
        else body.emit(type);
    }
}
