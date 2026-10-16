package com.fleshbone.damage;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import com.fleshbone.FleshBone;
import com.fleshbone.attach.BodyRuntime;
import com.fleshbone.attach.FBAttachments;
import com.fleshbone.config.FBConfig;
import com.fleshbone.config.FBSettings;
import com.fleshbone.event.BodyTicker;
import com.fleshbone.sim.Body;
import com.fleshbone.sim.injury.Injury;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDrownEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent;

/**
 * Replaces vanilla health: every hit a simulated player takes becomes injuries on body parts, and vanilla health never
 * moves. Only the mod's own death damage (and /kill, the void...) reaches the hearts.
 */
@EventBusSubscriber(modid = FleshBone.MODID)
public final class DamageRouter {
    /** Vanilla healing points to tissue points (golden apples, regeneration potions). */
    private static final float HEAL_TO_TISSUE = 6f;

    private DamageRouter() {}

    /** Plan the hit while the attack geometry is fresh, and switch off global armor in favour of per-part armor. */
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !BodyTicker.simulated(player)) return;
        DamageSource source = event.getSource();
        if (DamageClassifier.isPassthrough(source)) return;
        BodyRuntime runtime = player.getData(FBAttachments.RUNTIME);
        runtime.pendingPlan = DamageClassifier.classify(player, source, ThreadLocalRandom.current());
        runtime.pendingSource = source;
        if (FBConfig.PER_PART_ARMOR.get()) event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> 0f);
    }

    /** A localized hit only wears the armor piece covering that part. */
    @SubscribeEvent
    static void onArmorHurt(ArmorHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !FBConfig.PER_PART_ARMOR.get()) return;
        BodyRuntime runtime = player.getData(FBAttachments.RUNTIME);
        DamageClassifier.Plan plan = runtime.pendingPlan;
        if (plan == null || runtime.pendingSource != event.getDamageSource() || !plan.localized()) return;
        Set<EquipmentSlot> covering = PartArmor.coveringSlots(plan.mode() == DamageClassifier.Mode.HEAD ? com.fleshbone.sim.BodyPart.HEAD : plan.part());
        for (EquipmentSlot slot : event.getArmorMap().keySet()) {
            if (!covering.contains(slot)) event.setNewDamage(slot, 0f);
        }
    }

    /** Final damage after enchantments, effects and absorption: becomes injuries; vanilla health is left alone. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !BodyTicker.simulated(player)) return;
        DamageSource source = event.getSource();
        if (DamageClassifier.isPassthrough(source)) return;
        float damage = event.getNewDamage();
        event.setNewDamage(0f);
        BodyRuntime runtime = player.getData(FBAttachments.RUNTIME);
        DamageClassifier.Plan plan = runtime.pendingSource == source && runtime.pendingPlan != null
                ? runtime.pendingPlan
                : DamageClassifier.classify(player, source, ThreadLocalRandom.current());
        runtime.pendingPlan = null;
        runtime.pendingSource = null;
        if (damage <= 0f || plan.mode() == DamageClassifier.Mode.IGNORE) return;

        Body body = player.getData(FBAttachments.BODY);
        float scale = FBSettings.damageScale();
        float severity = damage * scale;
        List<Injury> injuries = plan.injuries(severity, ThreadLocalRandom.current());
        if (FBConfig.PER_PART_ARMOR.get()) injuries = PartArmor.reduce(player, source, injuries, scale);
        body.injureAll(injuries, ThreadLocalRandom.current());
        plan.sideEffects(body, severity);
        if (plan.suffocates()) runtime.suffocationTicks = 20;
        runtime.dirty = true;
    }

    /** Vanilla healing regrows tissue instead of hearts. */
    @SubscribeEvent
    static void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !BodyTicker.simulated(player)) return;
        float amount = event.getAmount();
        event.setCanceled(true);
        if (amount <= 0f) return;
        player.getData(FBAttachments.BODY).regenerate(amount * HEAL_TO_TISSUE);
        player.getData(FBAttachments.RUNTIME).dirty = true;
    }

    /** Running out of air lowers blood oxygen (see the respiration system) instead of dealing damage. */
    @SubscribeEvent
    static void onDrown(LivingDrownEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && BodyTicker.simulated(player)) event.setDamageAmount(0f);
    }

    /** A totem pulls the body back from death. */
    @SubscribeEvent
    static void onTotem(LivingUseTotemEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !BodyTicker.simulated(player)) return;
        player.getData(FBAttachments.BODY).stabilize();
        player.getData(FBAttachments.RUNTIME).dirty = true;
    }
}
