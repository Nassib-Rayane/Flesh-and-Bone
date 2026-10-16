package com.fleshbone.item;

import java.util.random.RandomGenerator;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyPart;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

/** What a medical item does, independently of how it is used (quick use, health panel, on another player). */
public interface MedicalAction {
    enum Target {
        /** Applied to one body part. */
        PART,
        /** Taken by the whole body (pills, injections). */
        BODY
    }

    /** Hands-on procedure replacing the timer in the health panel. */
    enum Minigame { NONE, BANDAGE, SUTURE, EXTRACT, SAW, DEFIB, SPLINT, INFUSION, CHEST_DRAIN, CAUTERIZE }

    /**
     * @param uses       item uses (durability) consumed; 0 consumes nothing
     * @param messageKey feedback shown to the medic, or {@code null}
     */
    record Result(boolean success, int uses, String messageKey, ItemStack give) {
        public Result(boolean success, int uses, String messageKey) {
            this(success, uses, messageKey, ItemStack.EMPTY);
        }

        public static Result ok(int uses) {
            return new Result(true, uses, null);
        }

        public static Result fail(String messageKey) {
            return new Result(false, 0, messageKey);
        }
    }

    Target target();

    /** Application time in ticks. */
    int useTicks();

    ItemUseAnimation animation();

    /** Part treated by a quick use, or {@code null} if there is nothing to treat (always {@code null} for BODY actions). */
    BodyPart autoTarget(Body body);

    /** @param part {@code null} for BODY actions */
    boolean canApply(Body body, BodyPart part);

    /** @param amount how much of the item the minigame used (bandage wraps), 0 for the default */
    Result apply(Body body, BodyPart part, float quality, int amount, RandomGenerator rng);

    /**
     * Actions that need the item itself or the patient (blood bags) override this; the game always calls this one.
     * {@code Result.give} is handed to the medic.
     */
    default Result apply(Body body, BodyPart part, float quality, int amount, RandomGenerator rng, ItemStack stack, ServerPlayer patient) {
        return apply(body, part, quality, amount, rng);
    }

    default Minigame minigame() {
        return Minigame.NONE;
    }

    /** Irreversible actions ask for confirmation in the health panel and cannot be quick-used. */
    default boolean dangerous() {
        return false;
    }

    /** Quality of a quick use, done in a hurry without the minigame. */
    default float quickQuality() {
        return 0.7f;
    }
}
