package com.fleshbone.client;

import com.fleshbone.FleshBone;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// Never loaded on a dedicated server: client-only code is safe here.
@Mod(value = FleshBone.MODID, dist = Dist.CLIENT)
public final class FleshBoneClient {
    public FleshBoneClient(IEventBus modEventBus, ModContainer container) {
        // Mods screen > Flesh & Bone > Config
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
