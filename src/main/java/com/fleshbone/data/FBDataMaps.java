package com.fleshbone.data;

import java.util.Optional;

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
 * sources, clothing and items affect the body.
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
     * What a food does to the body. Hunger and weight default to values derived from the vanilla food properties
     * (5 hunger per food point + 1.5 per saturation point, 1 kg per 150 hunger).
     *
     * @param hunger         hunger restored (0–125 scale), if not derived
     * @param weight         kilograms gained, if not derived
     * @param sickness       nausea added (negative soothes)
     * @param sicknessChance chance of the nausea
     * @param mood           lasting mood change (negative for disgusting food)
     * @param thirst         thirst change (dry or salty food is negative)
     */
    public record Nutrition(Optional<Float> hunger, Optional<Float> weight, float sickness, float sicknessChance, float mood, float thirst) {
        public static final Codec<Nutrition> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.optionalFieldOf("hunger").forGetter(Nutrition::hunger),
                Codec.FLOAT.optionalFieldOf("weight").forGetter(Nutrition::weight),
                Codec.FLOAT.optionalFieldOf("sickness", 0f).forGetter(Nutrition::sickness),
                Codec.FLOAT.optionalFieldOf("sickness_chance", 1f).forGetter(Nutrition::sicknessChance),
                Codec.FLOAT.optionalFieldOf("mood", 0f).forGetter(Nutrition::mood),
                Codec.FLOAT.optionalFieldOf("thirst", 0f).forGetter(Nutrition::thirst)
        ).apply(i, Nutrition::new));
    }

    /**
     * A block that radiates heat or cold. Nearby, the felt air temperature is pulled toward {@code temperature} by
     * {@code strength} (0–1, felt one block away; it falls off with distance). Blocks with a {@code lit} state only
     * count while lit.
     */
    public record HeatSource(float temperature, float strength) {
        public static final Codec<HeatSource> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("temperature").forGetter(HeatSource::temperature),
                Codec.FLOAT.fieldOf("strength").forGetter(HeatSource::strength)
        ).apply(i, HeatSource::new));
    }

    /** @param value 0–1 insulation added by wearing the item */
    public record Insulation(float value) {
        public static final Codec<Insulation> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("value").forGetter(Insulation::value)
        ).apply(i, Insulation::new));
    }

    /** @param weight kilograms per item (see {@link ItemWeights} for the defaults of unlisted items) */
    public record ItemWeight(float weight) {
        public static final Codec<ItemWeight> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("weight").forGetter(ItemWeight::weight)
        ).apply(i, ItemWeight::new));
    }

    public static final DataMapType<Item, Drink> DRINKS = DataMapType.builder(FleshBone.id("drinks"), Registries.ITEM, Drink.CODEC).build();
    public static final DataMapType<Item, Nutrition> NUTRITION = DataMapType.builder(FleshBone.id("nutrition"), Registries.ITEM, Nutrition.CODEC).build();
    public static final DataMapType<Block, HeatSource> HEAT_SOURCES = DataMapType.builder(FleshBone.id("heat_sources"), Registries.BLOCK, HeatSource.CODEC).build();
    public static final DataMapType<Item, Insulation> INSULATION = DataMapType.builder(FleshBone.id("insulation"), Registries.ITEM, Insulation.CODEC).build();
    /** Synced so the client shows the same carried weight as the server. */
    public static final DataMapType<Item, ItemWeight> ITEM_WEIGHTS = DataMapType.builder(FleshBone.id("item_weights"), Registries.ITEM, ItemWeight.CODEC)
            .synced(ItemWeight.CODEC, false).build();

    private FBDataMaps() {}

    @SubscribeEvent
    static void register(RegisterDataMapTypesEvent event) {
        event.register(DRINKS);
        event.register(NUTRITION);
        event.register(HEAT_SOURCES);
        event.register(INSULATION);
        event.register(ITEM_WEIGHTS);
    }
}
