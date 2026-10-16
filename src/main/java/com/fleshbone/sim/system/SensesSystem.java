package com.fleshbone.sim.system;

import java.util.random.RandomGenerator;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyEvent;
import com.fleshbone.sim.Senses;

/** Eyes and ears: blasts damage hearing (it comes back about a point a minute), lost eyes stay lost. */
public final class SensesSystem {
    /** Hearing recovered per second. */
    public static final float HEARING_RECOVERY = 1f / 60f;

    private SensesSystem() {}

    public static void tick(Body body, float dt) {
        Senses s = body.senses;
        if (s.tinnitus > 0f) s.tinnitus = Math.max(0f, s.tinnitus - dt);
        if (s.hearingLoss > 0f) s.hearingLoss = Math.max(0f, s.hearingLoss - HEARING_RECOVERY * dt);
    }

    /** A blast close to the ears. */
    public static void deafen(Body body, float amount) {
        if (amount <= 0f) return;
        Senses s = body.senses;
        s.hearingLoss = Math.min(100f, s.hearingLoss + amount);
        s.tinnitus = Math.max(s.tinnitus, Math.min(120f, amount * 1.5f));
        if (amount > 10f) body.emit(BodyEvent.Type.EARS_RINGING);
    }

    /** Loses one eye (the remaining one if the other is already gone). */
    public static void loseEye(Body body, RandomGenerator rng) {
        Senses s = body.senses;
        if (s.blind()) return;
        if (s.leftEyeLost) s.rightEyeLost = true;
        else if (s.rightEyeLost) s.leftEyeLost = true;
        else if (rng.nextBoolean()) s.leftEyeLost = true;
        else s.rightEyeLost = true;
        body.emit(BodyEvent.Type.EYE_LOST);
    }
}
