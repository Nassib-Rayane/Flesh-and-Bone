package com.fleshbone.event;

import com.fleshbone.attach.BodyRuntime;
import com.fleshbone.config.FBConfig;
import com.fleshbone.config.FBSettings;
import com.fleshbone.data.FBDataMaps;
import com.fleshbone.data.ItemWeights;
import com.fleshbone.sim.Environment;
import com.fleshbone.sim.system.SleepSystem;

import net.minecraft.core.BlockPos;
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
        runtime.lastX = x;
        runtime.lastZ = z;
        runtime.hasLastPosition = true;

        long now = player.level().getGameTime();
        if (now - runtime.lastSampleTick >= SAMPLE_INTERVAL || runtime.lastSampleTick < 0) {
            runtime.lastSampleTick = now;
            sampleSlow(player, runtime);
        }
        env.ambientTemp = runtime.ambientTemp;
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
        runtime.comfort = pet || (runtime.heatStrength > 0.25f && runtime.heatTarget > 30f);
        runtime.sleepQuality = sleepQuality(player, runtime);
    }

    /**
     * Felt air temperature in °C from the biome (adjusted for altitude), day and night, rain and depth.
     * Plains feel about 18 °C, deserts 36 °C by day, snowy plains below freezing; caves tend to 12 °C.
     */
    static float airTemperature(ServerLevel level, BlockPos pos, boolean skyVisible) {
        Biome biome = level.getBiome(pos).value();
        float t = biome.getBaseTemperature();
        if (pos.getY() > 80) t -= (pos.getY() - 80) * 0.00167f;
        float celsius = t <= 1f ? 30f * t - 6f : 24f + 12f * (t - 1f);
        if (level.dimension() == Level.NETHER) {
            celsius = Math.max(celsius, 42f);
        } else if (level.dimension() == Level.END) {
            celsius = 5f;
        } else {
            if (!level.isBrightOutside()) celsius -= t > 1.5f ? 14f : 7f;
            else celsius += 3f;
            if (level.isRainingAt(pos.above())) celsius -= biome.coldEnoughToSnow(pos, level.getSeaLevel()) ? 6f : 4f;
            if (!skyVisible && pos.getY() < level.getSeaLevel()) celsius = celsius * 0.3f + 12f * 0.7f;
        }
        return celsius;
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
