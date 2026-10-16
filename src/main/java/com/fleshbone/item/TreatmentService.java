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

    /** An unconscious or helpless medic cannot do anything, not even to themselves. */
    public static boolean helpless(ServerPlayer medic) {
        return BodyTicker.simulated(medic) && medic.getData(FBAttachments.BODY).isIncapacitated();
    }

    public static boolean canReach(ServerPlayer medic, ServerPlayer patient) {
        return medic == patient || (patient.isAlive() && medic.level() == patient.level() && medic.distanceToSqr(patient) <= REACH * REACH);
    }

    /** Applies the medical item {@code stack} to {@code part} of the patient. */
    public static boolean treat(ServerPlayer medic, ServerPlayer patient, ItemStack stack, BodyPart part, float quality) {
        return treat(medic, patient, stack, part, quality, 0);
    }

    public static boolean treat(ServerPlayer medic, ServerPlayer patient, ItemStack stack, BodyPart part, float quality, int amount) {
        return treat(medic, patient, stack, part, quality, amount, 0);
    }

    /**
     * @param amount   how much of the item a procedure used (0 = the item's default)
     * @param mistakes {@code Mistakes.pack()} of what went wrong during the procedure
     */
    public static boolean treat(ServerPlayer medic, ServerPlayer patient, ItemStack stack, BodyPart part, float quality, int amount, int mistakes) {
        MedicalAction action = MedicalActions.of(stack);
        if (action == null || !BodyTicker.simulated(patient) || !canReach(medic, patient)) return false;
        if (helpless(medic)) return false;
        Body body = patient.getData(FBAttachments.BODY);
        if (quality < 0f) {
            // Given up halfway: nothing used, but the harm is done (a drip is set up in the forearm)
            BodyPart where = part == null && action.minigame() == MedicalAction.Minigame.INFUSION ? BodyPart.FOREARM_L : part;
            abandoned(medic, patient, body, where, mistakes);
            return false;
        }
        if (action.target() == MedicalAction.Target.PART && (part == null || !action.canApply(body, part))) {
            medic.sendOverlayMessage(Component.translatable("fleshbone.message.cannot_apply"));
            return false;
        }
        // A miserable body may not bother treating itself (Casualties: Unknown's mood refusals)
        Body medicBody = medic.getData(FBAttachments.BODY);
        if (medic == patient && ThreadLocalRandom.current().nextFloat() < com.fleshbone.sim.system.MindSystem.refusalChance(medicBody.mind.mood)) {
            medic.sendOverlayMessage(Component.translatable("fleshbone.message.refuse_treat"));
            return false;
        }
        // A clever medic does better work
        quality = Math.max(0f, Math.min(1f, quality + com.fleshbone.sim.system.SkillSystem.treatmentBonus(medicBody.skills)));
        MedicalAction.Result result = action.apply(body, part, quality, amount, ThreadLocalRandom.current(), stack, patient);
        Treatments.complications(body, part, com.fleshbone.sim.procedure.Mistakes.unpack(mistakes));
        // Sawing through a bone is hard work
        if (action.minigame() == MedicalAction.Minigame.SAW && amount > 0 && BodyTicker.simulated(medic)) {
            com.fleshbone.sim.system.StaminaSystem.exert(medicBody, Math.min(60f, amount * 2.5f));
        }
        if (result.success() && BodyTicker.simulated(medic)) {
            com.fleshbone.sim.system.SkillSystem.gain(medicBody, com.fleshbone.sim.Skills.Stat.INTELLIGENCE, 3f + 4f * quality);
        }
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
        procedure(medic, patient, procedure, part, quality, 0);
    }

    public static void procedure(ServerPlayer medic, ServerPlayer patient, FBPayloads.Procedure procedure, BodyPart part, float quality, int mistakes) {
        if (part == null || !BodyTicker.simulated(patient) || !canReach(medic, patient) || helpless(medic)) return;
        Body body = patient.getData(FBAttachments.BODY);
        PartState s = body.part(part);
        com.fleshbone.sim.procedure.Mistakes m = com.fleshbone.sim.procedure.Mistakes.unpack(mistakes);
        if (procedure == FBPayloads.Procedure.RESET_DISLOCATION) {
            // Overshooting the arc can snap the bone
            boolean snapped = m.tear > 0 && Treatments.canReset(s) && !s.isFractured() && ThreadLocalRandom.current().nextFloat() < 0.15f * m.tear;
            m.tear = 0;
            if (snapped) {
                s.fracture = 1f;
                body.emit(com.fleshbone.sim.BodyEvent.Type.FRACTURE, part);
            }
            if (quality < 0f || snapped) {
                Treatments.complications(body, part, m);
                if (snapped) medic.sendOverlayMessage(Component.translatable("fleshbone.message.reset.broke_bone"));
                else abandoned(medic, patient, body, part, m.pack());
                markChanged(medic, patient);
                return;
            }
        } else if (quality < 0f) {
            abandoned(medic, patient, body, part, mistakes);
            return;
        }
        switch (procedure) {
            case RESET_DISLOCATION -> {
                if (!Treatments.canReset(s)) return;
                Treatments.ResetResult result = Treatments.resetDislocation(body, part, quality, ThreadLocalRandom.current());
                Treatments.complications(body, part, m);
                if (result == Treatments.ResetResult.SUCCESS) learn(medic, 5f);
                medic.sendOverlayMessage(Component.translatable("fleshbone.message.reset." + result.name().toLowerCase(java.util.Locale.ROOT)));
            }
            case PULL_SHRAPNEL -> {
                if (!Treatments.canExtract(s)) return;
                boolean ok = Treatments.extractShrapnel(body, part, quality, false, ThreadLocalRandom.current());
                Treatments.complications(body, part, com.fleshbone.sim.procedure.Mistakes.unpack(mistakes));
                if (ok) learn(medic, 5f);
                medic.sendOverlayMessage(Component.translatable(ok ? "fleshbone.message.extract_ok" : "fleshbone.message.extract_failed"));
            }
        }
        markChanged(medic, patient);
    }

    public static void cpr(ServerPlayer medic, ServerPlayer patient, float perfusion) {
        cpr(medic, patient, perfusion, 0);
    }

    /** CPR on a patient whose heart or breathing has stopped; pushing too deep cracks ribs. */
    public static void cpr(ServerPlayer medic, ServerPlayer patient, float perfusion, int cracks) {
        if (medic == patient || !BodyTicker.simulated(patient) || !canReach(medic, patient) || helpless(medic)) return;
        Body body = patient.getData(FBAttachments.BODY);
        if (!body.vitals.cardiacArrest && body.vitals.respRate >= 2.5f) return;
        // Compressions are exhausting
        if (BodyTicker.simulated(medic)) com.fleshbone.sim.system.StaminaSystem.exert(medic.getData(FBAttachments.BODY), 0.6f);
        if (cracks > 0) {
            PartState thorax = body.part(BodyPart.THORAX);
            thorax.pain = Math.min(150f, thorax.pain + 6f * cracks);
            if (!thorax.isFractured() && ThreadLocalRandom.current().nextFloat() < 0.08f * cracks) {
                thorax.fracture = 1f;
                body.emit(com.fleshbone.sim.BodyEvent.Type.FRACTURE, BodyPart.THORAX);
            }
        }
        if (Treatments.cpr(body, perfusion, ThreadLocalRandom.current())) {
            medic.sendOverlayMessage(Component.translatable("fleshbone.message.heart_restarted"));
        }
        markChanged(medic, patient);
    }

    /** Takes a device off; splints and tourniquets go back into the medic's inventory. */
    public static void removeDevice(ServerPlayer medic, ServerPlayer patient, FBPayloads.Device device, BodyPart part) {
        if (part == null || !BodyTicker.simulated(patient) || !canReach(medic, patient) || helpless(medic)) return;
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

    /** A procedure given up halfway: only the harm done counts. */
    private static void abandoned(ServerPlayer medic, ServerPlayer patient, Body body, BodyPart part, int mistakes) {
        com.fleshbone.sim.procedure.Mistakes m = com.fleshbone.sim.procedure.Mistakes.unpack(mistakes);
        if (m.total() == 0) return;
        Treatments.complications(body, part, m);
        medic.sendOverlayMessage(Component.translatable("fleshbone.message.abandoned"));
        markChanged(medic, patient);
    }

    private static void learn(ServerPlayer medic, float xp) {
        if (BodyTicker.simulated(medic)) {
            com.fleshbone.sim.system.SkillSystem.gain(medic.getData(FBAttachments.BODY), com.fleshbone.sim.Skills.Stat.INTELLIGENCE, xp);
        }
    }

    private static void markChanged(ServerPlayer medic, ServerPlayer patient) {
        BodyRuntime runtime = patient.getData(FBAttachments.RUNTIME);
        runtime.dirty = true;
        patient.syncData(FBAttachments.BODY);
        if (medic != patient) PatientSessions.sendNow(medic, patient);
    }
}
