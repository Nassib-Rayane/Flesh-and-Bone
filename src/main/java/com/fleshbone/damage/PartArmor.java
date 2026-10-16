package com.fleshbone.damage;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.injury.Injury;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/**
 * Armor protects only what it covers. Each piece is scaled so that a full set gives the same protection as vanilla:
 * a diamond helmet (3 points) protects the head like 20 points of armor.
 */
public final class PartArmor {
    private PartArmor() {}

    /** The armor slot covering a part first, and a secondary slot that helps at half strength (or {@code null}). */
    static EquipmentSlot primary(BodyPart part) {
        return switch (part.region()) {
            case HEAD -> EquipmentSlot.HEAD;
            case TORSO, ARM -> EquipmentSlot.CHEST;
            case HAND -> null;
            case LEG -> part == BodyPart.THIGH_L || part == BodyPart.THIGH_R ? EquipmentSlot.LEGS : EquipmentSlot.FEET;
        };
    }

    static EquipmentSlot secondary(BodyPart part) {
        if (part == BodyPart.ABDOMEN) return EquipmentSlot.LEGS;
        if (part == BodyPart.SHIN_L || part == BodyPart.SHIN_R) return EquipmentSlot.LEGS;
        return null;
    }

    public static Set<EquipmentSlot> coveringSlots(BodyPart part) {
        Set<EquipmentSlot> slots = EnumSet.noneOf(EquipmentSlot.class);
        EquipmentSlot primary = primary(part);
        EquipmentSlot secondary = secondary(part);
        if (primary != null) slots.add(primary);
        if (secondary != null) slots.add(secondary);
        return slots;
    }

    /** Full-set equivalence: armor points of a diamond piece in each slot. */
    private static float reference(EquipmentSlot slot) {
        return switch (slot) {
            case CHEST -> 8f;
            case LEGS -> 6f;
            default -> 3f;
        };
    }

    public static List<Injury> reduce(LivingEntity entity, DamageSource source, List<Injury> injuries, float damageScale) {
        if (source.is(DamageTypeTags.BYPASSES_ARMOR) || injuries.isEmpty()) return injuries;
        List<Injury> out = new ArrayList<>(injuries.size());
        for (Injury injury : injuries) {
            float damage = injury.severity() / damageScale;
            float reduced = reduce(entity, source, injury.part(), damage);
            out.add(injury.withSeverity(reduced * damageScale));
        }
        return out;
    }

    public static float reduce(LivingEntity entity, DamageSource source, BodyPart part, float damage) {
        float armor = 0f;
        float toughness = 0f;
        EquipmentSlot primary = primary(part);
        EquipmentSlot secondary = secondary(part);
        if (primary != null) {
            float[] values = slotValues(entity, primary);
            float scale = 20f / reference(primary);
            armor += values[0] * scale;
            toughness += values[1] * 4f;
        }
        if (secondary != null) {
            float[] values = slotValues(entity, secondary);
            float scale = 20f / reference(secondary);
            armor += values[0] * scale * 0.5f;
            toughness += values[1] * 2f;
        }
        if (armor <= 0f) return damage;
        return CombatRules.getDamageAfterAbsorb(entity, damage, source, Math.min(armor, 30f), toughness);
    }

    /** Flat armor and toughness granted by the item worn in a slot. */
    private static float[] slotValues(LivingEntity entity, EquipmentSlot slot) {
        ItemStack stack = entity.getItemBySlot(slot);
        float[] values = new float[2];
        if (stack.isEmpty()) return values;
        stack.forEachModifier(slot, (attribute, modifier) -> {
            if (modifier.operation() != AttributeModifier.Operation.ADD_VALUE) return;
            if (attribute.value() == Attributes.ARMOR.value()) values[0] += (float) modifier.amount();
            else if (attribute.value() == Attributes.ARMOR_TOUGHNESS.value()) values[1] += (float) modifier.amount();
        });
        return values;
    }
}
