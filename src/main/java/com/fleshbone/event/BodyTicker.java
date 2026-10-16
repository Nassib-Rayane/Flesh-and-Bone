package com.fleshbone.event;

import java.util.concurrent.ThreadLocalRandom;

import com.fleshbone.FleshBone;
import com.fleshbone.attach.BodyRuntime;
import com.fleshbone.attach.FBAttachments;
import com.fleshbone.config.FBConfig;
import com.fleshbone.config.FBSettings;
import com.fleshbone.damage.FBDamageTypes;
import com.fleshbone.effect.GameplayEffects;
import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyEvent;
import com.fleshbone.sim.DeathCause;
import com.fleshbone.sim.Environment;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Runs every player's body once per server tick, applies its consequences and keeps the client in sync. */
@EventBusSubscriber(modid = FleshBone.MODID)
public final class BodyTicker {
    /** Ticks between two syncs while the body is changing (4 per second). */
    private static final int SYNC_INTERVAL = 5;
    /** Ticks between two syncs while nothing notable happens. */
    private static final int IDLE_SYNC_INTERVAL = 40;
    private static final Environment ENV = new Environment();

    private BodyTicker() {}

    /** Whether this player's body is simulated (not paused for creative or spectator). */
    public static boolean simulated(Player player) {
        return !(FBConfig.PAUSE_IN_CREATIVE.get() && (player.isCreative() || player.isSpectator()));
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) tickPlayer(player);
    }

    /** One simulation step for one player: body, consequences, death, sync. */
    public static void tickPlayer(ServerPlayer player) {
        if (!player.isAlive()) return;
        Body body = player.getData(FBAttachments.BODY);
        BodyRuntime runtime = player.getData(FBAttachments.RUNTIME);
        boolean simulated = simulated(player);

        if (simulated) {
            Environment env = EnvironmentSampler.sample(player, runtime, ENV);
            if (env.foodLevel <= 0) body.needs.starvation += env.dt;
            else body.needs.starvation = Math.max(0f, body.needs.starvation - env.dt * 5f);

            body.tick(env, FBSettings.sim(), ThreadLocalRandom.current());
            if (body.exhaustionDebt > 0f) {
                player.causeFoodExhaustion(body.exhaustionDebt);
                body.exhaustionDebt = 0f;
            }
            for (BodyEvent bodyEvent : body.drainEvents()) {
                Feedback.onBodyEvent(player, body, bodyEvent);
                if (bodyEvent.type() == BodyEvent.Type.VOMITED) vomit(player);
                runtime.dirty = true;
            }
            DeathCause cause = body.deathCause();
            if (cause != null) {
                kill(player, cause);
                return;
            }
        }
        GameplayEffects.apply(player, body, runtime, simulated);
        if (simulated) bleedParticles(player, body);
        sync(player, body, runtime);
    }

    private static void sync(ServerPlayer player, Body body, BodyRuntime runtime) {
        long now = player.level().getGameTime();
        long since = now - runtime.lastSyncTick;
        boolean active = body.derived.totalBleed() > 0f || body.derived.totalPain > 0.5f || body.vitals.consciousness < 100f
                || body.vitals.bloodVolume < 4.995f;
        int interval = runtime.dirty ? 2 : active ? SYNC_INTERVAL : IDLE_SYNC_INTERVAL;
        if (since >= interval) {
            player.syncData(FBAttachments.BODY);
            runtime.lastSyncTick = now;
            runtime.dirty = false;
        }
    }

    /** Blood drips from bleeding players, visible to everyone around. */
    private static void bleedParticles(ServerPlayer player, Body body) {
        float rate = body.derived.externalBleed;
        if (rate < 0.01f || player.tickCount % 10 != 0) return;
        int count = Math.min(8, 1 + (int) (rate * 15f));
        player.level().sendParticles(new net.minecraft.core.particles.DustParticleOptions(0x7A0A0A, 1.1f),
                player.getX(), player.getY() + 0.7, player.getZ(), count, 0.2, 0.35, 0.2, 0.0);
    }

    /** The stomach empties: food is lost along with the water the body already accounted for. */
    private static void vomit(ServerPlayer player) {
        var food = player.getFoodData();
        food.setFoodLevel(Math.max(0, food.getFoodLevel() - 4));
        food.setSaturation(0f);
        player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.SLIME_SQUISH,
                net.minecraft.sounds.SoundSource.PLAYERS, 1f, 0.6f);
        player.level().sendParticles(new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM,
                        net.minecraft.world.item.Items.ROTTEN_FLESH),
                player.getX(), player.getEyeY() - 0.2, player.getZ(), 12, 0.2, 0.1, 0.2, 0.05);
    }

    public static void kill(ServerPlayer player, DeathCause cause) {
        player.hurtServer(player.level(), FBDamageTypes.source(player.level(), cause), Float.MAX_VALUE);
    }

    /** Send the full body right away when the player (re)appears. */
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) player.syncData(FBAttachments.BODY);
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) player.syncData(FBAttachments.BODY);
    }

    @SubscribeEvent
    static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) player.syncData(FBAttachments.BODY);
    }
}
