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
        if (isEpisode(event.type())) {
            if (!com.fleshbone.config.FBConfig.BRAIN_EPISODES.get()) return;
            FBPayloads.Episode episode = new FBPayloads.Episode(event.type().ordinal());
            if (player.connection != null && player.connection.hasChannel(episode)) PacketDistributor.sendToPlayer(player, episode);
        }
        String key = "fleshbone.thought." + event.type().name().toLowerCase(Locale.ROOT);
        String partKey = event.part() == null ? "" : "fleshbone.part." + event.part().serializedName() + ".my";
        FBPayloads.Thought thought = new FBPayloads.Thought(key, partKey);
        if (player.connection != null && player.connection.hasChannel(thought)) {
            PacketDistributor.sendToPlayer(player, thought);
        } else {
            player.sendOverlayMessage(Component.translatable(key, partKey.isEmpty() ? Component.empty() : Component.translatable(partKey)));
        }
    }

    public static boolean isEpisode(BodyEvent.Type type) {
        return switch (type) {
            case BRAIN_HALLUCINATION, BRAIN_MIRRORED, BRAIN_DROP, BRAIN_SHAKE, BRAIN_FLASH -> true;
            default -> false;
        };
    }
}
