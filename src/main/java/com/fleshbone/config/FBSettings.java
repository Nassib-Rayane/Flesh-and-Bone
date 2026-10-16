package com.fleshbone.config;

import com.fleshbone.sim.SimSettings;
import com.fleshbone.sim.system.Encumbrance;

/** Bridges the NeoForge config to the pure-Java {@link SimSettings}. */
public final class FBSettings {
    private FBSettings() {}

    public static SimSettings sim() {
        Encumbrance.baseCapacity = FBConfig.CARRY_CAPACITY.get().floatValue();
        return new SimSettings(
                FBConfig.DAMAGE_SCALE.get().floatValue(),
                FBConfig.HEALING_SPEED.get().floatValue(),
                (float) (FBConfig.FRACTURE_HEAL_MINUTES.get() * 60.0),
                (float) (FBConfig.DISLOCATION_HEAL_MINUTES.get() * 60.0),
                FBConfig.HUNGER_SPEED.get().floatValue(),
                FBConfig.THIRST_SPEED.get().floatValue(),
                FBConfig.ENERGY_SPEED.get().floatValue(),
                FBConfig.INFECTION_SPEED.get().floatValue(),
                FBConfig.INFECTIONS.get(), FBConfig.HUNGER.get(), FBConfig.THIRST.get(), FBConfig.TEMPERATURE.get(),
                FBConfig.ENERGY.get(), FBConfig.STAMINA.get(), FBConfig.MOOD.get(), FBConfig.OPIOID_DEPENDENCE.get(),
                FBConfig.LAST_STAND.get());
    }

    public static float damageScale() {
        return FBConfig.DAMAGE_SCALE.get().floatValue();
    }

    public static float simulationSpeed() {
        return FBConfig.SIMULATION_SPEED.get().floatValue();
    }

    /** C:U hunger replaces the vanilla food bar. */
    public static boolean hunger() {
        return FBConfig.HUNGER.get();
    }

    /** Exact values are shown to everyone (otherwise only through a vitals monitor). */
    public static boolean healthChip() {
        return FBConfig.HEALTH_CHIP.get();
    }
}
