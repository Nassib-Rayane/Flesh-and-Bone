package com.fleshbone.effect;

import com.fleshbone.FleshBone;
import com.fleshbone.attach.BodyRuntime;
import com.fleshbone.attach.FBAttachments;
import com.fleshbone.event.BodyTicker;
import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.BodyPart.Side;
import com.fleshbone.sim.Capabilities;
import com.fleshbone.sim.Environment;
import com.fleshbone.sim.PartState;
import com.fleshbone.sim.Skills;
import com.fleshbone.sim.system.HygieneSystem;
import com.fleshbone.sim.system.PainSystem;
import com.fleshbone.sim.system.SkillSystem;
import com.fleshbone.sim.system.SleepSystem;
import com.fleshbone.sim.system.StaminaSystem;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * Turns the body's state into vanilla consequences: attribute modifiers, sprinting, unusable hands, lying down,
 * sleeping on the ground and workouts; and feeds back what the player does: effort, strain, experience.
 */
@EventBusSubscriber(modid = FleshBone.MODID)
public final class GameplayEffects {
    private static final Identifier MOVE = FleshBone.id("body_movement");
    private static final Identifier JUMP = FleshBone.id("body_jump");
    private static final Identifier ATTACK = FleshBone.id("body_attack_damage");
    private static final Identifier ATTACK_SPEED = FleshBone.id("body_attack_speed");
    private static final Identifier MINING = FleshBone.id("body_mining");
    private static final Identifier SAFE_FALL = FleshBone.id("body_safe_fall");
    /** Stamina below which a workout stops by itself. */
    public static final float EXERCISE_MIN_STAMINA = 5f;

    private GameplayEffects() {}

    public static Side mainSide(Player player) {
        return player.getMainArm() == HumanoidArm.LEFT ? Side.LEFT : Side.RIGHT;
    }

    public static Capabilities capabilities(Player player) {
        if (!BodyTicker.simulated(player)) return Capabilities.HEALTHY;
        return Capabilities.compute(player.getData(FBAttachments.BODY), mainSide(player));
    }

    public static void apply(ServerPlayer player, Body body, BodyRuntime runtime, boolean simulated) {
        Capabilities caps = simulated ? Capabilities.compute(body, mainSide(player)) : Capabilities.HEALTHY;
        runtime.appliedMove = multiplier(player, Attributes.MOVEMENT_SPEED, MOVE, caps.move(), runtime.appliedMove);
        runtime.appliedJump = multiplier(player, Attributes.JUMP_STRENGTH, JUMP, caps.jump(), runtime.appliedJump);
        runtime.appliedAttack = multiplier(player, Attributes.ATTACK_DAMAGE, ATTACK, caps.attackDamage(), runtime.appliedAttack);
        runtime.appliedAttackSpeed = multiplier(player, Attributes.ATTACK_SPEED, ATTACK_SPEED, caps.attackSpeed(), runtime.appliedAttackSpeed);
        runtime.appliedMining = multiplier(player, Attributes.BLOCK_BREAK_SPEED, MINING, caps.mining(), runtime.appliedMining);
        if (Math.abs(caps.safeFallDelta() - runtime.appliedSafeFall) > 0.01f) {
            AttributeInstance instance = player.getAttribute(Attributes.SAFE_FALL_DISTANCE);
            if (instance != null) {
                if (caps.safeFallDelta() == 0f) instance.removeModifier(SAFE_FALL);
                else instance.addOrUpdateTransientModifier(new AttributeModifier(SAFE_FALL, caps.safeFallDelta(), AttributeModifier.Operation.ADD_VALUE));
            }
            runtime.appliedSafeFall = caps.safeFallDelta();
        }
        if (!caps.canSprint() && player.isSprinting()) player.setSprinting(false);
        if (simulated) {
            checkExercise(player, body, runtime, caps);
            scratchyPlants(player, body, runtime);
            if (body.part(BodyPart.THORAX).isDislocated() && runtime.moveSpeed > 0.8f) {
                // A dislocated spine hurts with every step
                body.part(BodyPart.THORAX).pain = Math.min(150f, body.part(BodyPart.THORAX).pain + 0.08f);
            }
        } else {
            runtime.exercise = Environment.Exercise.NONE;
            body.needs.exercise = 0;
            body.needs.asleep = false;
        }

        Pose pose = null;
        if (caps.incapacitated() || runtime.exercise == Environment.Exercise.PUSHUPS || runtime.exercise == Environment.Exercise.PLANK) {
            pose = Pose.SWIMMING;
        } else if (runtime.exercise == Environment.Exercise.SQUATS) {
            pose = (player.tickCount / 15) % 2 == 0 ? Pose.CROUCHING : Pose.STANDING;
        }
        if (pose != null && !player.isSleeping()) {
            if (player.getForcedPose() != pose) player.setForcedPose(pose);
            runtime.forcedPose = true;
        } else if (runtime.forcedPose) {
            player.setForcedPose(null);
            runtime.forcedPose = false;
        }
    }

