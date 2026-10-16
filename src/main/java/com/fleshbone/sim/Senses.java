package com.fleshbone.sim;

/** Eyes and ears: lost eyes are permanent, hearing damaged by blasts comes back slowly. */
public final class Senses {
    /** Hearing loss above which sounds are muffled. */
    public static final float HEARING_IMPAIRED = 15f;
    public static final float HEARING_POOR = 40f;
    public static final float HEARING_DEAF = 70f;

    public boolean leftEyeLost;
    public boolean rightEyeLost;
    /** 0 (fine) to 100 (deaf). */
    public float hearingLoss;
    /** Seconds of ringing ears left. */
    public float tinnitus;

    public boolean blind() {
        return leftEyeLost && rightEyeLost;
    }

    /** Volume multiplier for everything heard. */
    public float hearing() {
        if (hearingLoss > HEARING_DEAF) return 0.15f;
        if (hearingLoss > HEARING_POOR) return 0.4f;
        if (hearingLoss > HEARING_IMPAIRED) return 0.7f;
        return 1f;
    }

    public void reset() {
        leftEyeLost = rightEyeLost = false;
        hearingLoss = tinnitus = 0f;
    }

    public void visit(StateVisitor v) {
        leftEyeLost = v.b("left_eye_lost", leftEyeLost);
        rightEyeLost = v.b("right_eye_lost", rightEyeLost);
        hearingLoss = v.f("hearing_loss", hearingLoss);
        tinnitus = v.f("tinnitus", tinnitus);
    }
}
