package com.fleshbone.event;

import com.fleshbone.FleshBone;
import com.fleshbone.attach.FBAttachments;
import com.fleshbone.attach.Knowledge;
import com.fleshbone.sim.Body;
import com.fleshbone.sim.Skills;
import com.fleshbone.sim.system.SkillSystem;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Intelligence experience, after Casualties: Unknown: the first craft of each item (+10), every trade (+2.5),
 * exploring deeper than ever (+20 per 16 blocks below Y 48). Treatments add theirs in the treatment service.
 */
@EventBusSubscriber(modid = FleshBone.MODID)
public final class LearningEvents {
    public static final float TRADE_XP = 2.5f;

    private LearningEvents() {}

    @SubscribeEvent
    static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !BodyTicker.simulated(player)) return;
        ItemStack crafted = event.getCrafting();
        if (crafted.isEmpty()) return;
        Knowledge knowledge = player.getData(FBAttachments.KNOWLEDGE);
        if (knowledge.crafted.add(BuiltInRegistries.ITEM.getKey(crafted.getItem()).toString())) {
            gain(player, Knowledge.FIRST_CRAFT_XP);
        }
    }

    @SubscribeEvent
    static void onTrade(TradeWithVillagerEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && BodyTicker.simulated(player)) gain(player, TRADE_XP);
    }

    /** Once a second: a new depth record teaches something. */
    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0 || !BodyTicker.simulated(player)) return;
        int below = Knowledge.DEPTH_START - player.getBlockY();
        if (below < Knowledge.DEPTH_BAND) return;
        int band = below / Knowledge.DEPTH_BAND;
        Knowledge knowledge = player.getData(FBAttachments.KNOWLEDGE);
        if (band > knowledge.deepestBand) {
            int gained = band - Math.max(0, knowledge.deepestBand);
            knowledge.deepestBand = band;
            gain(player, Knowledge.DEPTH_XP * gained);
        }
    }

    private static void gain(ServerPlayer player, float xp) {
        Body body = player.getData(FBAttachments.BODY);
        SkillSystem.gain(body, Skills.Stat.INTELLIGENCE, xp);
        player.getData(FBAttachments.RUNTIME).dirty = true;
    }
}
