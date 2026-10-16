package com.fleshbone.sim.system;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.Environment;
import com.fleshbone.sim.Needs;
import com.fleshbone.sim.SimSettings;
import com.fleshbone.sim.Vitals;

/**
 * Core temperature as a heat balance, after Casualties: Unknown's structure:
 * <pre>
 * dT/dt = exchange × (felt air − T) / insulation      (clothes, body fat, wet skin, water)
 *       + metabolic heat                              (less when exhausted or on opioids)
 *       + activity heat                               (running, swimming, workouts)
 *       + shivering                                   (below the setpoint, burns hunger)
 *       + emergency heating below 32 °C               (burns hunger fast)
 *       − sweating                                    (above the setpoint, costs water, wets the skin)
 *       − evaporation from wet skin
 * </pre>
 * Rates are calibrated for Minecraft's climates: naked, 20 °C air is neutral, −10 °C brings hypothermia in about ten
 * minutes, leather and a campfire keep the cold away. Infections raise the setpoint (fever, up to 40.5 °C).
 */
public final class TemperatureSystem {
    public static final float NORMAL = 37f;
    /** Heat exchanged with the air per second and per °C of difference, without clothes. */
    public static final float EXCHANGE = 0.00074f;
    /** Heat the resting body makes (°C/s): just enough to hold 37 °C naked in 20 °C air. */
    public static final float BASE_HEAT = 0.0126f;
    public static final float SHIVER_HEAT = 0.02f;
    public static final float EMERGENCY_HEAT = 0.01f;
    public static final float SWEAT_COOLING = 0.03f;
    /** Hunger burnt per second at full shivering, and by emergency heating. */
    public static final float SHIVER_HUNGER = 0.02f;
    public static final float EMERGENCY_HUNGER = 0.035f;
    /** Thirst lost per second at full sweating. */
    public static final float SWEAT_THIRST = 0.05f;
    public static final float HYPOTHERMIA = 35f;
    /** Below this, emergency heating kicks in and the limbs go numb. */
    public static final float NUMB = 32f;
    public static final float FIBRILLATION_COLD = 28.5f;
    public static final float HYPERTHERMIA = 38.5f;
    /** Above this the body loses blood volume (0.225 L/min). */
    public static final float HEAT_BLOOD_LOSS = 41f;

    private TemperatureSystem() {}

    public static void tick(Body body, Environment env, SimSettings settings, float dt) {
        Vitals v = body.vitals;
        Needs n = body.needs;
        float setpoint = NORMAL + InfectionSystem.feverDrive(body);
        float t = v.bodyTemp;
        float change;
        if (!settings.temperature()) {
            // Weather off: only fevers move the temperature
            change = SimMath.clamp(setpoint - t, -1f, 1f) * 0.01f;
        } else {
            float exchange = EXCHANGE * (env.feltTemperature() - t) / insulation(body, env);
            float relief = Math.min(100f, ChemistrySystem.relief(body.chem));
            float metabolism = BASE_HEAT * (0.6f + 0.4f * SimMath.clamp(n.energy / 100f, 0f, 1f)) * (1f - 0.005f * relief);
            boolean starving = settings.hunger() && n.hunger <= 0f;
            float drive = body.isUnconscious() ? 0.4f : 1f;
            float shiver = SimMath.clamp((setpoint - 0.5f - t) / 1.5f, 0f, 1f) * drive * (starving ? 0.3f : 1f);
            boolean emergency = t < NUMB && !starving;
            float sweat = SimMath.clamp((t - setpoint - 0.2f) / 1f, 0f, 1f) * (n.thirst > 0f ? 1f : 0.3f);
            change = exchange + metabolism + activityHeat(env)
                    + shiver * SHIVER_HEAT + (emergency ? EMERGENCY_HEAT : 0f)
                    - sweat * SWEAT_COOLING - 0.0001f * n.wetness;
            if (settings.hunger()) {
                n.hunger = Math.max(Needs.MIN_HUNGER, n.hunger - (shiver * SHIVER_HUNGER + (emergency ? EMERGENCY_HUNGER : 0f)) * dt);
            }
            if (settings.thirst()) n.thirst = Math.max(Needs.MIN_THIRST, n.thirst - sweat * SWEAT_THIRST * dt);
            n.wetness = Math.min(100f, n.wetness + sweat * 0.15f * dt);
        }
        v.bodyTemp = SimMath.clamp(t + change * dt, 15f, 46f);
        if (v.bodyTemp > HEAT_BLOOD_LOSS) v.bloodVolume = Math.max(0f, v.bloodVolume - 0.225f / 60f * dt);
    }

    /**
     * Thermal insulation: 1 naked, up to 4 fully dressed; ±2 % per kg of body weight around 70 kg; wet clothes keep
     * only 40 % of it; water conducts heat away whatever you wear.
     */
    public static float insulation(Body body, Environment env) {
        Needs n = body.needs;
        float clothes = 1f + 3f * SimMath.clamp(env.insulation, 0f, 1f);
        float fat = SimMath.clamp(1f + 0.0214f * (n.weight - Needs.NORMAL_WEIGHT), 0.4f, 1.8f);
        float dry = 1f - 0.6f * n.wetness / 100f;
        float insulation = clothes * fat * dry;
        if (env.inWater) insulation = Math.min(insulation, 0.6f);
        return Math.max(0.25f, insulation);
    }

    /** Heat made by moving (°C/s). */
    static float activityHeat(Environment env) {
        if (env.exercise != Environment.Exercise.NONE) return 0.01f;
        if (env.swimming) return 0.008f;
        if (env.sprinting) return 0.012f;
        return env.moveSpeed > 0.5f ? 0.003f : 0f;
    }
}
