package com.fleshbone.sim.system;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.Environment;
import com.fleshbone.sim.Needs;

/**
 * Dirt on the body (Casualties: Unknown: 2.4 % an hour on its own, plus blood, digging, being hit, lying on the
 * ground) and getting wet; water and rain wash it off, heat and warm air dry it.
 */
public final class HygieneSystem {
    /** Dirt gained per second just from living. */
    public static final float PASSIVE_DIRT = 2.4f / 3600f;
    /** Dirt per hit taken. */
    public static final float HIT_DIRT = 1.35f;

    private HygieneSystem() {}

    public static void tick(Body body, Environment env, float dt) {
        Needs n = body.needs;
        float dirt = PASSIVE_DIRT + body.derived.externalBleed * 0.3f;
        if (body.isIncapacitated()) dirt += 0.05f;
        if (env.inWater) dirt -= 2f;
        else if (env.exposedToRain) dirt -= 0.5f;
        n.dirtiness = SimMath.clamp(n.dirtiness + dirt * dt, 0f, 100f);

        float wet;
        if (env.inWater) wet = 8f;
        else if (env.exposedToRain) wet = 0.6f;
        else wet = -(0.08f + env.heatStrength * 0.5f + Math.max(0f, env.feltTemperature() - 20f) * 0.01f);
        n.wetness = SimMath.clamp(n.wetness + wet * dt, 0f, 100f);
    }

    /** Called by the game when the player digs dirt-like blocks or gets hit. */
    public static void soil(Body body, float amount) {
        body.needs.dirtiness = SimMath.clamp(body.needs.dirtiness + amount, 0f, 100f);
    }
}
