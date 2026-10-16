package com.fleshbone.sim;

/**
 * Gameplay settings the simulation needs, decoupled from the NeoForge config so the simulation stays pure Java.
 *
 * @param damageScale            injury severity per point of vanilla damage
 * @param healingSpeed           multiplier for natural skin and muscle regeneration
 * @param fractureHealSeconds    time for an untreated fracture to heal
 * @param dislocationHealSeconds time for an untreated dislocation to heal
 */
public record SimSettings(
        float damageScale,
        float healingSpeed,
        float fractureHealSeconds,
        float dislocationHealSeconds,
        boolean infection,
        boolean cardio,
        boolean thirst,
        boolean temperature,
        boolean energy,
        boolean stamina,
        boolean mood,
        boolean drugs) {

    public static final SimSettings DEFAULT = new SimSettings(5f, 1f, 30f * 60f, 12f * 60f,
            true, true, true, true, true, true, true, true);
}
