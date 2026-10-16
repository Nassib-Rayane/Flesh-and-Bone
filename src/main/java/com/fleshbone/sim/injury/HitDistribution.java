package com.fleshbone.sim.injury;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.BodyPart.Side;

/** Spreads one damage event over body parts. Pure functions so the distribution can be unit tested. */
public final class HitDistribution {
    private HitDistribution() {}

    /**
     * Part hit at a relative height of the body (0 = feet, 1 = top of the head) on a given side.
     * Arms hang beside the torso, so a side hit at chest or belly height may land on them.
     */
    public static BodyPart partAtHeight(float relativeHeight, Side side, RandomGenerator rng) {
        Side limbSide = side == Side.CENTER ? (rng.nextBoolean() ? Side.LEFT : Side.RIGHT) : side;
        float armChance = side == Side.CENTER ? 0.15f : 0.4f;
        if (relativeHeight >= 0.82f) return BodyPart.HEAD;
        if (relativeHeight >= 0.62f) {
            return rng.nextFloat() < armChance ? BodyPart.arm(limbSide).get(0) : BodyPart.THORAX;
        }
        if (relativeHeight >= 0.47f) {
            if (rng.nextFloat() < armChance) return BodyPart.arm(limbSide).get(rng.nextFloat() < 0.6f ? 1 : 2);
            return BodyPart.ABDOMEN;
        }
        if (relativeHeight >= 0.25f) return BodyPart.leg(limbSide).get(0);
        return BodyPart.leg(limbSide).get(1);
    }

    /** Landing: shins take the brunt, harder falls reach the thighs, torso, hands and finally the head. */
    public static List<Injury> fall(float severity, RandomGenerator rng) {
        List<Injury> out = new ArrayList<>();
        if (severity <= 0f) return out;
        out.add(Injury.of(BodyPart.SHIN_L, InjuryKind.FALL, severity * jitter(rng, 0.6f)));
        out.add(Injury.of(BodyPart.SHIN_R, InjuryKind.FALL, severity * jitter(rng, 0.6f)));
        if (severity > 20f) {
            out.add(Injury.of(BodyPart.THIGH_L, InjuryKind.FALL, severity * jitter(rng, 0.25f)));
            out.add(Injury.of(BodyPart.THIGH_R, InjuryKind.FALL, severity * jitter(rng, 0.25f)));
        }
        if (severity > 30f) {
            BodyPart hand = rng.nextBoolean() ? BodyPart.HAND_L : BodyPart.HAND_R;
            out.add(Injury.of(hand, InjuryKind.FALL, severity * jitter(rng, 0.15f)));
        }
        if (severity > 40f) {
            out.add(Injury.of(BodyPart.ABDOMEN, InjuryKind.BLUNT, severity * jitter(rng, 0.2f)));
            out.add(Injury.of(BodyPart.THORAX, InjuryKind.BLUNT, severity * jitter(rng, 0.15f)));
        }
        if (severity > 60f) {
            out.add(Injury.of(BodyPart.HEAD, InjuryKind.BLUNT, severity * jitter(rng, 0.15f)));
        }
        return out;
    }

    /** Blast: 3–6 parts, favouring the side facing the explosion. */
    public static List<Injury> explosion(float severity, Side facing, RandomGenerator rng) {
        List<Injury> out = new ArrayList<>();
        int count = 3 + rng.nextInt(4);
        float share = severity * 1.2f / count;
        for (int n = 0; n < count; n++) {
            BodyPart part = randomPart(facing, rng);
            out.add(Injury.of(part, InjuryKind.EXPLOSION, share * jitter(rng, 1f)));
        }
        return out;
    }

    /** Fire: a few random parts, or every part when submerged in lava. */
    public static List<Injury> burn(float severity, boolean wholeBody, RandomGenerator rng) {
        List<Injury> out = new ArrayList<>();
        if (wholeBody) {
            for (BodyPart part : BodyPart.VALUES) out.add(Injury.of(part, InjuryKind.BURN, severity * 0.25f));
        } else {
            for (int n = 0; n < 3; n++) out.add(Injury.of(randomPart(Side.CENTER, rng), InjuryKind.BURN, severity * 0.4f));
        }
        return out;
    }

    /** Poison, magic, withering: a little muscle damage everywhere. */
    public static List<Injury> systemic(float severity) {
        List<Injury> out = new ArrayList<>();
        for (BodyPart part : BodyPart.VALUES) out.add(Injury.of(part, InjuryKind.SYSTEMIC, severity * 0.1f));
        return out;
    }

    /** Crushed chest: suffocating in a wall, entity cramming. */
    public static List<Injury> crushTorso(float severity) {
        return List.of(Injury.of(BodyPart.THORAX, InjuryKind.CRUSH, severity * 0.7f),
                Injury.of(BodyPart.ABDOMEN, InjuryKind.CRUSH, severity * 0.3f));
    }

    /** Frostbite: hands and shins first. */
    public static List<Injury> freeze(float severity) {
        return List.of(Injury.of(BodyPart.HAND_L, InjuryKind.FREEZE, severity * 0.3f),
                Injury.of(BodyPart.HAND_R, InjuryKind.FREEZE, severity * 0.3f),
                Injury.of(BodyPart.SHIN_L, InjuryKind.FREEZE, severity * 0.3f),
                Injury.of(BodyPart.SHIN_R, InjuryKind.FREEZE, severity * 0.3f));
    }

    /** Area weights roughly proportional to the part's surface; parts on {@code facing} count double. */
    public static BodyPart randomPart(Side facing, RandomGenerator rng) {
        float total = 0f;
        float[] weights = new float[BodyPart.COUNT];
        for (BodyPart part : BodyPart.VALUES) {
            float w = switch (part.region()) {
                case HEAD -> 0.8f;
                case TORSO -> 1.6f;
                case ARM -> 0.7f;
                case HAND -> 0.3f;
                case LEG -> 1.0f;
            };
            if (facing != Side.CENTER && part.side() == facing) w *= 2f;
            weights[part.ordinal()] = w;
            total += w;
        }
        float roll = rng.nextFloat() * total;
        for (BodyPart part : BodyPart.VALUES) {
            roll -= weights[part.ordinal()];
            if (roll <= 0f) return part;
        }
        return BodyPart.THORAX;
    }

    /** {@code base} ± 20 %. */
    private static float jitter(RandomGenerator rng, float base) {
        return base * (0.8f + 0.4f * rng.nextFloat());
    }
}
