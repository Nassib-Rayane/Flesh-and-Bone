package com.fleshbone.damage;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.BodyPart.Side;
import com.fleshbone.sim.injury.HitDistribution;
import com.fleshbone.sim.injury.Injury;
import com.fleshbone.sim.injury.InjuryKind;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.item.ItemStack;

/** Decides what a vanilla damage source does to the body: which parts, which kind of trauma, which side effects. */
public final class DamageClassifier {
    public enum Mode {
        /** Vanilla handles it (death types, /kill, void). */
        PASSTHROUGH,
        IGNORE,
        /** One part, found by {@link HitLocator}. */
        SINGLE,
        FALL,
        EXPLOSION,
        BURN_PARTIAL,
        BURN_FULL,
        LEGS,
        HEAD,
        SYSTEMIC,
        CRUSH,
        FREEZE,
        RESPIRATORY,
        INTERNAL,
        ELECTRIC
    }

    /**
     * @param part only for {@link Mode#SINGLE}
     * @param side side the damage comes from
     */
    public record Plan(Mode mode, InjuryKind kind, BodyPart part, Side side, float contamination, float venom, boolean suffocates, boolean poisoned) {
        /** Spreads a total severity over the body. */
        public List<Injury> injuries(float severity, RandomGenerator rng) {
            List<Injury> out = new ArrayList<>();
            switch (mode) {
                case SINGLE -> out.add(new Injury(part, kind, severity, contamination, 0, venom));
                case FALL -> {
                    out.addAll(HitDistribution.fall(severity, rng));
                    if (kind == InjuryKind.PIERCE) {
                        out.add(Injury.of(rng.nextBoolean() ? BodyPart.SHIN_L : BodyPart.SHIN_R, InjuryKind.PIERCE, severity * 0.5f));
                    }
                }
                case EXPLOSION -> out.addAll(HitDistribution.explosion(severity, side, rng));
                case BURN_PARTIAL -> out.addAll(HitDistribution.burn(severity, false, rng));
                case BURN_FULL -> out.addAll(HitDistribution.burn(severity, true, rng));
                case LEGS -> {
                    out.add(new Injury(BodyPart.SHIN_L, kind, severity * 0.5f, contamination, 0, venom));
                    out.add(new Injury(BodyPart.SHIN_R, kind, severity * 0.5f, contamination, 0, venom));
                }
                case HEAD -> {
                    if (kind == InjuryKind.BLUNT && side == Side.CENTER) {
                        // Elytra into a wall: face and chest
                        out.add(Injury.of(BodyPart.HEAD, InjuryKind.BLUNT, severity * 0.6f));
                        out.add(Injury.of(BodyPart.THORAX, InjuryKind.BLUNT, severity * 0.6f));
                    } else {
                        out.add(Injury.of(BodyPart.HEAD, kind, severity));
                    }
                }
                case SYSTEMIC -> out.addAll(HitDistribution.systemic(severity));
                case CRUSH -> out.addAll(HitDistribution.crushTorso(severity));
                case FREEZE -> out.addAll(HitDistribution.freeze(severity));
                case INTERNAL -> {
                    out.add(Injury.of(BodyPart.THORAX, InjuryKind.BLUNT, severity * 0.6f));
                    out.add(Injury.of(BodyPart.ABDOMEN, InjuryKind.BLUNT, severity * 0.4f));
                    out.add(Injury.of(BodyPart.HEAD, InjuryKind.BLUNT, severity * 0.3f));
                }
                case ELECTRIC -> {
                    out.add(Injury.of(BodyPart.THORAX, InjuryKind.ELECTRIC, severity * 0.5f));
                    out.addAll(HitDistribution.burn(severity * 0.5f, false, rng));
                }
                case RESPIRATORY, PASSTHROUGH, IGNORE -> { }
            }
            return out;
        }

        /** Effects that are not tissue damage. */
        public void sideEffects(Body body, float severity) {
            if (mode == Mode.RESPIRATORY) body.vitals.spo2 = Math.max(0f, body.vitals.spo2 - severity * 0.4f);
            if (poisoned) body.needs.sickness = Math.min(100f, body.needs.sickness + severity * 0.5f);
        }

        /** Localized hits: only the armor piece over the part wears down. */
        public boolean localized() {
            return mode == Mode.SINGLE || mode == Mode.HEAD;
        }
    }

    private DamageClassifier() {}

