package com.fleshbone.sim;

/**
 * What the world tells the body each tick. Filled in by the Minecraft glue (or by tests); the simulation never reads
 * the game directly.
 */
public final class Environment {
    /** Simulated seconds elapsed this tick (already multiplied by the simulation speed). */
    public float dt = 0.05f;
    /** Vanilla food level, 0–20. */
    public int foodLevel = 20;
    public float saturation = 5f;
    public boolean sprinting;
    public boolean swimming;
    /** Head under water. */
    public boolean underwater;
    /** Standing or floating in water. */
    public boolean inWater;
    /** Rain or snow falling on the player. */
    public boolean exposedToRain;
    public boolean sleeping;
    public boolean onGround = true;
    public boolean crouching;
    /** Out of air (drowning) or stuck in a block. */
    public boolean breathingBlocked;
    /** Horizontal speed in blocks per second. */
    public float moveSpeed;
    /** Air temperature around the player in °C, before heat sources. */
    public float ambientTemp = 20f;
    /** Extra °C radiated by nearby fires, lava, furnaces... */
    public float heatSource;
    /** 0–1 clothing insulation. */
    public float insulation;
    /** 0–15 block light at the player's eyes. */
    public int light = 15;
    public boolean skyVisible = true;
    /** 0–1, how heavy the worn armor is. */
    public float armorWeight;
    /** Something comforting nearby: music, a tamed pet... */
    public boolean comfort;

    /** Regeneration multiplier from vanilla hunger: full speed at 6+, slowed when hungry, none when starving. */
    public float nutrition() {
        if (foodLevel >= 6) return 1f;
        return foodLevel > 0 ? 0.35f : 0f;
    }
}
