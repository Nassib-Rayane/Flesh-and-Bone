package com.fleshbone.sim;

/** Mental state. */
public final class Mind {
    /** Mood as felt, −100 (miserable) to 100 (gleeful): lasting mood plus what the current situation adds or takes. */
    public float mood;
    /** Lasting mood left by events (a good meal, a healed bone, an amputation...), fading back to 0. */
    public float moodBase;
    /** 0–100: scared (>25), traumatized (>45), shell-shocked (>80). */
    public float trauma;
    /** Seconds spent in darkness, feeds dread. */
    public float darkness;

    public void reset() {
        mood = moodBase = trauma = darkness = 0f;
    }

    /** Adds a lasting mood change (kept within ±40 so events cannot pin the mood). */
    public void addMood(float amount) {
        moodBase = Math.max(-40f, Math.min(40f, moodBase + amount));
    }

    public void visit(StateVisitor v) {
        mood = v.f("mood", mood);
        moodBase = v.f("mood_base", moodBase);
        trauma = v.f("trauma", trauma);
        darkness = v.f("darkness", darkness);
    }
}
