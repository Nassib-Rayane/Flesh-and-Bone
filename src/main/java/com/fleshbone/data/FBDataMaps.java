package com.fleshbone.data;

import com.fleshbone.FleshBone;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * Data-driven tuning (data/&lt;namespace&gt;/data_maps/...): modpacks can describe how their own drinks, foods, heat
 * sources and clothing affect the body.
 */
@EventBusSubscriber(modid = FleshBone.MODID)
public final class FBDataMaps {
    /**
     * @param thirst         water restored (negative dries you out)
     * @param sickness       nausea added when it goes wrong
     * @param sicknessChance chance of that happening (untreated water...)
     * @param warmth         °C added to the body (hot soup, cold drinks negative)
     * @param caffeine       stimulant dose
     */
    public record Drink(float thirst, float sickness, float sicknessChance, float warmth, float caffeine) {
        public static final Codec<Drink> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("thirst").forGetter(Drink::thirst),
                Codec.FLOAT.optionalFieldOf("sickness", 0f).forGetter(Drink::sickness),
                Codec.FLOAT.optionalFieldOf("sickness_chance", 0f).forGetter(Drink::sicknessChance),
                Codec.FLOAT.optionalFieldOf("warmth", 0f).forGetter(Drink::warmth),
                Codec.FLOAT.optionalFieldOf("caffeine", 0f).forGetter(Drink::caffeine)
        ).apply(i, Drink::new));
    }

    /**
     * @param sickness       nausea added (negative soothes)
     * @param sicknessChance chance of the nausea
     * @param mood           temporary mood boost (negative for disgusting food)
     * @param thirst         thirst change (dry or salty food is negative)
     */
    public record FoodEffect(float sickness, float sicknessChance, float mood, float thirst) {
        public static final Codec<FoodEffect> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.optionalFieldOf("sickness", 0f).forGetter(FoodEffect::sickness),
                Codec.FLOAT.optionalFieldOf("sickness_chance", 1f).forGetter(FoodEffect::sicknessChance),
                Codec.FLOAT.optionalFieldOf("mood", 0f).forGetter(FoodEffect::mood),
                Codec.FLOAT.optionalFieldOf("thirst", 0f).forGetter(FoodEffect::thirst)
        ).apply(i, FoodEffect::new));
    }

    /** @param heat °C felt one block away (negative for ice); lit/unlit blocks only count when lit */
    public record HeatSource(float heat) {
        public static final Codec<HeatSource> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("heat").forGetter(HeatSource::heat)
        ).apply(i, HeatSource::new));
    }

    /** @param value 0–1 insulation added by wearing the item */
    public record Insulation(float value) {
        public static final Codec<Insulation> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("value").forGetter(Insulation::value)
        ).apply(i, Insulation::new));
    }

    public static final DataMapType<Item, Drink> DRINKS = DataMapType.builder(FleshBone.id("drinks"), Registries.ITEM, Drink.CODEC).build();
    public static final DataMapType<Item, FoodEffect> FOOD_EFFECTS = DataMapType.builder(FleshBone.id("food_effects"), Registries.ITEM, FoodEffect.CODEC).build();
    public static final DataMapType<Block, HeatSource> HEAT_SOURCES = DataMapType.builder(FleshBone.id("heat_sources"), Registries.BLOCK, HeatSource.CODEC).build();
    public static final DataMapType<Item, Insulation> INSULATION = DataMapType.builder(FleshBone.id("insulation"), Registries.ITEM, Insulation.CODEC).build();

    private FBDataMaps() {}

    @SubscribeEvent
    static void register(RegisterDataMapTypesEvent event) {
        event.register(DRINKS);
        event.register(FOOD_EFFECTS);
        event.register(HEAT_SOURCES);
        event.register(INSULATION);
    }
}
