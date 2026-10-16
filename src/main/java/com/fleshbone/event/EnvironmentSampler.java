package com.fleshbone.event;

import com.fleshbone.attach.BodyRuntime;
import com.fleshbone.config.FBConfig;
import com.fleshbone.config.FBSettings;
import com.fleshbone.data.FBDataMaps;
import com.fleshbone.data.ItemWeights;
import com.fleshbone.sim.Environment;
import com.fleshbone.sim.system.SleepSystem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.Tags;

/** Reads what the body needs to know about the world around a player. Expensive parts are sampled once a second. */
public final class EnvironmentSampler {
    private static final int SAMPLE_INTERVAL = 20;
    private static final int HEAT_RADIUS = 4;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private EnvironmentSampler() {}

    public static Environment sample(ServerPlayer player, BodyRuntime runtime, Environment env) {
        env.dt = 0.05f * FBSettings.simulationSpeed();
        env.sprinting = player.isSprinting();
        env.swimming = player.isSwimming();
        env.underwater = player.isUnderWater();
        env.inWater = player.isInWater();
        env.exposedToRain = player.level().isRainingAt(player.blockPosition().above());
        env.sleeping = player.isSleeping();
        env.onGround = player.onGround();
        env.crouching = player.isCrouching();
        env.breathingBlocked = player.getAirSupply() <= 0 || runtime.suffocationTicks > 0;
        if (runtime.suffocationTicks > 0) runtime.suffocationTicks--;
        env.exercise = runtime.exercise;

        double x = player.getX();
        double z = player.getZ();
        if (runtime.hasLastPosition) {
            double dx = x - runtime.lastX;
            double dz = z - runtime.lastZ;
            float speed = (float) (Math.sqrt(dx * dx + dz * dz) * 20.0);
            env.moveSpeed = speed > 40f ? 0f : speed;
        }
        runtime.moveSpeed = env.moveSpeed;
        runtime.lastX = x;
        runtime.lastZ = z;
        runtime.hasLastPosition = true;

        long now = player.level().getGameTime();
        if (now - runtime.lastSampleTick >= SAMPLE_INTERVAL || runtime.lastSampleTick < 0) {
            runtime.lastSampleTick = now;
            sampleSlow(player, runtime);
        }
        env.ambientTemp = runtime.ambientTemp;
        env.humidity = runtime.humidity;
        env.waterTemp = runtime.waterTemp;
        env.heatTarget = runtime.heatTarget;
        env.heatStrength = runtime.heatStrength;
        env.insulation = runtime.insulation;
        env.light = runtime.light;
        env.skyVisible = runtime.skyVisible;
        env.comfort = runtime.comfort;
        env.load = runtime.load;
        env.sleepQuality = player.isSleeping() ? SleepSystem.GOOD : runtime.sleepQuality;
        return env;
    }

