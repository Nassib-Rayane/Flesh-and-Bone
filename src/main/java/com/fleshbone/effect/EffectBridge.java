package com.fleshbone.effect;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.Environment;
import com.fleshbone.sim.Needs;
import com.fleshbone.sim.PartState;
import com.fleshbone.sim.system.HungerSystem;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Vanilla status effects, translated into the body: poison sickens and cramps, wither eats the flesh, nausea turns the
 * stomach, hunger empties it, saturation fills it, fire resistance keeps the heat out, water breathing and the conduit
 * keep water from chilling, dolphin's grace makes swimming easy, blindness and darkness frighten, heroes feel loved.
 */
public final class EffectBridge {
    private EffectBridge() {}

    /** Called once per tick, before the body ticks; sets the environment flags the simulation reads. */
    public static void apply(ServerPlayer player, Body body, Environment env, float dt) {
        Needs n = body.needs;
        int poison = level(player, MobEffects.POISON);
        if (poison > 0) {
            n.sickness = Math.min(100f, n.sickness + 0.15f * poison * dt);
            ache(body.part(BodyPart.ABDOMEN), 10f + 5f * poison, dt);
        }
        int wither = level(player, MobEffects.WITHER);
        if (wither > 0) {
            // The flesh blackens and dies, a little everywhere
            for (BodyPart part : BodyPart.VALUES) {
                PartState s = body.part(part);
                if (s.amputated) continue;
                s.muscle = Math.max(0f, s.muscle - 0.08f * wither * dt);
                s.skin = Math.max(0f, s.skin - 0.05f * wither * dt);
            }
        }
        if (level(player, MobEffects.NAUSEA) > 0) n.sickness = Math.min(70f, n.sickness + 0.1f * dt);
        int hunger = level(player, MobEffects.HUNGER);
        if (hunger > 0) n.hunger = Math.max(Needs.MIN_HUNGER, n.hunger - HungerSystem.DRAIN * 2f * hunger * dt);
        int saturation = level(player, MobEffects.SATURATION);
        if (saturation > 0) n.hunger = Math.min(Needs.MAX_HUNGER, n.hunger + saturation * dt);
        env.heatProof = player.hasEffect(MobEffects.FIRE_RESISTANCE);
        env.swimEase = player.hasEffect(MobEffects.DOLPHINS_GRACE);
        if (player.hasEffect(MobEffects.WATER_BREATHING) || player.hasEffect(MobEffects.CONDUIT_POWER)) {
            env.waterTemp = Math.max(env.waterTemp, 22f);
        }
        if (player.hasEffect(MobEffects.BLINDNESS) || player.hasEffect(MobEffects.DARKNESS)) body.mind.darkness += dt;
        if (player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)) env.comfort = true;
    }

    /** 0 without the effect, else its amplifier + 1. */
    private static int level(ServerPlayer player, Holder<MobEffect> effect) {
        MobEffectInstance instance = player.getEffect(effect);
        return instance == null ? 0 : instance.getAmplifier() + 1;
    }

    private static void ache(PartState s, float floor, float dt) {
        if (s.pain < floor) s.pain = Math.min(floor, s.pain + 2f * dt);
    }
}
