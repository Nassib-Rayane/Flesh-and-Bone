package com.fleshbone.sim.system;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.Environment;
import com.fleshbone.sim.Vitals;

/**
 * Core temperature. The body holds 37 °C (plus fever) against the environment: the colder or hotter the air, the more
 * it drifts, softened by clothing and helped by heat sources, shivering and sweating.
 */
public final class TemperatureSystem {
    public static final float NORMAL = 37f;

    private TemperatureSystem() {}

    public static void tick(Body body, Environment env, boolean environmental, float dt) {
        Vitals v = body.vitals;
        float setpoint = NORMAL + InfectionSystem.feverDrive(body);
        float drift = 0f;
        if (environmental) {
            float felt = env.ambientTemp + env.heatSource;
            // Wet skin loses heat much faster; water itself even more
            float wetness = body.needs.wetness / 100f;
            float exposure = (1f - 0.75f * env.insulation) * (1f + wetness * 1.5f) * (env.inWater ? 2.5f : 1f);
            // Comfortable band: the body regulates easily between 15 and 28 °C felt
            if (felt < 15f) drift -= (15f - felt) * 0.0009f * exposure;
            else if (felt > 28f) drift += (felt - 28f) * 0.0012f * (1f - 0.5f * env.insulation) * (body.needs.thirst < 30f ? 1.6f : 1f);
            if (env.sprinting) drift += 0.004f;
        }
        // Regulation pulls toward the setpoint (shivering, sweating); weaker when exhausted or unconscious
        float regulation = 0.01f * (body.isUnconscious() ? 0.4f : 1f) * (body.needs.stamina < 15f ? 0.6f : 1f);
        float correction = SimMath.clamp(setpoint - v.bodyTemp, -1f, 1f) * regulation;
        v.bodyTemp = SimMath.clamp(v.bodyTemp + (drift + correction) * dt, 20f, 45f);
    }
}
