package com.fleshbone.item;

import java.util.function.Supplier;

import com.fleshbone.FleshBone;
import com.fleshbone.sim.BloodType;
import com.mojang.serialization.Codec;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FBDataComponents {
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, FleshBone.MODID);

    /** Blood group of the donor, carried by a filled blood bag. */
    public static final Supplier<DataComponentType<BloodType>> BLOOD_TYPE = COMPONENTS.registerComponentType("blood_type", builder -> builder
            .persistent(Codec.STRING.xmap(BloodType::valueOf, BloodType::name))
            .networkSynchronized(ByteBufCodecs.idMapper(i -> BloodType.values()[i], BloodType::ordinal)));

    /** Water left in a canteen. */
    public static final Supplier<DataComponentType<Integer>> SIPS = COMPONENTS.registerComponentType("sips", builder -> builder
            .persistent(Codec.intRange(0, 64))
            .networkSynchronized(ByteBufCodecs.VAR_INT));

    private FBDataComponents() {}

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }
}
