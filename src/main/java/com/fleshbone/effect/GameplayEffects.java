package com.fleshbone.effect;

import com.fleshbone.FleshBone;
import com.fleshbone.attach.BodyRuntime;
import com.fleshbone.attach.FBAttachments;
import com.fleshbone.event.BodyTicker;
import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.BodyPart.Side;
import com.fleshbone.sim.Capabilities;
import com.fleshbone.sim.PartState;
import com.fleshbone.sim.system.PainSystem;

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
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/** Turns the body's state into vanilla consequences: attribute modifiers, sprinting, unusable hands, lying down. */
@EventBusSubscriber(modid = FleshBone.MODID)
public final class GameplayEffects {
    private static final Identifier MOVE = FleshBone.id("body_movement");
    private static final Identifier JUMP = FleshBone.id("body_jump");
    private static final Identifier ATTACK = FleshBone.id("body_attack_damage");
    private static final Identifier ATTACK_SPEED = FleshBone.id("body_attack_speed");
    private static final Identifier MINING = FleshBone.id("body_mining");
    private static final Identifier SAFE_FALL = FleshBone.id("body_safe_fall");

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

        if (caps.incapacitated()) {
            if (player.getForcedPose() != Pose.SWIMMING) player.setForcedPose(Pose.SWIMMING);
            runtime.forcedPose = true;
            if (player.isSleeping()) return;
        } else if (runtime.forcedPose) {
            player.setForcedPose(null);
            runtime.forcedPose = false;
        }
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

    /** Hitting with a broken arm or cracked ribs hurts; you cannot fight lying on the ground. */
    @SubscribeEvent
    static void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (!BodyTicker.simulated(player)) return;
        if (capabilities(player).incapacitated()) {
            event.setCanceled(true);
            return;
        }
        if (player instanceof ServerPlayer) {
            strainArm(player, 8f);
            com.fleshbone.sim.system.MetabolismSystem.exert(player.getData(FBAttachments.BODY), 2.5f);
        }
    }

    @SubscribeEvent
    static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (BodyTicker.simulated(event.getEntity()) && capabilities(event.getEntity()).incapacitated()) event.setNewSpeed(0f);
    }

    @SubscribeEvent
    static void onBlockBroken(BreakBlockEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !BodyTicker.simulated(player)) return;
        strainArm(player, 4f);
        com.fleshbone.sim.system.MetabolismSystem.exert(player.getData(FBAttachments.BODY), 0.4f);
        // Digging soil, sand and gravel gets you dirty
        if (event.getState().is(net.minecraft.tags.BlockTags.MINEABLE_WITH_SHOVEL)) {
            com.fleshbone.sim.system.HygieneSystem.soil(player.getData(FBAttachments.BODY), 1.5f);
        }
    }

    private static void strainArm(Player player, float pain) {
        Body body = player.getData(FBAttachments.BODY);
        for (BodyPart part : BodyPart.arm(mainSide(player))) {
            PartState s = body.part(part);
            if (s.isFractured()) s.pain += s.splinted ? pain * 0.4f : pain;
            else if (s.isDislocated()) s.pain += pain * 0.6f;
        }
        PartState thorax = body.part(BodyPart.THORAX);
        if (thorax.isFractured() || thorax.isDislocated()) thorax.pain += pain * 0.4f;
    }

    /** Jumping or landing on a broken leg hurts. */
    @SubscribeEvent
    static void onJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !BodyTicker.simulated(player)) return;
        Body body = player.getData(FBAttachments.BODY);
        com.fleshbone.sim.system.MetabolismSystem.exert(body, 3f);
        for (BodyPart part : BodyPart.VALUES) {
            if (!part.isLeg()) continue;
            PartState s = body.part(part);
            if (s.isFractured()) s.pain += s.splinted ? 3f : 8f;
            else if (s.isDislocated()) s.pain += 5f;
        }
        if (body.part(BodyPart.THORAX).isFractured()) body.part(BodyPart.THORAX).pain += 3f;
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

    private static boolean monstersNearby(ServerPlayer player, net.minecraft.core.BlockPos bed) {
        net.minecraft.world.phys.Vec3 center = net.minecraft.world.phys.Vec3.atBottomCenterOf(bed);
        return !player.level().getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class,
                new net.minecraft.world.phys.AABB(center.x - 8, center.y - 5, center.z - 8, center.x + 8, center.y + 5, center.z + 8),
                monster -> monster.isPreventingPlayerRest(player.level(), player)).isEmpty();
    }

    /** Keep napping in daylight until rested (vanilla would wake the player at once). */
    @SubscribeEvent
    static void onContinueSleeping(net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !BodyTicker.simulated(player)) return;
        if (event.mayContinueSleeping() || player.level().dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
        Body body = player.getData(FBAttachments.BODY);
        if (player.level().isBrightOutside() && body.needs.energy < 98f && body.derived.totalPain <= PainSystem.NO_SLEEP) {
            event.setContinueSleeping(true);
        }
    }
}
