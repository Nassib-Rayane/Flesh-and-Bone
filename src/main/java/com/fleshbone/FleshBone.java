package com.fleshbone;

import org.slf4j.Logger;

import com.fleshbone.attach.FBAttachments;
import com.fleshbone.config.FBConfig;
import com.fleshbone.gametest.FBGameTests;
import com.fleshbone.item.FBDataComponents;
import com.fleshbone.item.FBItems;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(FleshBone.MODID)
public final class FleshBone {
    public static final String MODID = "fleshbone";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FleshBone(IEventBus modEventBus, ModContainer modContainer) {
        FBAttachments.register(modEventBus);
        FBDataComponents.register(modEventBus);
        com.fleshbone.village.FBVillagers.register(modEventBus);
        FBItems.register(modEventBus);
        FBGameTests.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.SERVER, FBConfig.SERVER_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, FBConfig.CLIENT_SPEC);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
