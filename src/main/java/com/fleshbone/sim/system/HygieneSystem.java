package com.fleshbone.sim.system;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.Environment;
import com.fleshbone.sim.Needs;

/**
 * Dirt on the body (Casualties: Unknown: 2.4 % an hour on its own, plus blood, digging, being hit, lying on the
 * ground) and getting wet. Water and rain wash the dirt off. Wet clothes dry from the body's own heat (faster when it
 * is warm and moving), from dry, warm air and much faster by a fire; heavy clothes hold the water longer. Every bit of
 * water that evaporates takes heat away: drying out completely costs about 1.5 °C.
 */
public final class HygieneSystem {
    /** Dirt gained per second just from living. */
    public static final float PASSIVE_DIRT = 2.4f / 3600f;
    /** Dirt per hit taken. */
    public static final float HIT_DIRT = 1.35f;
    /** Core °C taken away per point of wetness that evaporates. */
    public static final float EVAPORATION_COOLING = 0.015f;

    private HygieneSystem() {}

    public static void tick(Body body, Environment env, float dt) {
        Needs n = body.needs;
        float dirt = PASSIVE_DIRT + body.derived.externalBleed * 0.3f;
        if (body.isIncapacitated()) dirt += 0.05f;
        if (env.inWater) dirt -= 2f;
        else if (env.exposedToRain) dirt -= 0.5f;
        n.dirtiness = SimMath.clamp(n.dirtiness + dirt * dt, 0f, 100f);

        if (env.inWater) {
            n.wetness = Math.min(100f, n.wetness + 8f * dt);
        } else if (env.exposedToRain) {
            n.wetness = Math.min(100f, n.wetness + 0.6f * dt);
        } else if (n.wetness > 0f) {
            float dried = Math.min(n.wetness, dryingRate(body, env) * dt);
            n.wetness -= dried;
            body.vitals.bodyTemp -= dried * EVAPORATION_COOLING;
        }
    }

    /** Wetness points dried per second. */
    public static float dryingRate(Body body, Environment env) {
        float felt = env.feltTemperature();
        float air = 0.03f + Math.max(0f, felt - 10f) * 0.006f;
        float bodyHeat = Math.max(0f, body.vitals.bodyTemp - 35f) * 0.05f * (env.moveSpeed > 0.5f ? 1.5f : 1f);
        float fire = env.heatTarget > 25f ? env.heatStrength : 0f;
        float dryness = 1.3f - 0.6f * SimMath.clamp(env.humidity, 0f, 1f);
        float clothes = 1f - 0.4f * SimMath.clamp(env.insulation, 0f, 1f);
        return ((air + bodyHeat) * dryness + fire) * clothes;
    }

    /** Called by the game when the player digs dirt-like blocks or gets hit. */
    public static void soil(Body body, float amount) {
        body.needs.dirtiness = SimMath.clamp(body.needs.dirtiness + amount, 0f, 100f);
    }

    /** Washing (a cauldron, a bucket poured over yourself). */
    public static void wash(Body body, float amount) {
        body.needs.dirtiness = Math.max(0f, body.needs.dirtiness - amount);
    }
}
