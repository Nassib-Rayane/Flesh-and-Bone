package com.fleshbone.sim;

/**
 * Survival needs. Hunger (−50 to 125) and thirst (−50 to 250) follow Casualties: Unknown's scales: 100 is comfortable,
 * above is satiated or slaked, 0 and below is starving or dehydrated. The other values are 0–100.
 */
public final class Needs {
    public static final float MAX_HUNGER = 125f;
    public static final float MIN_HUNGER = -50f;
    public static final float MAX_THIRST = 250f;
    public static final float MIN_THIRST = -50f;
    /** Healthy adult weight in kg; the weight classes are spread around it. */
    public static final float NORMAL_WEIGHT = 70f;

    public float hunger = 100f;
    /** Body weight in kg: grows with what is eaten, shrinks a little every second. */
    public float weight = NORMAL_WEIGHT;
    public float thirst = 100f;
    /** Sleepiness: 100 = rested, 0 = passing out. */
    public float energy = 100f;
    public float stamina = 100f;
    public float wetness;
    public float dirtiness;
    /** Nausea. */
    public float sickness;
    /** Builds up while nausea is above 50; at 50 the stomach empties. */
    public float vomitBuildup;
    /** Seconds left of an ongoing vomiting fit. */
    public float vomiting;
    /** Seconds left of a collapse from exhaustion (stamina ran out). */
    public float collapsed;
    /** Asleep on the ground (not in a bed): wakes up when rested or disturbed. */
    public boolean asleep;
    /** Quality of the current or last sleep: 0 none, 1 bad, 2 mediocre, 3 okay, 4 good. */
    public int sleepQuality;
    /** Seconds of grogginess left after a bad sleep (consciousness capped). */
    public float groggy;
    /** Collapsed from lack of sleep: unconscious until some energy comes back. */
    public boolean passedOut;
    /** Kilograms carried (inventory and equipment), measured by the game. */
    public float carried;
    /** Temperature felt around the body (air, or water when swimming), for display. */
    public float airTemp = 20f;
    /** Workout in progress ({@link Environment.Exercise} ordinal), mirrored here so the client shows the pose. */
    public int exercise;

    public void reset() {
        hunger = 100f;
        weight = NORMAL_WEIGHT;
        thirst = 100f;
        energy = 100f;
        stamina = 100f;
        wetness = dirtiness = sickness = vomitBuildup = vomiting = collapsed = groggy = 0f;
        asleep = passedOut = false;
        sleepQuality = 0;
    }

    public void visit(StateVisitor v) {
        hunger = v.f("hunger", hunger);
        weight = v.f("weight", weight);
        thirst = v.f("thirst", thirst);
        energy = v.f("energy", energy);
        stamina = v.f("stamina", stamina);
        wetness = v.f("wetness", wetness);
        dirtiness = v.f("dirtiness", dirtiness);
        sickness = v.f("sickness", sickness);
        vomitBuildup = v.f("vomit_buildup", vomitBuildup);
        vomiting = v.f("vomiting", vomiting);
        collapsed = v.f("collapsed", collapsed);
        asleep = v.b("asleep", asleep);
        sleepQuality = v.i("sleep_quality", sleepQuality);
        groggy = v.f("groggy", groggy);
        passedOut = v.b("passed_out", passedOut);
        carried = v.f("carried", carried);
        exercise = v.i("exercise", exercise);
        airTemp = v.f("air_temp", airTemp);
    }
}
