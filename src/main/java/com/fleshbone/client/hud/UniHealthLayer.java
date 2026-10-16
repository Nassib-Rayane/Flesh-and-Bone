package com.fleshbone.client.hud;

import com.fleshbone.client.ClientBody;
import com.fleshbone.config.FBConfig;
import com.fleshbone.sim.Body;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.gui.GuiLayer;

/** The UNI-HEALTH readout in the top-left corner of the screen (U cycles full / compact / hidden). */
public final class UniHealthLayer implements GuiLayer {
    @Override
    public void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.getDebugOverlay().showDebugScreen()) return;
        FBConfig.UniHealthMode mode = FBConfig.UNI_HEALTH.get();
        if (mode == FBConfig.UniHealthMode.HIDDEN) return;
        Body body = ClientBody.get();
        if (body == null) return;
        boolean exact = ClientBody.vitalsVisible(mc.player);
        UniHealth.Detail detail = mode == FBConfig.UniHealthMode.FULL ? UniHealth.Detail.FULL : UniHealth.Detail.COMPACT;
        float scale = FBConfig.UNI_HEALTH_SCALE.get().floatValue();
        g.pose().pushMatrix();
        g.pose().translate(4, 4);
        g.pose().scale(scale, scale);
        UniHealth.draw(g, mc.font, body, 0, 0, detail, exact, detail == UniHealth.Detail.FULL ? UniHealth.worstPart(body) : null,
                ClientBody.time());
        g.pose().popMatrix();
    }
}
