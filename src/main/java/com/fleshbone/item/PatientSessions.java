package com.fleshbone.item;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.fleshbone.FleshBone;
import com.fleshbone.attach.FBAttachments;
import com.fleshbone.event.BodyTicker;
import com.fleshbone.network.FBPayloads;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A medic examining another player: right-clicking them with a medical item opens their health panel, and their body
 * is streamed to the medic while they stay within reach.
 */
@EventBusSubscriber(modid = FleshBone.MODID)
public final class PatientSessions {
    private static final int SEND_INTERVAL = 5;
    /** Medic UUID → patient UUID. */
    private static final Map<UUID, UUID> SESSIONS = new HashMap<>();

    private PatientSessions() {}

    @SubscribeEvent
    static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Player target) || !(event.getItemStack().getItem() instanceof MedicalItem)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getEntity() instanceof ServerPlayer medic && target instanceof ServerPlayer patient
                && BodyTicker.simulated(patient) && TreatmentService.canReach(medic, patient)) {
            SESSIONS.put(medic.getUUID(), patient.getUUID());
            send(medic, new FBPayloads.PatientBody(patient.getId(), true, patient.getData(FBAttachments.BODY)));
        }
    }

    public static void stop(ServerPlayer medic) {
        SESSIONS.remove(medic.getUUID());
    }

    /** The patient currently examined by this medic, if still valid. */
    public static ServerPlayer patientOf(ServerPlayer medic, int entityId) {
        if (medic.getId() == entityId) return medic;
        UUID patientId = SESSIONS.get(medic.getUUID());
        if (patientId == null) return null;
        ServerPlayer patient = medic.level().getServer().getPlayerList().getPlayer(patientId);
        return patient != null && patient.getId() == entityId ? patient : null;
    }

    public static void sendNow(ServerPlayer medic, ServerPlayer patient) {
        send(medic, new FBPayloads.PatientBody(patient.getId(), false, patient.getData(FBAttachments.BODY)));
    }

    /** Only clients that negotiated the mod's channels can receive its payloads. */
    private static void send(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (player.connection != null && player.connection.hasChannel(payload)) PacketDistributor.sendToPlayer(player, payload);
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (SESSIONS.isEmpty() || server.getTickCount() % SEND_INTERVAL != 0) return;
        Iterator<Map.Entry<UUID, UUID>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, UUID> entry = it.next();
            ServerPlayer medic = server.getPlayerList().getPlayer(entry.getKey());
            ServerPlayer patient = server.getPlayerList().getPlayer(entry.getValue());
            if (medic == null) {
                it.remove();
            } else if (patient == null || !TreatmentService.canReach(medic, patient) || !BodyTicker.simulated(patient)) {
                it.remove();
                send(medic, new FBPayloads.PatientGone(patient == null ? -1 : patient.getId()));
            } else {
                sendNow(medic, patient);
            }
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
        SESSIONS.values().removeIf(id -> id.equals(event.getEntity().getUUID()));
    }
}
