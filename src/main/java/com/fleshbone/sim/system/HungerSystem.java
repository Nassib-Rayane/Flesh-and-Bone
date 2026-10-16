package com.fleshbone.sim.system;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyEvent;
import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.Environment;
import com.fleshbone.sim.Needs;
import com.fleshbone.sim.PartState;
import com.fleshbone.sim.SimSettings;

/**
 * Hunger and body weight, after Casualties: Unknown. Hunger drops 2.6 points a minute (plus shivering, healing and new
 * blood). Every meal also adds weight (one kilogram per 150 points eaten), and the body burns its reserves at the
 * same base pace, faster when active: eating as much as you spend keeps the weight steady. Starving (hunger at 0)
 * wastes the muscles everywhere until the body gives out.
 */
public final class HungerSystem {
    /** Hunger lost per second. */
    public static final float DRAIN = 2.6f / 60f;
    /** Kilograms gained per point of hunger eaten (C:U's 210 per kg for its 50 kg character, scaled to 70 kg). */
    public static final float KG_PER_HUNGER = 1f / 150f;
    /** Muscle lost per second on every part while starving. */
    public static final float STARVATION_WASTING = 0.04f;
    public static final float SATIATED = 100f;
    public static final float FULL = 120f;
    public static final float HUNGRY = 40f;
    /** Chance to throw up right away after eating past 125. */
    public static final float OVEREATING_VOMIT_CHANCE = 0.2f;

    private HungerSystem() {}

    public static void tick(Body body, Environment env, SimSettings settings, float dt) {
        Needs n = body.needs;
        if (!settings.hunger()) {
            n.hunger = SATIATED;
            return;
        }
        float speed = settings.hungerSpeed();
        float before = n.hunger;
        n.hunger = Math.max(Needs.MIN_HUNGER, n.hunger - DRAIN * speed * (body.isAsleep(env) ? 0.7f : 1f) * dt);
        n.weight = Math.max(30f, n.weight - DRAIN * KG_PER_HUNGER * speed * activity(env) * dt);
        if (n.hunger <= 0f) {
            if (before > 0f) body.emit(BodyEvent.Type.STARVING);
            for (BodyPart part : BodyPart.VALUES) {
                PartState s = body.part(part);
                if (!s.amputated) s.muscle = Math.max(0f, s.muscle - STARVATION_WASTING * dt);
            }
        }
    }

    /** How much faster than at rest the body burns its reserves. */
    static float activity(Environment env) {
        if (env.exercise != Environment.Exercise.NONE || env.sprinting || env.swimming) return 2f;
        return env.moveSpeed > 0.5f ? 1.3f : 1f;
    }
}
