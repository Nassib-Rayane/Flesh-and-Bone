package com.fleshbone.sim.system;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.Chemistry;
import com.fleshbone.sim.Derived;
import com.fleshbone.sim.Environment;
import com.fleshbone.sim.Mind;
import com.fleshbone.sim.Needs;
import com.fleshbone.sim.SimSettings;
import com.fleshbone.sim.Vitals;

/**
 * Mood and trauma, after Casualties: Unknown. Mood = lasting mood (left by events, fading back to 0 at 0.45 points a
 * minute when good, 0.6 when bad) + what the situation adds or takes right now (pain, trauma, blood loss, hunger,
 * thirst, sleepiness, cold, wet, dirt, darkness, nausea, opioids or withdrawal, company, daylight...).
 * A low mood drains energy faster and can make the body refuse to act (5 % at −10, up to 90 % at −100).
 * Trauma builds up while in strong pain and fades slowly.
 */
public final class MindSystem {
    public static final float TRAUMA_DECAY = 1f / 35f;
    public static final float BASE_DECAY_GOOD = 0.45f / 60f;
    public static final float BASE_DECAY_BAD = 0.6f / 60f;
    private static final float[] REFUSAL_MOOD = {-10f, -30f, -50f, -75f, -100f};
    private static final float[] REFUSAL_CHANCE = {0.05f, 0.15f, 0.3f, 0.55f, 0.9f};

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

        if (m.moodBase > 0f) m.moodBase = Math.max(0f, m.moodBase - BASE_DECAY_GOOD * dt);
        else if (m.moodBase < 0f) m.moodBase = Math.min(0f, m.moodBase + BASE_DECAY_BAD * dt);

        float target = settings.mood() ? SimMath.clamp(m.moodBase + situation(body, env), -100f, 100f) : 0f;
        m.mood = SimMath.approach(m.mood, target, 2f * dt);
    }

    /** What the current situation adds to (or takes from) the mood. */
    public static float situation(Body body, Environment env) {
        Mind m = body.mind;
        Vitals v = body.vitals;
        Needs n = body.needs;
        Chemistry c = body.chem;
        Derived d = body.derived;
        float pain = d.totalPain;
        float mood = 0f;
        if (pain > 80f) mood -= 32f;
        else if (pain > 55f) mood -= 22f;
        else if (pain > 30f) mood -= 12f;
        else if (pain > 10f) mood -= 4f;
        mood -= m.trauma * 0.525f;
        mood -= Math.max(0f, 5f - v.bloodVolume) / 0.1f * 0.8f;
        if (d.externalBleed > BleedingSystem.BLEEDING_LPM) mood -= 5f;
        if (n.hunger <= 0f) mood -= 20f;
        else if (n.hunger < HungerSystem.HUNGRY) mood -= 8f;
        else if (n.hunger > HungerSystem.SATIATED) mood += 5f;
        if (n.thirst <= 0f) mood -= 20f;
        else if (n.thirst <= ThirstSystem.PARCHED) mood -= 12f;
        else if (n.thirst <= ThirstSystem.DEHYDRATED) mood -= 4f;
        if (n.energy < 25f) mood -= 12f;
        else if (n.energy < 35f) mood -= 4f;
        if (v.bodyTemp < TemperatureSystem.HYPOTHERMIA) mood -= 10f;
        else if (v.bodyTemp > TemperatureSystem.HYPERTHERMIA) mood -= 8f;
        if (n.wetness > 45f) mood -= 6f;
        if (n.dirtiness > 80f) mood -= 6f;
        if (m.darkness > 60f) mood -= 10f;
        mood -= n.sickness * 0.1f;
        mood += Math.min(40f, ChemistrySystem.relief(c));
        mood -= Math.min(60f, ChemistrySystem.withdrawal(c) * 1.66f);
        if (c.caffeine > 10f) mood += 3f;
        mood -= Math.min(10f, ChemistrySystem.caffeineWithdrawal(c) * 0.5f);
        if (c.epinephrine > 30f) mood -= Math.min(10f, (c.epinephrine - 30f) * 0.2f);
        if (c.anaphylaxis > 10f) mood -= 15f;
        if (c.foodPoisoning > 10f) mood -= Math.min(15f, c.foodPoisoning * 0.3f);
        for (BodyPart part : BodyPart.VALUES) {
            if (body.part(part).amputated) {
                mood -= 10f;
                break;
            }
        }
        if (body.part(BodyPart.HEAD).skin < 10f) mood -= 8f;
        if (body.senses.leftEyeLost || body.senses.rightEyeLost) mood -= 8f;
        if (Math.abs(d.weightClass.level) >= 3) mood -= 6f;
        if (env != null) {
            if (env.comfort) mood += 10f;
            if (env.skyVisible && env.light >= 12) mood += 4f;
        }
        return mood;
    }

    /** Chance that a miserable body refuses to do something for itself (treat a wound, eat). */
    public static float refusalChance(float mood) {
        if (mood > REFUSAL_MOOD[0]) return 0f;
        for (int i = 1; i < REFUSAL_MOOD.length; i++) {
            if (mood >= REFUSAL_MOOD[i]) {
                float t = (REFUSAL_MOOD[i - 1] - mood) / (REFUSAL_MOOD[i - 1] - REFUSAL_MOOD[i]);
                return REFUSAL_CHANCE[i - 1] + t * (REFUSAL_CHANCE[i] - REFUSAL_CHANCE[i - 1]);
            }
        }
        return REFUSAL_CHANCE[REFUSAL_CHANCE.length - 1];
    }
}
