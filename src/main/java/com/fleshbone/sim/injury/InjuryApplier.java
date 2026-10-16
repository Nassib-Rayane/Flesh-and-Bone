package com.fleshbone.sim.injury;

import java.util.random.RandomGenerator;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyEvent;
import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.PartState;
import com.fleshbone.sim.Vitals;
import com.fleshbone.sim.system.HygieneSystem;
import com.fleshbone.sim.system.SensesSystem;
import com.fleshbone.sim.system.SleepSystem;

/** Turns an {@link Injury} into skin, muscle, bleeding, pain and lesions on the body. */
public final class InjuryApplier {
    /** Head muscle below this means a concussion (unconscious until it heals). */
    public static final float CONCUSSION_MUSCLE = 14.4f;
    /** Overflow damage on an already destroyed head that kills outright. */
    private static final float HEAD_DESTROYED_OVERFLOW = 20f;

    private InjuryApplier() {}

    public static void apply(Body body, Injury injury, RandomGenerator rng) {
        BodyPart part = injury.part();
        PartState s = body.part(part);
        InjuryKind kind = injury.kind();
        float sev = injury.severity();
        if (sev <= 0f) return;
        Vitals v = body.vitals;

        // Hits to a missing limb land on the stump
        if (s.amputated) {
            BodyPart parent = part.parent();
            if (parent == null) return;
            apply(body, new Injury(parent, kind, sev * 0.6f, injury.contamination(), injury.shrapnel(), injury.venom()), rng);
            return;
        }

        float skinLoss = sev * kind.skin;
        float muscleLoss = sev * kind.muscle;
        s.skin = Math.max(0f, s.skin - skinLoss);
        if (muscleLoss > s.muscle) {
            float overflow = muscleLoss - s.muscle;
            s.muscle = 0f;
            if (part == BodyPart.HEAD && overflow > HEAD_DESTROYED_OVERFLOW) v.headDestroyed = true;
            if (part.isTorso()) s.internalBleed = Math.min(100f, s.internalBleed + overflow * 0.5f);
        } else {
            s.muscle -= muscleLoss;
        }

        // Bruises do not bleed; anything that breaks the skin does
        if (kind.bleed > 0f && s.skin < 100f && (kind != InjuryKind.BLUNT || sev > 10f)) {
            boolean wasHeavy = s.bleed * 0.018f >= 0.225f;
            s.bleed = Math.min(100f, s.bleed + sev * kind.bleed);
            if (!wasHeavy && s.bleed * 0.018f >= 0.225f) body.emit(BodyEvent.Type.HEAVY_BLEEDING, part);
        }
        if (part.canBleedInternally() && kind.internal > 0f) {
            float internal = sev * kind.internal * (sev > 15f ? 1f : 0.3f);
            boolean had = s.internalBleed > 9.5f;
            s.internalBleed = Math.min(100f, s.internalBleed + internal);
            if (!had && s.internalBleed > 9.5f) body.emit(BodyEvent.Type.INTERNAL_BLEEDING, part);
        }

        s.pain = Math.min(150f, s.pain + sev * kind.pain);
        s.contamination = Math.min(100f, s.contamination + kind.contamination + injury.contamination());
        v.adrenaline = Math.min(100f, v.adrenaline + sev * 0.6f);

        // Dressings get soaked and stitches tear under new trauma; a hard blow snaps a splint
        if (s.isBandaged()) s.bandageSoak = Math.min(1f, s.bandageSoak + sev * 0.01f);
        if (s.sutured && (kind == InjuryKind.CUT || kind == InjuryKind.PIERCE || kind == InjuryKind.BITE) && sev > 15f) s.sutured = false;
        if (s.splinted && sev > 30f && (kind == InjuryKind.BLUNT || kind == InjuryKind.FALL || kind == InjuryKind.CRUSH)) s.splinted = false;

        boolean fractured = false;
        float fractureChance = kind.fractureChance(sev) * fractureFactor(part);
        if (fractureChance > 0f && rng.nextFloat() < fractureChance) {
            s.fracture = 1f;
            fractured = true;
            body.emit(BodyEvent.Type.FRACTURE, part);
            if (part == BodyPart.HEAD) {
                s.internalBleed = Math.min(100f, s.internalBleed + 10f);
                v.brainHealth = Math.max(0f, v.brainHealth - 5f);
            } else if (part == BodyPart.THORAX) {
                s.internalBleed = Math.min(100f, s.internalBleed + 8f);
            }
        }
        if (!fractured && part.canDislocate() && !s.isDislocated()) {
            float dislocationChance = kind.dislocationChance(sev) * 0.5f;
            if (dislocationChance > 0f && rng.nextFloat() < dislocationChance) {
                s.dislocation = 1f;
                body.emit(BodyEvent.Type.DISLOCATION, part);
            }
        }
        if (kind.shrapnel > 0f && rng.nextFloat() < kind.shrapnel) {
            s.shrapnel = Math.min(9, s.shrapnel + Math.max(1, injury.shrapnel()));
            body.emit(BodyEvent.Type.SHRAPNEL, part);
        }

        if (part == BodyPart.HEAD && kind.concussive()) {
            v.consciousness = Math.max(0f, v.consciousness - 1.6f * sev);
            if (sev > 35f) v.brainHealth = Math.max(0f, v.brainHealth - (sev - 35f) * 0.3f);
            if (s.muscle < CONCUSSION_MUSCLE && s.muscle + muscleLoss >= CONCUSSION_MUSCLE) body.emit(BodyEvent.Type.CONCUSSION, part);
        }

        switch (kind) {
            case ELECTRIC -> {
                v.fibrillation = Math.min(100f, v.fibrillation + sev * 1.5f);
                v.consciousness = Math.max(0f, v.consciousness - sev);
            }
            case FREEZE -> v.bodyTemp -= sev * 0.02f;
            case BURN -> v.bodyTemp += sev * 0.01f;
            // A blast close by rings the ears
            case EXPLOSION -> SensesSystem.deafen(body, sev * 0.6f);
            default -> { }
        }
        // A hard piercing blow to the face can take an eye
        if (part == BodyPart.HEAD && sev > 25f
                && (kind == InjuryKind.PIERCE || kind == InjuryKind.BALLISTIC || kind == InjuryKind.EXPLOSION || kind == InjuryKind.CUT)
                && rng.nextFloat() < Math.min(0.3f, (sev - 25f) / 125f)) {
            SensesSystem.loseEye(body, rng);
        }
        if (injury.venom() > 0f) {
            body.chem.venom = Math.min(100f, body.chem.venom + injury.venom());
            s.pain = Math.min(150f, s.pain + injury.venom() * 0.5f);
        }
        HygieneSystem.soil(body, HygieneSystem.HIT_DIRT);
        if (body.needs.asleep) SleepSystem.wake(body, SleepSystem.WakeReason.HURT);
    }

    /** Some bones break more easily than others. */
    private static float fractureFactor(BodyPart part) {
        return switch (part.region()) {
            case HEAD -> 0.6f;
            case TORSO -> part == BodyPart.ABDOMEN ? 0f : 0.5f;
            case HAND -> 1.1f;
            default -> 1f;
        };
    }
}
