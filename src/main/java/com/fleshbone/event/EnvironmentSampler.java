package com.fleshbone.event;

import com.fleshbone.attach.BodyRuntime;
import com.fleshbone.config.FBSettings;
import com.fleshbone.data.FBDataMaps;
import com.fleshbone.sim.Environment;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
        env.foodLevel = player.getFoodData().getFoodLevel();
        env.saturation = player.getFoodData().getSaturationLevel();
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
        env.heatSource = runtime.heatSource;
        env.insulation = runtime.insulation;
        env.light = runtime.light;
        env.skyVisible = runtime.skyVisible;
        env.comfort = runtime.comfort;
        env.armorWeight = runtime.armorWeight;
        return env;
    }

    private static void sampleSlow(ServerPlayer player, BodyRuntime runtime) {
        ServerLevel level = player.level();
        BlockPos pos = player.blockPosition();
        runtime.skyVisible = level.canSeeSky(pos.above());
        runtime.light = level.getMaxLocalRawBrightness(pos.above());
        runtime.ambientTemp = airTemperature(level, pos, runtime.skyVisible);
        runtime.heatSource = heatAround(level, pos);
        float insulation = 0f;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            FBDataMaps.Insulation value = stack.getData(FBDataMaps.INSULATION);
            if (value != null) insulation += value.value();
        }
        runtime.insulation = Math.min(0.9f, insulation);
        var armor = player.getAttribute(Attributes.ARMOR);
        runtime.armorWeight = armor == null ? 0f : (float) Math.min(1.0, armor.getValue() / 30.0);
        boolean pet = !level.getEntitiesOfClass(TamableAnimal.class, new AABB(pos).inflate(8),
                animal -> animal.isOwnedBy(player)).isEmpty();
        runtime.comfort = pet || runtime.heatSource >= 6f;
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

    /** Heat radiated by nearby blocks (fires, lava, furnaces...) minus the chill of ice, fading with distance. */
    public static float heatAround(ServerLevel level, BlockPos center) {
        float heat = 0f;
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
                    float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    heat += source.heat() / (1f + distance);
                }
            }
        }
        return Math.max(-15f, Math.min(30f, heat));
    }
}
