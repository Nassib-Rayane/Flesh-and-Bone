package com.fleshbone.damage;

import java.util.EnumMap;
import java.util.Map;

import com.fleshbone.FleshBone;
import com.fleshbone.sim.DeathCause;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;

/** One damage type per cause of death (data/fleshbone/damage_type), used to kill the player with the right message. */
public final class FBDamageTypes {
    private static final Map<DeathCause, ResourceKey<DamageType>> KEYS = new EnumMap<>(DeathCause.class);

    static {
        for (DeathCause cause : DeathCause.values()) {
            KEYS.put(cause, ResourceKey.create(Registries.DAMAGE_TYPE, FleshBone.id(cause.id())));
        }
    }

    private FBDamageTypes() {}

    public static ResourceKey<DamageType> key(DeathCause cause) {
        return KEYS.get(cause);
    }

    public static DamageSource source(Level level, DeathCause cause) {
        return level.damageSources().source(key(cause));
    }
}
