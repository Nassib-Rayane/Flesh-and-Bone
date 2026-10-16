package com.fleshbone.event;

import java.util.concurrent.ThreadLocalRandom;

import com.fleshbone.FleshBone;
import com.fleshbone.attach.FBAttachments;
import com.fleshbone.data.FBDataMaps;
import com.fleshbone.item.CanteenItem;
import com.fleshbone.sim.Body;
import com.fleshbone.sim.treatment.Treatments;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;

/** Drinking, eating and sleeping. */
@EventBusSubscriber(modid = FleshBone.MODID)
public final class NeedsEvents {
    /** Energy restored by a full night in a bed (on top of what was recovered while asleep). */
    private static final float NIGHT_SLEEP_ENERGY = 70f;

    private NeedsEvents() {}

    /** Every consumed item is looked up in the drink and food data maps. */
    @SubscribeEvent
    static void onConsumed(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !BodyTicker.simulated(player)) return;
        ItemStack stack = event.getItem();
        if (stack.isEmpty() || stack.getItem() instanceof CanteenItem) return;
        Body body = player.getData(FBAttachments.BODY);
        boolean sick = false;
        FBDataMaps.Drink drink = stack.getData(FBDataMaps.DRINKS);
        if (drink != null) {
            sick = Treatments.drink(body, drink.thirst(), drink.sickness(), drink.sicknessChance(), drink.warmth(), drink.caffeine(),
                    ThreadLocalRandom.current());
        }
        FBDataMaps.FoodEffect food = stack.getData(FBDataMaps.FOOD_EFFECTS);
        if (food != null) {
            sick |= Treatments.eat(body, food.sickness(), food.sicknessChance(), food.mood(), food.thirst(), ThreadLocalRandom.current());
        }
        if (drink == null && food == null) return;
        if (sick) player.sendOverlayMessage(Component.translatable("fleshbone.message.feel_sick"));
        player.getData(FBAttachments.RUNTIME).dirty = true;
    }

    /** Energy restored by a daytime nap. */
    private static final float NAP_ENERGY = 20f;
    /** Dimension and game time of the last daytime nap that ended (a nap does not skip time). */
    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> napLevel;
    private static long napTime = -1;

    /** A full night in a bed restores most of your energy; a daytime nap a little. */
    @SubscribeEvent
    static void onWakeUp(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !BodyTicker.simulated(player)) return;
        if (player.isSleepingLongEnough()) {
            boolean nap = player.level().dimension() == napLevel && player.level().getGameTime() == napTime;
            Body body = player.getData(FBAttachments.BODY);
            body.needs.energy = Math.min(100f, body.needs.energy + (nap ? NAP_ENERGY : NIGHT_SLEEP_ENERGY));
            player.getData(FBAttachments.RUNTIME).dirty = true;
        }
    }

    /** Everyone asleep in daylight: it was a nap, the clock does not jump to the next morning. */
    @SubscribeEvent
    static void onSleepFinished(net.neoforged.neoforge.event.level.SleepFinishedTimeEvent event) {
        var level = event.getLevel();
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel && serverLevel.isBrightOutside()) {
            event.setAdjustment(null);
            napLevel = serverLevel.dimension();
            napTime = serverLevel.getGameTime();
        }
    }
}
