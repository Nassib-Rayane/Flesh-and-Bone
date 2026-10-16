package com.fleshbone.sim.system;

import java.util.Locale;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyEvent;
import com.fleshbone.sim.Chemistry;
import com.fleshbone.sim.Environment;
import com.fleshbone.sim.Needs;
import com.fleshbone.sim.SimSettings;

/**
 * Sleepiness and sleep, after Casualties: Unknown.
 * <ul>
 *   <li>Awake, energy drops 0.07 %/s (a full bar lasts about 24 minutes), up to ×3.5 when out of stamina, ×3 when
 *       very nauseous, ×2 when miserable, ×0.55 on caffeine; strong opioids sedate.</li>
 *   <li>The body can fall asleep anywhere once tired (energy ≤ 35), unless in pain (&gt;31) or too sick (&gt;80).
 *       It recovers 0.28 to 0.5 %/s depending on how good the spot is, and wakes up when rested or disturbed by pain,
 *       cold, nausea, vomiting, water, withdrawal, despair, hunger or thirst.</li>
 *   <li>A bad sleep leaves the mind groggy (consciousness capped) for up to 2.5 minutes.</li>
 *   <li>At 0 energy the body passes out until 20 % comes back.</li>
 * </ul>
 */
public final class SleepSystem {
    public static final float DRAIN = 0.07f;
    /** Recovery per second by sleep quality: none, bad, mediocre, okay, good. */
    private static final float[] RECOVERY = {0f, 0.28f, 0.34f, 0.4f, 0.5f};
    public static final float TIRED = 35f;
    public static final float MAX_SICKNESS = 80f;
    public static final float MAX_PAIN = PainSystem.NO_SLEEP;
    /** Energy needed to come round after passing out from exhaustion. */
    public static final float WAKE_ENERGY = 20f;
    public static final float RESTED = 99.5f;
    public static final int BAD = 1;
    public static final int MEDIOCRE = 2;
    public static final int OKAY = 3;
    public static final int GOOD = 4;

    public enum Refusal {
        NOT_TIRED,
        IN_PAIN,
        TOO_SICK,
        RESTLESS,
        BAD_SPOT;

        public final String key = name().toLowerCase(Locale.ROOT);
    }

    public enum WakeReason {
        RESTED,
        HURT,
        PAIN,
        COLD,
        VOMITING,
        WATER,
        NAUSEA,
        WITHDRAWAL,
        DESPAIR,
        HUNGER,
        THIRST,
        /** Got up by themselves. */
        VOLUNTARY;

        public final String key = name().toLowerCase(Locale.ROOT);
    }

    private SleepSystem() {}

    public static float recovery(int quality) {
        return RECOVERY[Math.max(0, Math.min(RECOVERY.length - 1, quality))];
    }

    /** Why the body cannot fall asleep on a spot of this quality, or {@code null} if it can. */
    public static Refusal refusal(Body body, int quality) {
        Needs n = body.needs;
        if (quality <= 0) return Refusal.BAD_SPOT;
        if (n.energy > TIRED) return Refusal.NOT_TIRED;
        if (body.derived.totalPain > MAX_PAIN) return Refusal.IN_PAIN;
        if (n.sickness > MAX_SICKNESS) return Refusal.TOO_SICK;
        if (restless(body)) return Refusal.RESTLESS;
        return null;
    }

    /** Opioid withdrawal or a lot of caffeine: impossible to fall asleep. */
    public static boolean restless(Body body) {
        return ChemistrySystem.withdrawal(body.chem) > ChemistrySystem.SLEEPLESS_WITHDRAWAL || body.chem.caffeine > 30f;
    }

    public static void fallAsleep(Body body, int quality) {
        Needs n = body.needs;
        n.asleep = true;
        n.sleepQuality = Math.max(BAD, Math.min(GOOD, quality));
        body.emit(BodyEvent.Type.FELL_ASLEEP);
    }

    /** Ends a sleep on the ground. A bad night leaves the mind groggy for a while. */
    public static void wake(Body body, WakeReason reason) {
        Needs n = body.needs;
        if (!n.asleep) return;
        n.asleep = false;
        if (n.sleepQuality <= BAD) n.groggy = 150f;
        else if (n.sleepQuality == MEDIOCRE) n.groggy = 60f;
        body.emit(reason == WakeReason.RESTED ? BodyEvent.Type.RESTED : BodyEvent.Type.WOKEN_UP);
    }

    /** Whatever would wake a sleeping body right now, or {@code null}. */
    public static WakeReason wakeReason(Body body, Environment env) {
        Needs n = body.needs;
        Chemistry c = body.chem;
        if (n.energy >= RESTED) return WakeReason.RESTED;
        if (body.vitals.bodyTemp < 30f) return WakeReason.COLD;
        if (n.vomiting > 0f) return WakeReason.VOMITING;
        if (body.derived.totalPain > MAX_PAIN) return WakeReason.PAIN;
        if (env != null && env.underwater) return WakeReason.WATER;
        if (n.sickness > 55f) return WakeReason.NAUSEA;
        if (ChemistrySystem.withdrawal(c) > ChemistrySystem.SLEEPLESS_WITHDRAWAL) return WakeReason.WITHDRAWAL;
        if (body.mind.mood < -50f) return WakeReason.DESPAIR;
        if (n.hunger <= 0f) return WakeReason.HUNGER;
        if (n.thirst <= 0f) return WakeReason.THIRST;
        return null;
    }

    public static void tick(Body body, Environment env, SimSettings settings, float dt) {
        Needs n = body.needs;
        n.groggy = Math.max(0f, n.groggy - dt);
        if (!settings.energy()) {
            n.energy = 100f;
            n.passedOut = false;
            wake(body, WakeReason.RESTED);
            return;
        }
        float speed = settings.energySpeed();
        if (n.asleep) {
            WakeReason reason = wakeReason(body, env);
            if (reason != null) wake(body, reason);
        }
        if (body.isAsleep(env)) {
            int quality = n.passedOut ? BAD : n.asleep ? n.sleepQuality : env.sleepQuality;
            quality = Math.max(BAD, Math.min(GOOD, quality));
            if (!n.asleep) n.sleepQuality = quality;
            n.energy = Math.min(100f, n.energy + recovery(quality) * speed * dt);
        } else {
            n.energy = Math.max(0f, n.energy - drain(body) * speed * dt);
        }
        if (!n.passedOut && n.energy <= 0f) {
            n.passedOut = true;
            body.emit(BodyEvent.Type.PASSED_OUT_TIRED, null);
        } else if (n.passedOut && n.energy >= WAKE_ENERGY) {
            n.passedOut = false;
        }
    }

    /** Energy lost per second awake. */
    public static float drain(Body body) {
        Needs n = body.needs;
        Chemistry c = body.chem;
        float stamina = 1f + 2.5f * (100f - n.stamina) / 100f;
        float sickness = 1f + 0.02f * n.sickness;
        float mood = 1f + Math.max(0f, -body.mind.mood) / 100f;
        float caffeine = c.caffeine > 10f ? 0.55f : 1f;
        float relief = ChemistrySystem.relief(c);
        float sedation = relief > 30f ? 0.0025f * relief : 0f;
        return DRAIN * stamina * sickness * mood * caffeine + sedation;
    }
}
