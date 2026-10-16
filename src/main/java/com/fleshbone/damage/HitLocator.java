package com.fleshbone.damage;

import java.util.Optional;
import java.util.random.RandomGenerator;

import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.BodyPart.Side;
import com.fleshbone.sim.injury.HitDistribution;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Works out which body part a localized hit lands on, from the geometry of the attack. */
public final class HitLocator {
    /** Mobs aim a bit below their own eyes: mostly chest and belly, sometimes the head. */
    private static final double MOB_AIM_DROP = 0.45;

    private HitLocator() {}

    public record Hit(BodyPart part, Side side) {}

    public static Hit locate(LivingEntity target, DamageSource source, RandomGenerator rng) {
        Side side = sideOf(target, source.getSourcePosition());
        float height = relativeHeight(target, source, rng);
        return new Hit(HitDistribution.partAtHeight(height, side, rng), side);
    }

    /** 0 = feet, 1 = top of the head. */
    static float relativeHeight(LivingEntity target, DamageSource source, RandomGenerator rng) {
        Entity direct = source.getDirectEntity();
        Entity attacker = source.getEntity();
        double bottom = target.getY();
        double height = Math.max(0.1, target.getBbHeight());
        double y;
        if (direct != null && direct != attacker) {
            // A projectile: where it is when it connects
            y = direct.getY() + direct.getBbHeight() * 0.5;
        } else if (attacker instanceof Player player) {
            y = aimedHeight(player, target).orElse(player.getEyeY() - MOB_AIM_DROP);
        } else if (attacker != null) {
            y = attacker.getEyeY() - MOB_AIM_DROP + rng.nextGaussian() * 0.15 * height;
        } else {
            return rng.nextFloat();
        }
        return (float) Math.max(0.0, Math.min(1.0, (y - bottom) / height));
    }

    /** Where a player's crosshair crosses the target's hitbox. */
    static Optional<Double> aimedHeight(Player player, LivingEntity target) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1f).scale(8.0));
        return target.getBoundingBox().inflate(0.15).clip(eye, end).map(hit -> hit.y);
    }

    /** Which side of the target the source is on, relative to where the target faces. */
    public static Side sideOf(Entity target, Vec3 source) {
        if (source == null) return Side.CENTER;
        Vec3 look = Vec3.directionFromRotation(0f, target.getYRot());
        double sx = source.x - target.getX();
        double sz = source.z - target.getZ();
        double length = Math.sqrt(sx * sx + sz * sz);
        if (length < 1.0e-4) return Side.CENTER;
        // Facing south (+Z), east (+X) is on the left
        double cross = (look.z * sx - look.x * sz) / length;
        if (cross > 0.35) return Side.LEFT;
        if (cross < -0.35) return Side.RIGHT;
        return Side.CENTER;
    }
}
