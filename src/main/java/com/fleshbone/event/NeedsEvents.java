package com.fleshbone.event;

import java.util.concurrent.ThreadLocalRandom;

import com.fleshbone.FleshBone;
import com.fleshbone.attach.FBAttachments;
import com.fleshbone.config.FBSettings;
import com.fleshbone.data.FBDataMaps;
import com.fleshbone.item.CanteenItem;
import com.fleshbone.sim.Body;
import com.fleshbone.sim.system.HungerSystem;
import com.fleshbone.sim.system.MindSystem;
import com.fleshbone.sim.treatment.Treatments;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;

/** Drinking, eating and sleeping in a bed. */
@EventBusSubscriber(modid = FleshBone.MODID)
public final class NeedsEvents {
    /** Energy restored by a full night in a bed (on top of what was recovered while asleep). */
    private static final float NIGHT_SLEEP_ENERGY = 70f;
    /** Hunger per vanilla food point and per saturation point, for foods without an explicit value. */
    public static final float HUNGER_PER_FOOD = 5f;
    public static final float HUNGER_PER_SATURATION = 1.5f;

    private NeedsEvents() {}

    /** A miserable body may refuse to eat (Casualties: Unknown's mood refusals). */
    @SubscribeEvent
    static void onStartEating(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !BodyTicker.simulated(player)) return;
        ItemStack stack = event.getItem();
        if (!stack.has(DataComponents.FOOD) || stack.getData(FBDataMaps.DRINKS) != null) return;
        Body body = player.getData(FBAttachments.BODY);
        if (ThreadLocalRandom.current().nextFloat() < MindSystem.refusalChance(body.mind.mood)) {
            event.setCanceled(true);
            player.sendOverlayMessage(Component.translatable("fleshbone.message.refuse_eat"));
        }
    }

    /** Every consumed item is looked up in the drink and nutrition data maps. */
    @SubscribeEvent
    static void onConsumed(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !BodyTicker.simulated(player)) return;
        ItemStack stack = event.getItem();
        if (stack.isEmpty() || stack.getItem() instanceof CanteenItem) return;
        Body body = player.getData(FBAttachments.BODY);
        boolean sick = false;
        boolean overate = false;
        FBDataMaps.Drink drink = stack.getData(FBDataMaps.DRINKS);
        if (drink != null) {
            sick = Treatments.drink(body, drink.thirst(), drink.sickness(), drink.sicknessChance(), drink.warmth(), drink.caffeine(),
                    ThreadLocalRandom.current());
        }
        FoodProperties food = stack.get(DataComponents.FOOD);
        FBDataMaps.Nutrition nutrition = stack.getData(FBDataMaps.NUTRITION);
        if (food != null || nutrition != null) {
            boolean hunger = FBSettings.hunger();
            float points = hunger ? hunger(food, nutrition) : 0f;
            float weight = hunger ? nutrition != null && nutrition.weight().isPresent() ? nutrition.weight().get() : points * HungerSystem.KG_PER_HUNGER : 0f;
            Treatments.MealResult result = nutrition != null
                    ? Treatments.eat(body, points, weight, nutrition.thirst(), nutrition.sickness(), nutrition.sicknessChance(), nutrition.mood(),
                    ThreadLocalRandom.current())
                    : Treatments.eat(body, points, weight, 0f, 0f, 0f, 0f, ThreadLocalRandom.current());
            sick |= result == Treatments.MealResult.SICK;
            overate = result == Treatments.MealResult.OVERATE;
            // The vanilla bar stays locked: the meal has been counted here
            if (hunger) {
                player.getFoodData().setFoodLevel(BodyTicker.LOCKED_FOOD);
                player.getFoodData().setSaturation(0f);
            }
        } else if (drink == null) {
            return;
        }
        if (overate) player.sendOverlayMessage(Component.translatable("fleshbone.message.overate"));
        else if (sick) player.sendOverlayMessage(Component.translatable("fleshbone.message.feel_sick"));
        player.getData(FBAttachments.RUNTIME).dirty = true;
    }

    /** Hunger a food restores: from the data map, else 5 per vanilla food point and 1.5 per saturation point. */
    public static float hunger(FoodProperties food, FBDataMaps.Nutrition nutrition) {
        if (nutrition != null && nutrition.hunger().isPresent()) return nutrition.hunger().get();
        if (food == null) return 0f;
        return food.nutrition() * HUNGER_PER_FOOD + food.saturation() * HUNGER_PER_SATURATION;
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
