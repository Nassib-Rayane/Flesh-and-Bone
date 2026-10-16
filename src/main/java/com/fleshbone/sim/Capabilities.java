package com.fleshbone.sim;

import com.fleshbone.sim.BodyPart.Side;
import com.fleshbone.sim.system.ConsciousnessSystem;
import com.fleshbone.sim.system.HungerSystem;
import com.fleshbone.sim.system.PainSystem;
import com.fleshbone.sim.system.SkillSystem;
import com.fleshbone.sim.system.StaminaSystem;

/**
 * What the body can physically do right now, as multipliers on vanilla abilities. Pure function of the body state so
 * the client can predict it (sprint, movement input) from the synced body.
 *
 * <p>Legs, arms and trunk set what is mechanically possible; stamina strength (a cubic curve of stamina), carried
 * load, pain, consciousness, mood, a full stomach, body weight and strength level scale it.
 *
 * @param move          walking speed multiplier (0 when incapacitated)
 * @param jump          jump strength multiplier
 * @param attackDamage  melee damage multiplier
 * @param attackSpeed   attack speed multiplier
 * @param mining        block breaking speed multiplier
 * @param safeFallDelta change to the safe fall distance in blocks
 */
public record Capabilities(
        float move,
        float jump,
        float attackDamage,
        float attackSpeed,
        float mining,
        float safeFallDelta,
        boolean canSprint,
        boolean mainHandUsable,
        boolean offHandUsable,
        boolean incapacitated,
        boolean unconscious) {

    public static final Capabilities HEALTHY = new Capabilities(1f, 1f, 1f, 1f, 1f, 0f, true, true, true, false, false);

    /** @param mainSide side of the dominant hand */
    public static Capabilities compute(Body body, Side mainSide) {
        Vitals v = body.vitals;
        Needs n = body.needs;
        Derived d = body.derived;
        float pain = d.totalPain;
        float consciousness = v.consciousness;
        boolean incapacitated = body.isIncapacitated();
        boolean unconscious = body.isUnconscious();
        float strength = d.strength;
        float over = d.overload;
        int weight = d.weightClass.level;

        float legs = leg(body, Side.LEFT) * leg(body, Side.RIGHT);
        PartState abdomen = body.part(BodyPart.ABDOMEN);
        float core = 1f - 0.14f * (1f - abdomen.muscle / 100f);
        float painMove = pain > PainSystem.AGONY ? 0.65f : pain > PainSystem.SEVERE ? 0.85f : pain > PainSystem.PAIN ? 0.95f : 1f;
        float lostConsciousness = 1f - consciousness / 100f;
        float consciousnessMove = 1f - 0.97f * lostConsciousness * lostConsciousness * lostConsciousness;
        float staminaMove = 0.4f + 0.6f * strength;
        float mood = body.mind.mood;
        float lethargy = mood < -50f ? 0.92f : 1f;
        float load = 1f - 0.25f * over;
        float stuffed = n.hunger > HungerSystem.FULL ? 0.95f : 1f;
        float bulk = Math.abs(weight) >= 3 ? 0.9f : 1f;
        float retching = n.vomiting > 0f ? 0.05f : 1f;
        float move = incapacitated ? 0f
                : legs * core * painMove * consciousnessMove * staminaMove * lethargy * load * stuffed * bulk * retching;

        float jump = Math.min(legJump(body, Side.LEFT), legJump(body, Side.RIGHT));
        if (pain > PainSystem.AGONY) jump *= 0.7f;
        jump *= (0.5f + 0.5f * strength) * (1f - 0.2f * over) * (weight >= 3 ? 0.85f : 1f);
        if (incapacitated || n.vomiting > 0f) jump = 0f;

        float arm = arm(body, mainSide);
        float trunk = 1f;
        // Cracked ribs, a dislocated spine or a broken neck: −40 % each
        if (body.part(BodyPart.THORAX).isFractured()) trunk *= 0.6f;
        if (body.part(BodyPart.THORAX).isDislocated()) trunk *= 0.6f;
        if (body.part(BodyPart.HEAD).isFractured()) trunk *= 0.6f;
        float painAttack = pain > PainSystem.SEVERE ? 0.85f : 1f;
        float frail = weight <= -3 ? 0.85f : 1f;
        float attackDamage = arm * trunk * painAttack * (0.3f + 0.7f * strength) * frail
                * SkillSystem.attackMultiplier(body.skills);
        float gloom = mood < -30f ? 0.9f : 1f;
        float attackSpeed = Math.max(0.3f, consciousness / 100f) * (pain > PainSystem.AGONY ? 0.8f : 1f)
                * (0.6f + 0.4f * strength) * gloom / (1f + over);
        float coldHands = v.bodyTemp < 34f ? 0.8f : 1f;
        float mining = armMining(body, mainSide) * painAttack * (0.4f + 0.6f * strength) * gloom * coldHands;

        float safeFall = 0f;
        for (BodyPart part : BodyPart.VALUES) {
            if (part.isLeg() && body.part(part).isFractured()) safeFall -= 1f;
        }
        if (over > 0.5f) safeFall -= 1f;

        boolean legBroken = false;
        boolean legMissing = false;
        for (BodyPart part : BodyPart.VALUES) {
            if (!part.isLeg()) continue;
            PartState s = body.part(part);
            if (s.isFractured() && !s.splinted) legBroken = true;
            if (s.amputated) legMissing = true;
        }
        boolean canSprint = !incapacitated && !legBroken && !legMissing && pain <= PainSystem.AGONY
                && consciousness >= ConsciousnessSystem.FAINTING && n.stamina > StaminaSystem.SPRINT_MIN
                && over < 1f && n.vomiting <= 0f;

        return new Capabilities(move, jump, attackDamage, attackSpeed, mining, safeFall, canSprint,
                !incapacitated && handUsable(body, mainSide),
                !incapacitated && handUsable(body, mainSide == Side.LEFT ? Side.RIGHT : Side.LEFT),
                incapacitated, unconscious);
    }

    /** Thigh and shin of one side: fractures, dislocations, muscle loss, amputation. */
    static float leg(Body body, Side side) {
        float f = 1f;
        float muscle = 0f;
        for (BodyPart part : BodyPart.leg(side)) {
            PartState s = body.part(part);
            if (s.amputated) return 0.55f;
            if (s.isFractured()) f *= s.splinted ? 0.85f : 0.65f;
            if (s.isDislocated()) f *= s.splinted ? 0.85f : 0.75f;
            muscle += s.muscle;
        }
        return f * (1f - 0.14f * (1f - muscle / 200f));
    }

    static float legJump(Body body, Side side) {
        float f = 1f;
        for (BodyPart part : BodyPart.leg(side)) {
            PartState s = body.part(part);
            if (s.amputated) return 0.4f;
            if (s.isFractured()) f = Math.min(f, s.splinted ? 0.75f : 0.5f);
            if (s.isDislocated()) f = Math.min(f, 0.7f);
        }
        return f;
    }

    /** Dominant arm strength: up to −33 % from muscle loss, much weaker when broken. */
    static float arm(Body body, Side side) {
        float muscle = 0f;
        float f = 1f;
        for (BodyPart part : BodyPart.arm(side)) {
            PartState s = body.part(part);
            if (s.amputated) return 0.1f;
            if (s.isFractured()) f = Math.min(f, 0.25f);
            if (s.isDislocated()) f = Math.min(f, 0.6f);
            muscle += s.muscle;
        }
        return f * (1f - 0.33f * (1f - muscle / 300f));
    }

    static float armMining(Body body, Side side) {
        float f = arm(body, side);
        PartState hand = body.part(BodyPart.arm(side).get(2));
        if (hand.isFractured()) f = Math.min(f, 0.2f);
        return f;
    }

    /** A broken or missing arm cannot hold and use items. */
    static boolean handUsable(Body body, Side side) {
        for (BodyPart part : BodyPart.arm(side)) {
            PartState s = body.part(part);
            if (s.amputated || s.isFractured()) return false;
        }
        return true;
    }
}
