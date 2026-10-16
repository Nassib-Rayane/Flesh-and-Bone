package com.fleshbone.damage;

import com.fleshbone.FleshBone;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

/** Data-driven classification: modpacks can extend these tags to teach the mod about their damage types and mobs. */
public final class FBTags {
    private FBTags() {}

    public static final class DamageTypes {
        /** Reaches vanilla health untouched (our own death types, /kill...). */
        public static final TagKey<DamageType> PASSTHROUGH = tag("passthrough");
        /** Does nothing to the body. */
        public static final TagKey<DamageType> IGNORED = tag("ignored");
        public static final TagKey<DamageType> BLUNT = tag("blunt");
        public static final TagKey<DamageType> CUT = tag("cut");
        public static final TagKey<DamageType> PIERCE = tag("pierce");
        /** Crushes the chest (suffocation in a block, cramming). */
        public static final TagKey<DamageType> CRUSH = tag("crush");
        /** Spread over the whole body (magic, poison, withering). */
        public static final TagKey<DamageType> SYSTEMIC = tag("systemic");
        /** Lack of air: lowers oxygen instead of hurting a part. */
        public static final TagKey<DamageType> RESPIRATORY = tag("respiratory");
        /** Lands on the head (falling blocks, stalactites, elytra crash). */
        public static final TagKey<DamageType> HEAD_IMPACT = tag("head_impact");
        /** Touches the feet and shins (cactus, berry bushes, magma, campfire, dripstone). */
        public static final TagKey<DamageType> LEG_CONTACT = tag("leg_contact");
        /** Hurts the organs through the body (sonic boom). */
        public static final TagKey<DamageType> INTERNAL = tag("internal");
        /** Lightning and electricity: burns and heart rhythm. */
        public static final TagKey<DamageType> ELECTRIC = tag("electric");
        /** Vanilla starvation. */
        public static final TagKey<DamageType> STARVATION = tag("starvation");
        /** Melee hits whose kind depends on the attacker and its weapon. */
        public static final TagKey<DamageType> MELEE = tag("melee");

        private static TagKey<DamageType> tag(String path) {
            return TagKey.create(Registries.DAMAGE_TYPE, FleshBone.id(path));
        }
    }

    public static final class EntityTypes {
        public static final TagKey<EntityType<?>> BITERS = tag("biters");
        public static final TagKey<EntityType<?>> CLAWED = tag("clawed");
        public static final TagKey<EntityType<?>> CRUSHERS = tag("crushers");
        /** Attacks carry germs (rotting mobs). */
        public static final TagKey<EntityType<?>> DIRTY_ATTACKERS = tag("dirty_attackers");
        public static final TagKey<EntityType<?>> VENOMOUS = tag("venomous");

        private static TagKey<EntityType<?>> tag(String path) {
            return TagKey.create(Registries.ENTITY_TYPE, FleshBone.id(path));
        }
    }

    public static final class Items {
        public static final TagKey<Item> CUTTING_WEAPONS = tag("cutting_weapons");
        public static final TagKey<Item> PIERCING_WEAPONS = tag("piercing_weapons");
        public static final TagKey<Item> BLUNT_WEAPONS = tag("blunt_weapons");

        private static TagKey<Item> tag(String path) {
            return TagKey.create(Registries.ITEM, FleshBone.id(path));
        }
    }
}
