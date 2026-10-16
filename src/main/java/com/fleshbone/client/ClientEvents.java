package com.fleshbone.client;

import com.fleshbone.FleshBone;
import com.fleshbone.client.hud.BodyHudLayer;
import com.fleshbone.client.hud.MoodleLayer;
import com.fleshbone.client.hud.ScreenEffectsLayer;
import com.fleshbone.client.hud.ThirstLayer;
import com.fleshbone.effect.GameplayEffects;
import com.fleshbone.event.BodyTicker;
import com.fleshbone.sim.Capabilities;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Input;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = FleshBone.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    static void registerLayers(RegisterGuiLayersEvent event) {
        // The body takes the place of the vanilla hearts
        event.replaceLayer(VanillaGuiLayers.PLAYER_HEALTH, new BodyHudLayer());
        // Overlays sit over the pumpkin/powder snow overlays, under every HUD element
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, FleshBone.id("screen_effects"), new ScreenEffectsLayer());
        event.registerAbove(VanillaGuiLayers.EFFECTS, FleshBone.id("moodles"), new MoodleLayer());
        event.registerAbove(VanillaGuiLayers.FOOD_LEVEL, FleshBone.id("thirst"), new ThirstLayer());
    }

    /** Mirror the server's restrictions locally so the client never predicts a sprint or a standing pose it cannot have. */
    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        Capabilities caps = GameplayEffects.capabilities(player);
        if (!caps.canSprint() && player.isSprinting()) player.setSprinting(false);
        if (caps.incapacitated()) {
            if (player.getForcedPose() != Pose.SWIMMING) player.setForcedPose(Pose.SWIMMING);
        } else if (player.getForcedPose() == Pose.SWIMMING) {
            player.setForcedPose(null);
        }
    }

    /** Lying on the ground: no jumping, sneaking or sprinting. */
    @SubscribeEvent
    static void onMovementInput(MovementInputUpdateEvent event) {
        if (!BodyTicker.simulated(event.getEntity())) return;
        if (GameplayEffects.capabilities(event.getEntity()).incapacitated()) event.getInput().keyPresses = Input.EMPTY;
    }
}
