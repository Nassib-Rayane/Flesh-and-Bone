package com.fleshbone.sim;

/**
 * What the world tells the body each tick. Filled in by the Minecraft glue (or by tests); the simulation never reads
 * the game directly.
 */
public final class Environment {
    /** Workouts started from the health panel. */
    public enum Exercise {
        NONE,
        PUSHUPS,
        SQUATS,
        PLANK;

        public static final Exercise[] VALUES = values();
    }

    /** Simulated seconds elapsed this tick (already multiplied by the simulation speed). */
    public float dt = 0.05f;
    public boolean sprinting;
    public boolean swimming;
    /** Head under water. */
    public boolean underwater;
    /** Standing or floating in water. */
    public boolean inWater;
    /** Rain or snow falling on the player. */
    public boolean exposedToRain;
    /** Lying in a vanilla bed. */
    public boolean sleeping;
    /** How good the spot the body sleeps on is: 0 impossible, 1 bad, 2 mediocre, 3 okay, 4 good (a bed). */
    public int sleepQuality = 4;
    public boolean onGround = true;
    public boolean crouching;
    /** Out of air (drowning) or stuck in a block. */
    public boolean breathingBlocked;
    /** Horizontal speed in blocks per second. */
    public float moveSpeed;
    /** Air temperature around the player in °C, before heat sources. */
    public float ambientTemp = 20f;
    /** Temperature radiated by the nearby fires, lava or ice, and how strongly it is felt (0–1). */
    public float heatTarget = 20f;
    public float heatStrength;
    /** 0–1 clothing insulation. */
    public float insulation;
    /** 0–15 block light at the player's eyes. */
    public int light = 15;
    public boolean skyVisible = true;
    /** Something comforting nearby: a fire, a tamed pet... */
    public boolean comfort;
    public Exercise exercise = Exercise.NONE;
    /** Kilograms carried: inventory and equipment. */
    public float load;

    /** Air temperature pulled toward the nearby heat (or cold) sources. */
    public float feltTemperature() {
        return ambientTemp + (heatTarget - ambientTemp) * heatStrength;
    }
}
