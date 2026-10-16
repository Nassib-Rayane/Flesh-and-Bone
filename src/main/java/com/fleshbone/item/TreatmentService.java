package com.fleshbone.item;

import java.util.concurrent.ThreadLocalRandom;

import com.fleshbone.attach.BodyRuntime;
import com.fleshbone.attach.FBAttachments;
import com.fleshbone.event.BodyTicker;
import com.fleshbone.network.FBPayloads;
import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.PartState;
import com.fleshbone.sim.treatment.Treatments;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/** Server-side validation and application of treatments, whoever performs them on whomever. */
public final class TreatmentService {
    /** Maximum distance between medic and patient, in blocks. */
    public static final double REACH = 4.0;

    private TreatmentService() {}

    public static boolean canReach(ServerPlayer medic, ServerPlayer patient) {
        return medic == patient || (patient.isAlive() && medic.level() == patient.level() && medic.distanceToSqr(patient) <= REACH * REACH);
    }

    /** Applies the medical item {@code stack} to {@code part} of the patient. */
    public static boolean treat(ServerPlayer medic, ServerPlayer patient, ItemStack stack, BodyPart part, float quality) {
        return treat(medic, patient, stack, part, quality, 0);
    }

    /** @param amount how much of the item a minigame used (0 = the item's default) */
    public static boolean treat(ServerPlayer medic, ServerPlayer patient, ItemStack stack, BodyPart part, float quality, int amount) {
        if (!(stack.getItem() instanceof MedicalItem item) || !BodyTicker.simulated(patient) || !canReach(medic, patient)) return false;
        MedicalAction action = item.action();
        Body body = patient.getData(FBAttachments.BODY);
        if (action.target() == MedicalAction.Target.PART && (part == null || !action.canApply(body, part))) {
            medic.sendOverlayMessage(Component.translatable("fleshbone.message.cannot_apply"));
            return false;
        }
        MedicalAction.Result result = action.apply(body, part, quality, amount, ThreadLocalRandom.current(), stack, patient);
        if (result.uses() > 0) {
            if (stack.isDamageableItem()) stack.hurtAndBreak(result.uses(), medic.level(), medic, broken -> { });
            else stack.shrink(1);
        }
        if (!result.give().isEmpty() && !medic.getInventory().add(result.give())) medic.drop(result.give(), false);
        if (result.messageKey() != null) {
            medic.sendOverlayMessage(Component.translatable(result.messageKey()));
        } else if (medic != patient) {
            patient.sendOverlayMessage(Component.translatable("fleshbone.message.treated_by", medic.getDisplayName()));
        }
        medic.level().playSound(null, patient.blockPosition(),
                action.target() == MedicalAction.Target.BODY ? SoundEvents.GENERIC_DRINK.value() : SoundEvents.WOOL_PLACE,
                SoundSource.PLAYERS, 0.6f, 1.1f);
        markChanged(medic, patient);
        return result.success();
    }

    /** Hands-only procedures: putting a joint back, pulling a fragment out with bare fingers. */
    public static void procedure(ServerPlayer medic, ServerPlayer patient, FBPayloads.Procedure procedure, BodyPart part, float quality) {
        if (part == null || !BodyTicker.simulated(patient) || !canReach(medic, patient)) return;
        Body body = patient.getData(FBAttachments.BODY);
        PartState s = body.part(part);
        switch (procedure) {
            case RESET_DISLOCATION -> {
                if (!Treatments.canReset(s)) return;
                Treatments.ResetResult result = Treatments.resetDislocation(body, part, quality, ThreadLocalRandom.current());
                medic.sendOverlayMessage(Component.translatable("fleshbone.message.reset." + result.name().toLowerCase(java.util.Locale.ROOT)));
            }
            case PULL_SHRAPNEL -> {
                if (!Treatments.canExtract(s)) return;
                boolean ok = Treatments.extractShrapnel(body, part, quality, false, ThreadLocalRandom.current());
                medic.sendOverlayMessage(Component.translatable(ok ? "fleshbone.message.extract_ok" : "fleshbone.message.extract_failed"));
            }
        }
        markChanged(medic, patient);
    }

    /** CPR on a patient whose heart or breathing has stopped. */
    public static void cpr(ServerPlayer medic, ServerPlayer patient, float perfusion) {
        if (medic == patient || !BodyTicker.simulated(patient) || !canReach(medic, patient)) return;
        Body body = patient.getData(FBAttachments.BODY);
        if (!body.vitals.cardiacArrest && body.vitals.respRate >= 2.5f) return;
        if (Treatments.cpr(body, perfusion, ThreadLocalRandom.current())) {
            medic.sendOverlayMessage(Component.translatable("fleshbone.message.heart_restarted"));
        }
        markChanged(medic, patient);
    }

    /** Takes a device off; splints and tourniquets go back into the medic's inventory. */
    public static void removeDevice(ServerPlayer medic, ServerPlayer patient, FBPayloads.Device device, BodyPart part) {
        if (part == null || !BodyTicker.simulated(patient) || !canReach(medic, patient)) return;
        Body body = patient.getData(FBAttachments.BODY);
        PartState s = body.part(part);
        ItemStack giveBack = ItemStack.EMPTY;
        switch (device) {
            case BANDAGE -> {
                if (!s.isBandaged()) return;
                s.removeBandage();
            }
            case SPLINT -> {
                if (!s.splinted) return;
                s.splinted = false;
                giveBack = new ItemStack(FBItems.SPLINT.get());
            }
            case TOURNIQUET -> {
                if (!s.tourniquet) return;
                Treatments.removeTourniquet(body, part);
                giveBack = new ItemStack(FBItems.TOURNIQUET.get());
            }
        }
        if (!giveBack.isEmpty() && !medic.getInventory().add(giveBack)) medic.drop(giveBack, false);
        body.derived.compute(body);
        markChanged(medic, patient);
    }

    private static void markChanged(ServerPlayer medic, ServerPlayer patient) {
        BodyRuntime runtime = patient.getData(FBAttachments.RUNTIME);
        runtime.dirty = true;
        patient.syncData(FBAttachments.BODY);
        if (medic != patient) PatientSessions.sendNow(medic, patient);
    }
}