    /** A workout stops when the body moves, falls, runs out of stamina or cannot go on. */
    private static void checkExercise(ServerPlayer player, Body body, BodyRuntime runtime, Capabilities caps) {
        if (runtime.exercise == Environment.Exercise.NONE) return;
        String reason = null;
        if (caps.incapacitated() || body.needs.asleep) reason = "fleshbone.message.exercise.stopped";
        else if (body.needs.stamina < EXERCISE_MIN_STAMINA) reason = "fleshbone.message.exercise.exhausted";
        else if (!player.onGround() || player.isInWater() || runtime.moveSpeed > 1f) {
            reason = "fleshbone.message.exercise.stopped";
        }
        if (reason != null) {
            runtime.exercise = Environment.Exercise.NONE;
            player.sendOverlayMessage(Component.translatable(reason));
            runtime.dirty = true;
        }
    }

    /** Starts (or stops, with {@link Environment.Exercise#NONE}) a workout asked from the health panel. */
    public static void startExercise(ServerPlayer player, Environment.Exercise exercise) {
        BodyRuntime runtime = player.getData(FBAttachments.RUNTIME);
        Body body = player.getData(FBAttachments.BODY);
        if (exercise == Environment.Exercise.NONE || exercise == runtime.exercise) {
            runtime.exercise = Environment.Exercise.NONE;
            return;
        }
        if (!BodyTicker.simulated(player) || body.isIncapacitated() || body.needs.asleep) return;
        if (!player.onGround() || player.isInWater()) {
            player.sendOverlayMessage(Component.translatable("fleshbone.message.exercise.need_ground"));
            return;
        }
        if (body.needs.stamina < 30f) {
            player.sendOverlayMessage(Component.translatable("fleshbone.message.exercise.too_tired"));
            return;
        }
        runtime.exercise = exercise;
        runtime.dirty = true;
    }

    /** Lies down to sleep where the player stands, if the body agrees (Casualties: Unknown's sleep button). */
    public static void trySleep(ServerPlayer player) {
        Body body = player.getData(FBAttachments.BODY);
        BodyRuntime runtime = player.getData(FBAttachments.RUNTIME);
        if (!BodyTicker.simulated(player) || player.isSleeping()) return;
        if (body.needs.asleep) {
            SleepSystem.wake(body, SleepSystem.WakeReason.VOLUNTARY);
            runtime.dirty = true;
            return;
        }
        if (monstersNearby(player, player.blockPosition())) {
            player.sendOverlayMessage(Component.translatable("fleshbone.message.sleep.monsters"));
            return;
        }
        SleepSystem.Refusal refusal = SleepSystem.refusal(body, runtime.sleepQuality);
        if (refusal != null) {
            player.sendOverlayMessage(Component.translatable("fleshbone.message.sleep." + refusal.key));
            return;
        }
        runtime.exercise = Environment.Exercise.NONE;
        SleepSystem.fallAsleep(body, runtime.sleepQuality);
        runtime.dirty = true;
    }

    /** Applies {@code value} as a total multiplier through a transient modifier; returns the applied value. */
    private static float multiplier(Player player, Holder<Attribute> attribute, Identifier id, float value, float applied) {
        if (Math.abs(value - applied) < 0.005f) return applied;
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return value;
        if (Math.abs(value - 1f) < 0.005f) instance.removeModifier(id);
        else instance.addOrUpdateTransientModifier(new AttributeModifier(id, value - 1f, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        return value;
    }

    // ------------------------------------------------------------------ restrictions (both sides: the body is synced)

    private static boolean handBlocked(Player player, InteractionHand hand) {
        if (!BodyTicker.simulated(player)) return false;
        Capabilities caps = capabilities(player);
        if (caps.incapacitated()) return true;
        return hand == InteractionHand.MAIN_HAND ? !caps.mainHandUsable() : !caps.offHandUsable();
    }

    private static void block(Player player, InteractionHand hand, ICancellableEvent event) {
        if (!handBlocked(player, hand)) return;
        event.setCanceled(true);
        if (player instanceof ServerPlayer serverPlayer && !capabilities(player).incapacitated()) {
            serverPlayer.sendOverlayMessage(Component.translatable("fleshbone.message.hand_unusable"));
        }
    }

    @SubscribeEvent
    static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        block(event.getEntity(), event.getHand(), event);
    }

    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        block(event.getEntity(), event.getHand(), event);
    }

