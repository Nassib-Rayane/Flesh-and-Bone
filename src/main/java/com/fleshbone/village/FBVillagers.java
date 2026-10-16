package com.fleshbone.village;

import java.util.function.Supplier;

import com.fleshbone.FleshBone;
import com.google.common.collect.ImmutableSet;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The village doctor: a profession, its workstation (the medicine cabinet) and its point of interest. */
public final class FBVillagers {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FleshBone.MODID);
    private static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, FleshBone.MODID);
    private static final DeferredRegister<VillagerProfession> PROFESSIONS = DeferredRegister.create(Registries.VILLAGER_PROFESSION, FleshBone.MODID);

    public static final DeferredBlock<MedicineCabinetBlock> MEDICINE_CABINET = BLOCKS.registerBlock("medicine_cabinet", MedicineCabinetBlock::new,
            p -> p.mapColor(MapColor.SNOW).strength(2f).sound(SoundType.WOOD));

    public static final DeferredHolder<PoiType, PoiType> DOCTOR_POI = POI_TYPES.register("doctor", () ->
            new PoiType(ImmutableSet.copyOf(MEDICINE_CABINET.get().getStateDefinition().getPossibleStates()), 1, 1));

    public static final ResourceKey<PoiType> DOCTOR_POI_KEY = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, FleshBone.id("doctor"));

    public static final DeferredHolder<VillagerProfession, VillagerProfession> DOCTOR = PROFESSIONS.register("doctor", () ->
            new VillagerProfession(Component.translatable("entity.fleshbone.villager.doctor"),
                    poi -> poi.is(DOCTOR_POI_KEY), poi -> poi.is(DOCTOR_POI_KEY),
                    ImmutableSet.of(), ImmutableSet.of(), SoundEvents.VILLAGER_WORK_CLERIC,
                    Int2ObjectMap.ofEntries(
                            Int2ObjectMap.entry(1, tradeSet(1)),
                            Int2ObjectMap.entry(2, tradeSet(2)),
                            Int2ObjectMap.entry(3, tradeSet(3)),
                            Int2ObjectMap.entry(4, tradeSet(4)),
                            Int2ObjectMap.entry(5, tradeSet(5)))));

    private FBVillagers() {}

    private static ResourceKey<TradeSet> tradeSet(int level) {
        return ResourceKey.create(Registries.TRADE_SET, FleshBone.id("doctor/level_" + level));
    }

    public static Supplier<MedicineCabinetBlock> cabinet() {
        return MEDICINE_CABINET;
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        POI_TYPES.register(modBus);
        PROFESSIONS.register(modBus);
    }
}
