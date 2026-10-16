package com.fleshbone.sim;

/** Survival needs on top of vanilla hunger. All values 0–100 except thirst, which goes up to 150 (overhydration). */
public final class Needs {
    public static final float MAX_THIRST = 150f;

    /** 100 = fully hydrated. */
    public float thirst = 100f;
    /** Sleepiness: 100 = rested, 0 = passing out. */
    public float energy = 100f;
    public float stamina = 100f;
    public float wetness;
    public float dirtiness;
    /** Nausea. */
    public float sickness;
    /** Seconds before the next possible vomit. */
    public float vomitCooldown;
    /** Seconds spent starving (vanilla food level at 0). */
    public float starvation;
    /** Collapsed from exhaustion: unconscious until some energy comes back. */
    public boolean passedOut;

    public void reset() {
        thirst = 100f;
        energy = 100f;
        stamina = 100f;
        wetness = dirtiness = sickness = vomitCooldown = starvation = 0f;
        passedOut = false;
    }

    public void visit(StateVisitor v) {
        thirst = v.f("thirst", thirst);
        energy = v.f("energy", energy);
        stamina = v.f("stamina", stamina);
        wetness = v.f("wetness", wetness);
        dirtiness = v.f("dirtiness", dirtiness);
        sickness = v.f("sickness", sickness);
        vomitCooldown = v.f("vomit_cooldown", vomitCooldown);
        starvation = v.f("starvation", starvation);
        passedOut = v.b("passed_out", passedOut);
    }
}
