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
import com.fleshbone.sim.system.HungerSystem;
import com.fleshbone.sim.treatment.Treatments;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
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
    /** The vanilla food level held while the C:U hunger runs: below 20 so food can still be eaten. */
    public static final int LOCKED_FOOD = 19;
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
            lockFood(player, body, runtime);
            Environment env = EnvironmentSampler.sample(player, runtime, ENV);
            body.tick(env, FBSettings.sim(), ThreadLocalRandom.current());
            for (BodyEvent bodyEvent : body.drainEvents()) {
                Feedback.onBodyEvent(player, body, bodyEvent);
                switch (bodyEvent.type()) {
                    case VOMITED -> vomit(player, false);
                    case VOMITED_BLOOD -> vomit(player, true);
                    case BRAIN_DROP -> {
                        if (FBConfig.BRAIN_EPISODES.get() && !player.getMainHandItem().isEmpty()) player.drop(true);
                    }
                    default -> { }
                }
                runtime.dirty = true;
            }
            DeathCause cause = body.deathCause();
            if (cause != null) {
                kill(player, cause);
                return;
            }
        } else {
            runtime.foodLocked = false;
        }
        GameplayEffects.apply(player, body, runtime, simulated);
        if (simulated) bleedParticles(player, body);
        sync(player, body, runtime);
    }

    /**
     * Holds the vanilla food bar at 19 (so eating stays possible) while the C:U hunger runs. Food that reached the
     * vanilla bar some other way (cake, other mods) since the last tick is turned into hunger.
     */
    private static void lockFood(ServerPlayer player, Body body, BodyRuntime runtime) {
        if (!FBSettings.hunger()) {
            runtime.foodLocked = false;
            return;
        }
        FoodData food = player.getFoodData();
        if (runtime.foodLocked) {
            float gained = Math.max(0, food.getFoodLevel() - LOCKED_FOOD) * 5f + food.getSaturationLevel() * 1.5f;
            if (gained > 0.01f) {
                Treatments.eat(body, gained, gained * HungerSystem.KG_PER_HUNGER, 0f, 0f, 0f, 0f, ThreadLocalRandom.current());
                runtime.dirty = true;
            }
        }
        food.setFoodLevel(LOCKED_FOOD);
        food.setSaturation(0f);
        runtime.foodLocked = true;
    }

    private static void sync(ServerPlayer player, Body body, BodyRuntime runtime) {
        long now = player.level().getGameTime();
        long since = now - runtime.lastSyncTick;
        boolean active = body.derived.totalBleed() > 0f || body.derived.totalPain > 0.5f || body.vitals.consciousness < 100f
                || body.vitals.bloodVolume < 4.995f || body.needs.stamina < 99.5f || Math.abs(body.vitals.heartRate - 70f) > 3f;
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
        player.level().sendParticles(new DustParticleOptions(0x7A0A0A, 1.1f),
                player.getX(), player.getY() + 0.7, player.getZ(), count, 0.2, 0.35, 0.2, 0.0);
    }

    /** A vomiting fit starts: retching sounds, and what comes up (blood if something bleeds inside). */
    private static void vomit(ServerPlayer player, boolean blood) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1f, 0.6f);
        if (blood) {
            player.level().sendParticles(new DustParticleOptions(0x8A0000, 1.4f),
                    player.getX(), player.getEyeY() - 0.2, player.getZ(), 16, 0.2, 0.1, 0.2, 0.05);
        } else {
            player.level().sendParticles(new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM,
                            net.minecraft.world.item.Items.ROTTEN_FLESH),
                    player.getX(), player.getEyeY() - 0.2, player.getZ(), 12, 0.2, 0.1, 0.2, 0.05);
        }
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

    /** A new body after death, but the skills (and the weight, roughly) carry over if the config says so. */
    @SubscribeEvent
    static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() || !FBConfig.KEEP_SKILLS_ON_DEATH.get()) return;
        Body old = event.getOriginal().getData(FBAttachments.BODY);
        Body fresh = event.getEntity().getData(FBAttachments.BODY);
        fresh.skills.copyFrom(old.skills);
        fresh.derived.compute(fresh);
    }
}
