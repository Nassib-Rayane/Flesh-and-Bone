package com.fleshbone.config;

import com.fleshbone.sim.SimSettings;

/** Bridges the NeoForge config to the pure-Java {@link SimSettings}. */
public final class FBSettings {
    private FBSettings() {}

    public static SimSettings sim() {
        return new SimSettings(
                FBConfig.DAMAGE_SCALE.get().floatValue(),
                FBConfig.HEALING_SPEED.get().floatValue(),
                (float) (FBConfig.FRACTURE_HEAL_MINUTES.get() * 60.0),
                (float) (FBConfig.DISLOCATION_HEAL_MINUTES.get() * 60.0),
                FBConfig.INFECTIONS.get(), true, FBConfig.THIRST.get(), FBConfig.TEMPERATURE.get(), FBConfig.ENERGY.get(),
                FBConfig.STAMINA.get(), FBConfig.MOOD.get(), FBConfig.OPIOID_DEPENDENCE.get());
    }

    public static float damageScale() {
        return FBConfig.DAMAGE_SCALE.get().floatValue();
    }

    public static float simulationSpeed() {
        return FBConfig.SIMULATION_SPEED.get().floatValue();
    }
}