    @SubscribeEvent
    static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        block(event.getEntity(), event.getHand(), event);
    }

    @SubscribeEvent
    static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        block(event.getEntity(), event.getHand(), event);
    }

    /**
     * Swinging costs stamina (1 % bare-handed, 3 % with a weapon or tool, as in Casualties: Unknown). A broken arm,
     * cracked ribs or a broken neck hurt with every blow. You cannot fight lying on the ground.
     */
    @SubscribeEvent
    static void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (!BodyTicker.simulated(player)) return;
        if (capabilities(player).incapacitated()) {
            event.setCanceled(true);
            return;
        }
        if (player instanceof ServerPlayer) {
            strain(player, 8f);
            Body body = player.getData(FBAttachments.BODY);
            StaminaSystem.exert(body, player.getMainHandItem().isEmpty() ? StaminaSystem.PUNCH_COST : StaminaSystem.SWING_COST);
        }
    }

    /** Blows landed train strength (damage / 3 XP); kills harden (resilience). */
    @SubscribeEvent
    static void onDamageDealt(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || event.getEntity() == player || !BodyTicker.simulated(player)) return;
        if (event.getSource().getDirectEntity() != player) return;
        SkillSystem.gain(player.getData(FBAttachments.BODY), Skills.Stat.STRENGTH, Math.min(10f, event.getOriginalDamage()) / 3f);
    }

    @SubscribeEvent
    static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || event.getEntity() == player || !BodyTicker.simulated(player)) return;
        if (event.getEntity() instanceof net.minecraft.world.entity.monster.Enemy) {
            SkillSystem.gain(player.getData(FBAttachments.BODY), Skills.Stat.RESILIENCE, 2f);
        }
    }

    /**
     * Mining is slower when weak; pounding stone, ore or metal with a bare fist hurts the hand: a dull ache while
     * mining, a bruise every few seconds of it.
     */
    @SubscribeEvent
    static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (!BodyTicker.simulated(player)) return;
        if (capabilities(player).incapacitated()) {
            event.setNewSpeed(0f);
            return;
        }
        if (player instanceof ServerPlayer server && player.getMainHandItem().isEmpty()
                && event.getState().is(com.fleshbone.damage.FBTags.Blocks.HURTS_BARE_HANDS)) {
            BodyRuntime runtime = server.getData(FBAttachments.RUNTIME);
            long now = server.level().getGameTime();
            if (now != runtime.lastPunchTick) {
                runtime.lastPunchTick = now;
                Body body = server.getData(FBAttachments.BODY);
                BodyPart hand = mainSide(player) == Side.LEFT ? BodyPart.HAND_L : BodyPart.HAND_R;
                PartState s = body.part(hand);
                s.pain = Math.min(150f, s.pain + 0.04f);
                if (++runtime.punchTicks >= 60) {
                    runtime.punchTicks = 0;
                    body.injure(com.fleshbone.sim.injury.Injury.of(hand, com.fleshbone.sim.injury.InjuryKind.BLUNT, 3f),
                            java.util.concurrent.ThreadLocalRandom.current());
                    runtime.dirty = true;
                }
            }
        }
    }

    @SubscribeEvent
    static void onBlockBroken(BreakBlockEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !BodyTicker.simulated(player)) return;
        strain(player, 4f);
        Body body = player.getData(FBAttachments.BODY);
        StaminaSystem.exert(body, StaminaSystem.MINE_COST);
        SkillSystem.gain(body, Skills.Stat.STRENGTH, 0.1f);
        // Digging soil, sand and gravel gets you dirty
        if (event.getState().is(net.minecraft.tags.BlockTags.MINEABLE_WITH_SHOVEL)) HygieneSystem.soil(body, 1f);
        if (player.getMainHandItem().isEmpty()) {
            BodyPart hand = mainSide(player) == Side.LEFT ? BodyPart.HAND_L : BodyPart.HAND_R;
            java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();
            if (event.getState().is(com.fleshbone.damage.FBTags.Blocks.SHARP_WHEN_BROKEN)) {
                // Glass shatters into the fist: a cut, and sometimes shards stuck in it
                body.injure(new com.fleshbone.sim.injury.Injury(hand, com.fleshbone.sim.injury.InjuryKind.CUT, 9f + rng.nextFloat() * 7f, 5f, 0, 0f), rng);
                if (rng.nextFloat() < 0.35f) {
                    body.part(hand).shrapnel = Math.min(9, body.part(hand).shrapnel + 1);
                    body.emit(com.fleshbone.sim.BodyEvent.Type.SHRAPNEL, hand);
                }
                player.sendOverlayMessage(Component.translatable("fleshbone.message.glass_cut"));
            } else if (event.getState().is(com.fleshbone.damage.FBTags.Blocks.HURTS_BARE_HANDS)) {
                body.injure(com.fleshbone.sim.injury.Injury.of(hand, com.fleshbone.sim.injury.InjuryKind.BLUNT, 4f), rng);
            }
            player.getData(FBAttachments.RUNTIME).dirty = true;
        }
    }

    /** Wading through dry bushes and roses scratches the legs, unless leggings or boots cover them. */
    static void scratchyPlants(ServerPlayer player, Body body, BodyRuntime runtime) {
        if (++runtime.scratchTimer % 10 != 0 || runtime.moveSpeed < 1f) return;
        net.minecraft.world.level.block.state.BlockState feet = player.level().getBlockState(player.blockPosition());
        if (!feet.is(com.fleshbone.damage.FBTags.Blocks.SCRATCHY_PLANTS)) return;
        java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();
        boolean sprinting = player.isSprinting();
        if (rng.nextFloat() > (sprinting ? 0.6f : 0.3f)) return;
        BodyPart part = rng.nextBoolean() ? (rng.nextBoolean() ? BodyPart.SHIN_L : BodyPart.SHIN_R) : (rng.nextBoolean() ? BodyPart.THIGH_L : BodyPart.THIGH_R);
        for (net.minecraft.world.entity.EquipmentSlot slot : com.fleshbone.damage.PartArmor.coveringSlots(part)) {
            if (!player.getItemBySlot(slot).isEmpty()) return;
        }
        body.injure(new com.fleshbone.sim.injury.Injury(part, com.fleshbone.sim.injury.InjuryKind.CUT, sprinting ? 4f : 2.5f, 6f, 0, 0f), rng);
        player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.3f);
        player.sendOverlayMessage(Component.translatable("fleshbone.message.scratched"));
        runtime.dirty = true;
    }

    /** Washing at a water cauldron (empty hand), or pouring a water bucket over yourself (sneaking). */
    @SubscribeEvent
    static void onUseBlockToWash(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !BodyTicker.simulated(player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty()) return;
        net.minecraft.world.level.block.state.BlockState state = player.level().getBlockState(event.getPos());
        if (!state.is(net.minecraft.world.level.block.Blocks.WATER_CAULDRON)) return;
        Body body = player.getData(FBAttachments.BODY);
        HygieneSystem.wash(body, 40f);
        body.needs.wetness = Math.min(100f, body.needs.wetness + 10f);
        net.minecraft.world.level.block.LayeredCauldronBlock.lowerFillLevel(state, player.level(), event.getPos());
        player.level().playSound(null, event.getPos(), net.minecraft.sounds.SoundEvents.GENERIC_SPLASH, net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.2f);
        player.sendOverlayMessage(Component.translatable("fleshbone.message.washed"));
        player.getData(FBAttachments.RUNTIME).dirty = true;
        event.setCanceled(true);
    }

    @SubscribeEvent
    static void onDouse(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !BodyTicker.simulated(player) || !player.isCrouching()) return;
        net.minecraft.world.item.ItemStack stack = event.getItemStack();
        if (!stack.is(net.minecraft.world.item.Items.WATER_BUCKET)) return;
        Body body = player.getData(FBAttachments.BODY);
        HygieneSystem.wash(body, 30f);
        body.needs.wetness = Math.min(100f, body.needs.wetness + 70f);
        body.vitals.bodyTemp -= 0.4f;
        player.clearFire();
        if (!player.getAbilities().instabuild) player.setItemInHand(event.getHand(), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BUCKET));
        player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.GENERIC_SPLASH, net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1f);
        player.sendOverlayMessage(Component.translatable("fleshbone.message.doused"));
        player.getData(FBAttachments.RUNTIME).dirty = true;
        event.setCanceled(true);
    }

    /** Effort hurts what is broken: the working arm, the ribs or spine, and a broken neck (C:U: +3 per blow). */
    private static void strain(Player player, float pain) {
        Body body = player.getData(FBAttachments.BODY);
        for (BodyPart part : BodyPart.arm(mainSide(player))) {
            PartState s = body.part(part);
            if (s.isFractured()) s.pain += s.splinted ? pain * 0.4f : pain;
            else if (s.isDislocated()) s.pain += pain * 0.6f;
        }
        PartState thorax = body.part(BodyPart.THORAX);
        if (thorax.isFractured()) thorax.pain += 3f;
        if (thorax.isDislocated()) thorax.pain += 4f;
        PartState head = body.part(BodyPart.HEAD);
        if (head.isFractured()) head.pain += 3f;
    }

    /** Jumping costs 1 % stamina (more when overloaded); landing on a broken leg or neck hurts. */
    @SubscribeEvent
    static void onJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !BodyTicker.simulated(player)) return;
        Body body = player.getData(FBAttachments.BODY);
        StaminaSystem.exert(body, StaminaSystem.JUMP_COST);
        for (BodyPart part : BodyPart.VALUES) {
            if (!part.isLeg()) continue;
            PartState s = body.part(part);
            if (s.isFractured()) s.pain += s.splinted ? 3f : 8f;
            else if (s.isDislocated()) s.pain += 5f;
        }
        if (body.part(BodyPart.THORAX).isFractured()) body.part(BodyPart.THORAX).pain += 3f;
        if (body.part(BodyPart.HEAD).isFractured()) body.part(BodyPart.HEAD).pain += 3f;
    }

    /** Energy below which a bed can be used for a nap during the day. */
    private static final float NAP_ENERGY = 60f;

    @SubscribeEvent
    static void onSleep(CanPlayerSleepEvent event) {
        ServerPlayer player = event.getEntity();
        if (!BodyTicker.simulated(player)) return;
        Body body = player.getData(FBAttachments.BODY);
        Player.BedSleepingProblem problem = event.getProblem();
        if (problem == null || isDaytimeRule(player, problem)) {
            if (body.derived.totalPain > PainSystem.NO_SLEEP) {
                event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
                player.sendOverlayMessage(Component.translatable("fleshbone.message.too_much_pain_to_sleep"));
                return;
            }
            if (body.needs.sickness > SleepSystem.MAX_SICKNESS) {
                event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
                player.sendOverlayMessage(Component.translatable("fleshbone.message.sleep.too_sick"));
                return;
            }
        }
        // A tired body can nap during the day (vanilla only refuses because of the time of day)
        if (problem != null && isDaytimeRule(player, problem) && body.needs.energy < NAP_ENERGY) {
            if (monstersNearby(player, event.getPos())) {
                event.setProblem(Player.BedSleepingProblem.NOT_SAFE);
            } else {
                event.setProblem(null);
            }
        }
    }

    /** Vanilla's "you can only sleep at night" refusal, as opposed to an obstructed, distant or unsafe bed. */
    private static boolean isDaytimeRule(ServerPlayer player, Player.BedSleepingProblem problem) {
        return problem != Player.BedSleepingProblem.TOO_FAR_AWAY && problem != Player.BedSleepingProblem.OBSTRUCTED
                && problem != Player.BedSleepingProblem.NOT_SAFE && problem != Player.BedSleepingProblem.OTHER_PROBLEM
                && player.level().dimension() == net.minecraft.world.level.Level.OVERWORLD && player.level().isBrightOutside();
    }

    private static boolean monstersNearby(ServerPlayer player, net.minecraft.core.BlockPos pos) {
        net.minecraft.world.phys.Vec3 center = net.minecraft.world.phys.Vec3.atBottomCenterOf(pos);
        return !player.level().getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class,
                new net.minecraft.world.phys.AABB(center.x - 8, center.y - 5, center.z - 8, center.x + 8, center.y + 5, center.z + 8),
                monster -> monster.isPreventingPlayerRest(player.level(), player)).isEmpty();
    }

    /**
     * In a bed, the body is woken by the same things as on the ground (pain, cold, nausea...); a daytime nap goes on
     * until rested (vanilla would wake the player at once).
     */
    @SubscribeEvent
    static void onContinueSleeping(net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !BodyTicker.simulated(player)) return;
        Body body = player.getData(FBAttachments.BODY);
        SleepSystem.WakeReason reason = SleepSystem.wakeReason(body, null);
        if (reason != null && reason != SleepSystem.WakeReason.RESTED) {
            event.setContinueSleeping(false);
            return;
        }
        if (event.mayContinueSleeping() || player.level().dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
        if (player.level().isBrightOutside() && body.needs.energy < 98f) event.setContinueSleeping(true);
    }
}
