package com.fleshbone.sim.system;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.Environment;
import com.fleshbone.sim.Needs;

/** Dirt on the body (blood, mud, lying on the ground) and getting wet; water and rain wash it off. */
public final class HygieneSystem {
    private HygieneSystem() {}

    public static void tick(Body body, Environment env, float dt) {
        Needs n = body.needs;
        float dirt = body.derived.externalBleed * 0.3f;
        if (body.isIncapacitated()) dirt += 0.05f;
        if (env.inWater) dirt -= 2f;
        else if (env.exposedToRain) dirt -= 0.5f;
        n.dirtiness = SimMath.clamp(n.dirtiness + dirt * dt, 0f, 100f);

        float wet;
        if (env.inWater) wet = 8f;
        else if (env.exposedToRain) wet = 0.6f;
        else wet = -(0.08f + Math.max(0f, env.heatSource) * 0.05f + Math.max(0f, env.ambientTemp - 20f) * 0.01f);
        n.wetness = SimMath.clamp(n.wetness + wet * dt, 0f, 100f);
    }

    /** Called by the game when the player digs dirt-like blocks. */
    public static void soil(Body body, float amount) {
        body.needs.dirtiness = SimMath.clamp(body.needs.dirtiness + amount, 0f, 100f);
    }
}
