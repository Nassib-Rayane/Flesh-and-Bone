package com.fleshbone.sim;

/** Mental state. */
public final class Mind {
    /** −100 (miserable) to 100 (gleeful). */
    public float mood;
    /** 0–100: scared (>25), traumatized (>45), shell-shocked (>80). */
    public float trauma;
    /** Temporary mood boost or penalty from recent events (a good meal, a healed bone...), fades over time. */
    public float moodBonus;
    /** Seconds spent in darkness, feeds dread. */
    public float darkness;

    public void reset() {
        mood = trauma = moodBonus = darkness = 0f;
    }

    public void visit(StateVisitor v) {
        mood = v.f("mood", mood);
        trauma = v.f("trauma", trauma);
        moodBonus = v.f("mood_bonus", moodBonus);
        darkness = v.f("darkness", darkness);
    }
}
