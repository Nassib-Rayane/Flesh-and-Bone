package com.fleshbone.sim.system;

import java.util.random.RandomGenerator;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyEvent;
import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.Chemistry;
import com.fleshbone.sim.Environment;
import com.fleshbone.sim.Needs;
import com.fleshbone.sim.Vitals;
import com.fleshbone.sim.injury.InjuryApplier;

/**
 * Nausea, after Casualties: Unknown: fades 2.4 points a minute; opioids, withdrawal, fever, concussion, brain damage,
 * venom and water intoxication feed it. Above 50, a vomiting urge builds up (nausea × 0.0025 per second); when it
 * reaches 50 the body is overcome for five seconds (barely conscious, almost unable to move), then the stomach empties:
 * −17 hunger, −10 thirst, −8 nausea and a little blood, more if something bleeds inside.
 */
public final class SicknessSystem {
    public static final float DECAY = 2.4f / 60f;
    /** Nausea above which the urge to vomit builds up. */
    public static final float URGE = 50f;
    public static final float VOMIT_AT = 50f;
    public static final float FIT_SECONDS = 5f;
    /** Consciousness during a vomiting fit. */
    public static final float FIT_CONSCIOUSNESS = 32.5f;

    private SicknessSystem() {}

    public static void tick(Body body, Environment env, float dt, RandomGenerator rng) {
        Needs n = body.needs;
        Chemistry c = body.chem;
        Vitals v = body.vitals;
        float gain = 0f;
        if (ChemistrySystem.relief(c) > 30f) gain += 0.04f;
        if (ChemistrySystem.withdrawal(c) > ChemistrySystem.NAUSEOUS_WITHDRAWAL) gain += 0.09f;
        if (v.bodyTemp > 39f) gain += 0.03f;
        if (body.part(BodyPart.HEAD).muscle < InjuryApplier.CONCUSSION_MUSCLE) gain += 0.05f;
        if (v.brainHealth < BrainSystem.BLURRED) gain += 0.02f;
        if (n.thirst > ThirstSystem.WATER_INTOXICATION) gain += 0.05f;
        gain += c.venom * 0.002f;
        n.sickness = SimMath.clamp(n.sickness + (gain - DECAY) * dt, 0f, 100f);

        if (n.vomiting > 0f) {
            n.vomiting = Math.max(0f, n.vomiting - dt);
            if (n.vomiting <= 0f) empty(body);
        } else if (n.sickness > URGE) {
            n.vomitBuildup += n.sickness * 0.0025f * dt;
            if (n.vomitBuildup >= VOMIT_AT && !body.isUnconscious()) startVomiting(body);
        } else {
            n.vomitBuildup = Math.max(0f, n.vomitBuildup - 0.1f * dt);
        }
    }

    /** The fit starts (nausea overflow, a brain episode, overeating...). */
    public static void startVomiting(Body body) {
        Needs n = body.needs;
        if (n.vomiting > 0f) return;
        n.vomiting = FIT_SECONDS;
        n.vomitBuildup = 0f;
        body.emit(BodyEvent.Type.VOMITED);
    }

    /** End of the fit: the stomach empties. */
    static void empty(Body body) {
        Needs n = body.needs;
        Vitals v = body.vitals;
        n.hunger = Math.max(Needs.MIN_HUNGER, n.hunger - 17f);
        n.thirst = Math.max(Needs.MIN_THIRST, n.thirst - 10f);
        n.sickness = Math.max(0f, n.sickness - 8f);
        float blood = 0.025f;
        boolean bleedingInside = body.part(BodyPart.ABDOMEN).internalBleed > 5f || body.part(BodyPart.THORAX).internalBleed > 5f;
        if (bleedingInside) {
            blood += 0.075f;
            body.emit(BodyEvent.Type.VOMITED_BLOOD);
        }
        v.bloodVolume = Math.max(0f, v.bloodVolume - blood);
    }
}
