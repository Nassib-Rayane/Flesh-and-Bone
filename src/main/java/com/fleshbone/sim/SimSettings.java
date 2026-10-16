package com.fleshbone.sim;

/**
 * Gameplay settings the simulation needs, decoupled from the NeoForge config so the simulation stays pure Java.
 *
 * @param damageScale            injury severity per point of vanilla damage
 * @param healingSpeed           multiplier for natural skin and muscle regeneration
 * @param fractureHealSeconds    time for an untreated fracture to heal
 * @param dislocationHealSeconds time for an untreated dislocation to heal
 * @param hungerSpeed            multiplier for hunger and weight loss (1 = Casualties: Unknown's 2.6 per minute)
 * @param thirstSpeed            multiplier for thirst (1 = 3.5 per minute at rest)
 * @param energySpeed            multiplier for sleepiness and sleep recovery (1 = a full bar lasts about 24 minutes)
 * @param infectionSpeed         multiplier for infection growth (1 = 7.2 % per minute at 100 % immunity)
 * @param lastStand              a dying body may get one last stand
 */
public record SimSettings(
        float damageScale,
        float healingSpeed,
        float fractureHealSeconds,
        float dislocationHealSeconds,
        float hungerSpeed,
        float thirstSpeed,
        float energySpeed,
        float infectionSpeed,
        boolean infection,
        boolean hunger,
        boolean thirst,
        boolean temperature,
        boolean energy,
        boolean stamina,
        boolean mood,
        boolean drugs,
        boolean lastStand) {

    public static final SimSettings DEFAULT = new SimSettings(5f, 1f, 30f * 60f, 12f * 60f, 1f, 1f, 1f, 1f,
            true, true, true, true, true, true, true, true, true);
}
