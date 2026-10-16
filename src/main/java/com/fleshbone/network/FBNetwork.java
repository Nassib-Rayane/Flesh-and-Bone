package com.fleshbone.network;

import com.fleshbone.FleshBone;
import com.fleshbone.attach.BodyRuntime;
import com.fleshbone.attach.FBAttachments;
import com.fleshbone.item.MedicalItem;
import com.fleshbone.item.PatientSessions;
import com.fleshbone.item.TreatmentService;
import com.fleshbone.sim.BodyPart;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = FleshBone.MODID)
public final class FBNetwork {
    private FBNetwork() {}

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(FBPayloads.Treat.TYPE, FBPayloads.Treat.STREAM_CODEC, FBNetwork::onTreat);
        registrar.playToServer(FBPayloads.DoProcedure.TYPE, FBPayloads.DoProcedure.STREAM_CODEC, FBNetwork::onProcedure);
        registrar.playToServer(FBPayloads.RemoveDevice.TYPE, FBPayloads.RemoveDevice.STREAM_CODEC, FBNetwork::onRemoveDevice);
        registrar.playToServer(FBPayloads.Cpr.TYPE, FBPayloads.Cpr.STREAM_CODEC, FBNetwork::onCpr);
        registrar.playToServer(FBPayloads.StopExamining.TYPE, FBPayloads.StopExamining.STREAM_CODEC,
                (payload, context) -> PatientSessions.stop((ServerPlayer) context.player()));
        registrar.playToClient(FBPayloads.PatientBody.TYPE, FBPayloads.PatientBody.STREAM_CODEC);
        registrar.playToClient(FBPayloads.PatientGone.TYPE, FBPayloads.PatientGone.STREAM_CODEC);
        registrar.playToClient(FBPayloads.Thought.TYPE, FBPayloads.Thought.STREAM_CODEC);
        registrar.playToClient(FBPayloads.Episode.TYPE, FBPayloads.Episode.STREAM_CODEC);
        registrar.playToServer(FBPayloads.Sleep.TYPE, FBPayloads.Sleep.STREAM_CODEC,
                (payload, context) -> com.fleshbone.effect.GameplayEffects.trySleep((ServerPlayer) context.player()));
        registrar.playToServer(FBPayloads.Exercise.TYPE, FBPayloads.Exercise.STREAM_CODEC, FBNetwork::onExercise);
    }

    private static void onExercise(FBPayloads.Exercise payload, IPayloadContext context) {
        com.fleshbone.sim.Environment.Exercise[] values = com.fleshbone.sim.Environment.Exercise.VALUES;
        if (payload.exercise() < 0 || payload.exercise() >= values.length) return;
        com.fleshbone.effect.GameplayEffects.startExercise((ServerPlayer) context.player(), values[payload.exercise()]);
    }

    private static BodyPart part(int index) {
        return index >= 0 && index < BodyPart.COUNT ? BodyPart.VALUES[index] : null;
    }

    /** Medics cannot treat faster than the item allows. */
    private static boolean rateLimited(ServerPlayer medic, int ticks) {
        BodyRuntime runtime = medic.getData(FBAttachments.RUNTIME);
        long now = medic.level().getGameTime();
        if (now - runtime.lastTreatTick < Math.max(5, ticks * 3 / 4)) return true;
        runtime.lastTreatTick = now;
        return false;
    }

    private static void onTreat(FBPayloads.Treat payload, IPayloadContext context) {
        ServerPlayer medic = (ServerPlayer) context.player();
        ServerPlayer patient = PatientSessions.patientOf(medic, payload.targetId());
        if (patient == null || payload.slot() < 0 || payload.slot() >= medic.getInventory().getContainerSize()) return;
        ItemStack stack = medic.getInventory().getItem(payload.slot());
        if (!(stack.getItem() instanceof MedicalItem item) || rateLimited(medic, item.action().useTicks())) return;
        float quality = Math.max(0f, Math.min(1f, payload.quality()));
        TreatmentService.treat(medic, patient, stack, part(payload.part()), quality, Math.max(0, payload.amount()));
    }

    private static void onProcedure(FBPayloads.DoProcedure payload, IPayloadContext context) {
        ServerPlayer medic = (ServerPlayer) context.player();
        ServerPlayer patient = PatientSessions.patientOf(medic, payload.targetId());
        FBPayloads.Procedure[] procedures = FBPayloads.Procedure.values();
        if (patient == null || payload.procedure() < 0 || payload.procedure() >= procedures.length || rateLimited(medic, 40)) return;
        float quality = Math.max(0f, Math.min(1f, payload.quality()));
        TreatmentService.procedure(medic, patient, procedures[payload.procedure()], part(payload.part()), quality);
    }

    private static void onCpr(FBPayloads.Cpr payload, IPayloadContext context) {
        ServerPlayer medic = (ServerPlayer) context.player();
        ServerPlayer patient = PatientSessions.patientOf(medic, payload.targetId());
        if (patient == null || patient == medic) return;
        BodyRuntime runtime = medic.getData(FBAttachments.RUNTIME);
        long now = medic.level().getGameTime();
        if (now - runtime.lastCprTick < 8) return;
        runtime.lastCprTick = now;
        TreatmentService.cpr(medic, patient, Math.max(0f, Math.min(1f, payload.perfusion())));
    }

    private static void onRemoveDevice(FBPayloads.RemoveDevice payload, IPayloadContext context) {
        ServerPlayer medic = (ServerPlayer) context.player();
        ServerPlayer patient = PatientSessions.patientOf(medic, payload.targetId());
        FBPayloads.Device[] devices = FBPayloads.Device.values();
        if (patient == null || payload.device() < 0 || payload.device() >= devices.length) return;
        TreatmentService.removeDevice(medic, patient, devices[payload.device()], part(payload.part()));
    }
}