    private static void sampleSlow(ServerPlayer player, BodyRuntime runtime) {
        ServerLevel level = player.level();
        BlockPos pos = player.blockPosition();
        runtime.skyVisible = level.canSeeSky(pos.above());
        runtime.light = level.getMaxLocalRawBrightness(pos.above());
        runtime.ambientTemp = airTemperature(level, pos, runtime.skyVisible);
        runtime.humidity = humidity(level.getBiome(pos).value());
        runtime.waterTemp = waterTemperature(level, pos, runtime.ambientTemp);
        heatAround(level, pos, runtime);
        float insulation = 0f;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            FBDataMaps.Insulation value = stack.getData(FBDataMaps.INSULATION);
            if (value != null) insulation += value.value();
        }
        runtime.insulation = Math.min(0.9f, insulation);
        runtime.load = FBConfig.ENCUMBRANCE.get() ? ItemWeights.carried(player) : 0f;
        boolean pet = !level.getEntitiesOfClass(TamableAnimal.class, new AABB(pos).inflate(8),
                animal -> animal.isOwnedBy(player)).isEmpty();
        runtime.comfort = pet || (runtime.heatStrength > 0.25f && runtime.heatTarget > 30f) || musicNearby(level, pos);
        runtime.sleepQuality = sleepQuality(player, runtime);
    }

    /**
     * Air temperature in °C from the biome and the time of day, after a curve that reads like real climates: snowy
     * plains −5, taiga 5, meadows 12, forests 15, plains 18, swamps 22, jungles 27, savannas 32, badlands 35, deserts
     * 38 by day. Nights cool down more in dry biomes (a desert drops about 25 °C) than in humid ones; the coolest hour
     * is before dawn, the warmest early afternoon. Direct sun adds 3 °C, rain takes some away, houses keep the air
     * near 18 °C and caves near 12 °C. Altitude cools.
     */
    static float airTemperature(ServerLevel level, BlockPos pos, boolean skyVisible) {
        Holder<Biome> holder = level.getBiome(pos);
        Biome biome = holder.value();
        if (level.dimension() == Level.NETHER) return Math.max(42f, dayTemperature(biome.getBaseTemperature()));
        if (level.dimension() == Level.END) return 5f;
        float t = biome.getBaseTemperature();
        if (pos.getY() > 80) t -= (pos.getY() - 80) * 0.00167f;
        float celsius = dayTemperature(t);
        if (holder.is(Tags.Biomes.IS_DESERT)) celsius += 3f;
        else if (holder.is(Tags.Biomes.IS_SAVANNA)) celsius -= 3f;
        if (holder.is(Tags.Biomes.IS_SWAMP)) celsius += 4f;
        else if (holder.is(Tags.Biomes.IS_JUNGLE)) celsius += 2f;
        float humidity = humidity(biome);
        // Daily cycle: warmest around 14:00 (clock 8000), coolest around 02:00
        long clock = Math.floorMod(level.getOverworldClockTime(), 24000L);
        float warmth = 0.5f + 0.5f * (float) Math.cos(2.0 * Math.PI * (clock - 8000L) / 24000.0);
        float nightDrop = 8f + 17f * (1f - humidity);
        celsius -= nightDrop * (1f - warmth);
        boolean raining = level.isRainingAt(pos.above());
        if (skyVisible && level.isBrightOutside() && !raining) celsius += 3f;
        if (raining) celsius -= biome.coldEnoughToSnow(pos, level.getSeaLevel()) ? 6f : 4f;
        if (!skyVisible) {
            // Sheltered: houses buffer toward 18 °C, caves toward 12 °C
            celsius = pos.getY() < level.getSeaLevel() ? celsius * 0.3f + 12f * 0.7f : celsius * 0.4f + 18f * 0.6f;
        }
        return celsius;
    }

    /** Daytime air temperature for a biome temperature value. */
    static float dayTemperature(float t) {
        if (t <= 0f) return -5f + 20f * t;
        if (t < 0.25f) return -5f + 40f * t;
        if (t < 0.5f) return 5f + 28f * (t - 0.25f);
        if (t < 0.8f) return 12f + 20f * (t - 0.5f);
        if (t < 1f) return 18f + 45f * (t - 0.8f);
        return 27f + 8f * Math.min(1f, t - 1f);
    }

    /** 0–1 humidity from the biome's downfall. */
    static float humidity(Biome biome) {
        return Math.max(0f, Math.min(1f, biome.getModifiedClimateSettings().downfall()));
    }

    /** Water is colder than the air: 4 °C less, 8 °C less in deep or cold oceans, warm oceans are mild. */
    static float waterTemperature(ServerLevel level, BlockPos pos, float air) {
        Holder<Biome> holder = level.getBiome(pos);
        if (holder.is(net.minecraft.world.level.biome.Biomes.WARM_OCEAN)) return Math.max(air - 2f, 24f);
        float water = air - 4f;
        if (holder.is(net.minecraft.tags.BiomeTags.IS_DEEP_OCEAN) || holder.is(Tags.Biomes.IS_COLD)) water -= 4f;
        if (pos.getY() < level.getSeaLevel() - 10) water -= 2f;
        return Math.max(1f, water);
    }

    /** A jukebox playing within 8 blocks lifts the mood. */
    static boolean musicNearby(ServerLevel level, BlockPos center) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -8; dx <= 8; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -8; dz <= 8; dz++) {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState state = level.getBlockState(cursor);
                    if (state.is(net.minecraft.world.level.block.Blocks.JUKEBOX)
                            && state.getValue(net.minecraft.world.level.block.JukeboxBlock.HAS_RECORD)) return true;
                }
            }
        }
        return false;
    }

    /**
     * Heat (or cold) radiated by nearby blocks: each source pulls the felt temperature toward its own, with a strength
     * that fades with distance (×0.5 two blocks away, ×0.2 at four). Strengths add up to at most 0.9.
     */
    public static void heatAround(ServerLevel level, BlockPos center, BodyRuntime runtime) {
        float weight = 0f;
        float weighted = 0f;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -HEAT_RADIUS; dx <= HEAT_RADIUS; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -HEAT_RADIUS; dz <= HEAT_RADIUS; dz++) {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir()) continue;
                    FBDataMaps.HeatSource source = state.getData(FBDataMaps.HEAT_SOURCES);
                    if (source == null) continue;
                    if (state.hasProperty(BlockStateProperties.LIT) && !state.getValue(BlockStateProperties.LIT)) continue;
                    float distance2 = dx * dx + dy * dy + dz * dz;
                    float w = source.strength() / (1f + distance2 / 4f);
                    weight += w;
                    weighted += w * source.temperature();
                }
            }
        }
        runtime.heatStrength = Math.min(0.9f, weight);
        runtime.heatTarget = weight > 0f ? weighted / weight : runtime.ambientTemp;
    }

    /**
     * How well the player would sleep on the ground here: 3 on grass, moss, wool, hay or leaves, 2 on dirt, sand or
     * wood, 1 on stone; one less in the rain, in the cold or soaked; 0 in water or mid-air.
     */
    static int sleepQuality(ServerPlayer player, BodyRuntime runtime) {
        if (player.isInWater() || !player.onGround()) return 0;
        ServerLevel level = player.level();
        BlockPos below = player.blockPosition().below();
        BlockState ground = level.getBlockState(below);
        BlockState at = level.getBlockState(player.blockPosition());
        int quality;
        if (ground.is(BlockTags.WOOL) || ground.is(BlockTags.WOOL_CARPETS) || at.is(BlockTags.WOOL_CARPETS)
                || ground.is(net.minecraft.world.level.block.Blocks.HAY_BLOCK) || ground.is(BlockTags.LEAVES)
                || ground.is(net.minecraft.world.level.block.Blocks.MOSS_BLOCK) || ground.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)) {
            quality = SleepSystem.OKAY;
        } else if (ground.is(BlockTags.DIRT) || ground.is(BlockTags.SAND) || ground.is(BlockTags.PLANKS) || ground.is(BlockTags.LOGS)
                || ground.is(BlockTags.SNOW)) {
            quality = SleepSystem.MEDIOCRE;
        } else {
            quality = SleepSystem.BAD;
        }
        if (level.isRainingAt(player.blockPosition().above())) quality--;
        float felt = runtime.ambientTemp + (runtime.heatTarget - runtime.ambientTemp) * runtime.heatStrength;
        if (felt < 5f || felt > 35f) quality--;
        return Math.max(SleepSystem.BAD, quality);
    }
}
