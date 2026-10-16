package com.fleshbone.sim.system;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.Needs;
import com.fleshbone.sim.Skills;

/**
 * Carrying capacity, after Casualties: Unknown: a base load, ±1.25 kg per level of the weaker of strength and
 * resilience, more when satiated, less when hungry, thirsty, nauseous or outside a healthy weight. Overload goes from
 * 0 (within capacity) to 1 (twice the capacity): stamina costs ×(1 + overload), walking up to −25 %, slower swings.
 */
public final class Encumbrance {
    public static final float BASE_CAPACITY = 25f;
    /** Base capacity in kg, set from the server config. */
    public static float baseCapacity = BASE_CAPACITY;

    private Encumbrance() {}

    public static float capacity(Body body) {
        Needs n = body.needs;
        Skills s = body.skills;
        float capacity = baseCapacity + 1.25f * (Math.min(s.strength(), s.resilience()) - Skills.START_LEVEL);
        if (n.hunger > HungerSystem.SATIATED) capacity += 3.4f;
        else if (n.hunger < HungerSystem.HUNGRY) capacity -= 3.4f;
        if (n.thirst < 40f) capacity -= 2.3f;
        capacity -= n.sickness * 0.057f;
        capacity += weightTerm(n.weight);
        return Math.max(5f, capacity);
    }

    /** Too heavy a body carries itself instead of a load; too thin a body has no strength. Up to −13.6 kg. */
    static float weightTerm(float weight) {
        if (weight > 77f) return -Math.min(13.6f, (weight - 77f) * 0.5f);
        if (weight < 62.86f) return -Math.min(13.6f, (62.86f - weight) * 0.8f);
        return 0f;
    }

    public static float overload(float load, float capacity) {
        if (capacity <= 0f) return 1f;
        return SimMath.clamp(load / capacity - 1f, 0f, 1f);
    }
}
