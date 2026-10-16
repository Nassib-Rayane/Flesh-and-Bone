package com.fleshbone.client;

import org.lwjgl.glfw.GLFW;

import com.fleshbone.FleshBone;
import com.fleshbone.client.screen.HealthPanelScreen;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.util.Lazy;

@EventBusSubscriber(modid = FleshBone.MODID, value = Dist.CLIENT)
public final class FBKeys {
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(FleshBone.id("main"));
    public static final Lazy<KeyMapping> HEALTH_PANEL = Lazy.of(() -> new KeyMapping(
            "key.fleshbone.health_panel", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY));
    public static final Lazy<KeyMapping> UNI_HEALTH = Lazy.of(() -> new KeyMapping(
            "key.fleshbone.uni_health", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, CATEGORY));
    /** Lie down to sleep where you stand, or get up. */
    public static final Lazy<KeyMapping> SLEEP = Lazy.of(() -> new KeyMapping(
            "key.fleshbone.sleep", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY));

    private FBKeys() {}

    @SubscribeEvent
    static void register(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(HEALTH_PANEL.get());
        event.register(UNI_HEALTH.get());
        event.register(SLEEP.get());
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (HEALTH_PANEL.get().consumeClick()) {
            if (mc.screen == null && ClientBody.get() != null && ClientBody.canAct()) mc.setScreen(new HealthPanelScreen());
        }
        while (UNI_HEALTH.get().consumeClick()) {
            var modes = com.fleshbone.config.FBConfig.UniHealthMode.values();
            var next = modes[(com.fleshbone.config.FBConfig.UNI_HEALTH.get().ordinal() + 1) % modes.length];
            com.fleshbone.config.FBConfig.UNI_HEALTH.set(next);
        }
        while (SLEEP.get().consumeClick()) {
            if (ClientBody.get() != null && mc.getConnection() != null) {
                net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new com.fleshbone.network.FBPayloads.Sleep());
            }
        }
    }
}
