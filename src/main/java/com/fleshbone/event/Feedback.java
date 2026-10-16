package com.fleshbone.event;

import java.util.Locale;

import com.fleshbone.network.FBPayloads;
import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyEvent;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Tells the player what just happened to their body (thoughts above the hotbar). */
public final class Feedback {
    private Feedback() {}

    public static void onBodyEvent(ServerPlayer player, Body body, BodyEvent event) {
        String key = "fleshbone.thought." + event.type().name().toLowerCase(Locale.ROOT);
        String partKey = event.part() == null ? "" : "fleshbone.part." + event.part().serializedName() + ".my";
        FBPayloads.Thought thought = new FBPayloads.Thought(key, partKey);
        if (player.connection != null && player.connection.hasChannel(thought)) {
            PacketDistributor.sendToPlayer(player, thought);
        } else {
            player.sendOverlayMessage(Component.translatable(key, partKey.isEmpty() ? Component.empty() : Component.translatable(partKey)));
        }
    }
}
