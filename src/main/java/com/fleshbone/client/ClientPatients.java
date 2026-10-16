package com.fleshbone.client;

import com.fleshbone.FleshBone;
import com.fleshbone.client.screen.HealthPanelScreen;
import com.fleshbone.network.FBPayloads;
import com.fleshbone.sim.Body;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** The other player currently being examined, streamed by the server. */
@EventBusSubscriber(modid = FleshBone.MODID, value = Dist.CLIENT)
public final class ClientPatients {
    private static int patientId = -1;
    private static Body patientBody;

    private ClientPatients() {}

    @SubscribeEvent
    static void registerHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(FBPayloads.PatientBody.TYPE, (payload, context) -> onBody(payload));
        event.register(FBPayloads.PatientGone.TYPE, (payload, context) -> onGone(payload));
    }

    private static void onBody(FBPayloads.PatientBody payload) {
        patientId = payload.targetId();
        patientBody = payload.body();
        Minecraft mc = Minecraft.getInstance();
        if (payload.open() && (mc.screen == null || mc.screen instanceof HealthPanelScreen)) {
            mc.setScreen(new HealthPanelScreen(payload.targetId()));
        }
    }

    private static void onGone(FBPayloads.PatientGone payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof HealthPanelScreen screen && screen.isExamining()) {
            screen.patientLost();
            if (mc.player != null) mc.player.sendOverlayMessage(Component.translatable("fleshbone.message.patient_gone"));
        }
        patientId = -1;
        patientBody = null;
    }

    /** The examined body if {@code entityId} is the current patient, else {@code null}. */
    public static Body body(int entityId) {
        return entityId == patientId ? patientBody : null;
    }
}