    public static boolean isPassthrough(DamageSource source) {
        return source.is(FBTags.DamageTypes.PASSTHROUGH) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    public static Plan classify(LivingEntity target, DamageSource source, RandomGenerator rng) {
        Side side = HitLocator.sideOf(target, source.getSourcePosition());
        if (isPassthrough(source)) return plan(Mode.PASSTHROUGH, InjuryKind.BLUNT, side);
        if (source.is(FBTags.DamageTypes.IGNORED)) return plan(Mode.IGNORE, InjuryKind.BLUNT, side);
        if (source.is(FBTags.DamageTypes.STARVATION)) return plan(Mode.SYSTEMIC, InjuryKind.SYSTEMIC, side);
        if (source.is(FBTags.DamageTypes.RESPIRATORY)) return plan(Mode.RESPIRATORY, InjuryKind.BLUNT, side);
        if (source.is(FBTags.DamageTypes.CRUSH)) {
            return new Plan(Mode.CRUSH, InjuryKind.CRUSH, null, side, 0f, 0f, source.is(DamageTypes.IN_WALL), false);
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return plan(Mode.EXPLOSION, InjuryKind.EXPLOSION, side);
        if (source.is(FBTags.DamageTypes.ELECTRIC) || source.is(DamageTypeTags.IS_LIGHTNING)) return plan(Mode.ELECTRIC, InjuryKind.ELECTRIC, side);
        if (source.is(DamageTypeTags.IS_FIRE)) {
            if (source.is(DamageTypes.LAVA)) return plan(Mode.BURN_FULL, InjuryKind.BURN, side);
            if (source.is(FBTags.DamageTypes.LEG_CONTACT)) return plan(Mode.LEGS, InjuryKind.BURN, side);
            if (source.is(DamageTypeTags.IS_PROJECTILE)) return single(target, source, InjuryKind.BURN, 0f, 0f, rng);
            return plan(Mode.BURN_PARTIAL, InjuryKind.BURN, side);
        }
        if (source.is(DamageTypeTags.IS_FREEZING)) return plan(Mode.FREEZE, InjuryKind.FREEZE, side);
        if (source.is(FBTags.DamageTypes.HEAD_IMPACT)) {
            InjuryKind kind = source.is(DamageTypes.FALLING_ANVIL) ? InjuryKind.CRUSH
                    : source.is(DamageTypes.FALLING_STALACTITE) ? InjuryKind.PIERCE : InjuryKind.BLUNT;
            Side headSide = source.is(DamageTypes.FLY_INTO_WALL) ? Side.CENTER : Side.LEFT;
            return plan(Mode.HEAD, kind, headSide);
        }
        if (source.is(DamageTypeTags.IS_FALL)) {
            return plan(Mode.FALL, source.is(FBTags.DamageTypes.PIERCE) ? InjuryKind.PIERCE : InjuryKind.FALL, side);
        }
        if (source.is(FBTags.DamageTypes.INTERNAL)) return plan(Mode.INTERNAL, InjuryKind.BLUNT, side);
        if (source.is(FBTags.DamageTypes.SYSTEMIC)) {
            if (source.getDirectEntity() instanceof EvokerFangs) {
                return new Plan(Mode.LEGS, InjuryKind.BITE, null, side, 10f, 0f, false, false);
            }
            boolean poisoned = target.hasEffect(MobEffects.POISON);
            return new Plan(Mode.SYSTEMIC, InjuryKind.SYSTEMIC, null, side, 0f, 0f, false, poisoned);
        }
        if (source.is(FBTags.DamageTypes.LEG_CONTACT)) {
            return plan(Mode.LEGS, kindFromTags(source, InjuryKind.CUT), side);
        }
        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            float contamination = source.is(DamageTypes.SPIT) ? 20f : 4f;
            return single(target, source, kindFromTags(source, InjuryKind.PIERCE), contamination, 0f, rng);
        }
        if (source.is(DamageTypes.THORNS)) {
            BodyPart hand = target.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT ? BodyPart.HAND_L : BodyPart.HAND_R;
            return new Plan(Mode.SINGLE, InjuryKind.CUT, hand, side, 2f, 0f, false, false);
        }
        if (source.is(FBTags.DamageTypes.MELEE)) return melee(target, source, rng);
        return single(target, source, kindFromTags(source, InjuryKind.BLUNT), 0f, 0f, rng);
    }

    private static Plan plan(Mode mode, InjuryKind kind, Side side) {
        return new Plan(mode, kind, null, side, 0f, 0f, false, false);
    }

    private static Plan single(LivingEntity target, DamageSource source, InjuryKind kind, float contamination, float venom, RandomGenerator rng) {
        HitLocator.Hit hit = HitLocator.locate(target, source, rng);
        return new Plan(Mode.SINGLE, kind, hit.part(), hit.side(), contamination, venom, false, false);
    }

    private static InjuryKind kindFromTags(DamageSource source, InjuryKind fallback) {
        if (source.is(FBTags.DamageTypes.CUT)) return InjuryKind.CUT;
        if (source.is(FBTags.DamageTypes.PIERCE)) return InjuryKind.PIERCE;
        if (source.is(FBTags.DamageTypes.BLUNT)) return InjuryKind.BLUNT;
        return fallback;
    }

    /** The kind of a melee hit depends on the weapon, then on the attacker's natural weapons. */
    private static Plan melee(LivingEntity target, DamageSource source, RandomGenerator rng) {
        InjuryKind kind = InjuryKind.BLUNT;
        float contamination = 0f;
        float venom = 0f;
        Entity attacker = source.getEntity();
        if (source.is(DamageTypes.STING)) {
            kind = InjuryKind.PIERCE;
            venom = 10f;
        } else if (source.is(DamageTypes.MACE_SMASH)) {
            kind = InjuryKind.BLUNT;
        } else if (source.is(DamageTypes.SPEAR)) {
            kind = InjuryKind.PIERCE;
        } else if (attacker instanceof LivingEntity living) {
            ItemStack weapon = living.getWeaponItem();
            if (!weapon.isEmpty() && weapon.is(FBTags.Items.CUTTING_WEAPONS)) kind = InjuryKind.CUT;
            else if (!weapon.isEmpty() && weapon.is(FBTags.Items.PIERCING_WEAPONS)) kind = InjuryKind.PIERCE;
            else if (!weapon.isEmpty() && weapon.is(FBTags.Items.BLUNT_WEAPONS)) kind = InjuryKind.BLUNT;
            else if (living.is(FBTags.EntityTypes.CRUSHERS)) kind = InjuryKind.CRUSH;
            else if (living.is(FBTags.EntityTypes.BITERS)) kind = InjuryKind.BITE;
            else if (living.is(FBTags.EntityTypes.CLAWED)) kind = InjuryKind.CUT;
            else if (living instanceof Player) kind = InjuryKind.BLUNT;
            if (living.is(FBTags.EntityTypes.DIRTY_ATTACKERS)) contamination += 30f;
            if (living.is(FBTags.EntityTypes.VENOMOUS)) venom += 15f;
        }
        return single(target, source, kind, contamination, venom, rng);
    }
}
