package com.fleshbone.data;

import com.fleshbone.config.FBConfig;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Weight of carried items for encumbrance. The {@code item_weights} data map wins; otherwise the weight is guessed from
 * what the item is: armor from its protection, weapons and tools from their damage, food, blocks (light, so building
 * stays possible) and small items. Shulker boxes and bundles weigh what they hold.
 */
public final class ItemWeights {
    public static final float BLOCK = 0.03f;
    public static final float FOOD = 0.15f;
    public static final float POTION = 0.3f;
    public static final float SMALL = 0.05f;

    private ItemWeights() {}

    /** Everything the player carries and wears, in kg. */
    public static float carried(Player player) {
        Inventory inventory = player.getInventory();
        float total = 0f;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) total += of(inventory.getItem(slot));
        return total * FBConfig.ITEM_WEIGHT_SCALE.get().floatValue();
    }

    /** A whole stack, contents included. */
    public static float of(ItemStack stack) {
        return of(stack, 0);
    }

    private static float of(ItemStack stack, int depth) {
        if (stack.isEmpty()) return 0f;
        float total = unit(stack) * stack.getCount();
        if (depth < 2) {
            ItemContainerContents container = stack.get(DataComponents.CONTAINER);
            if (container != null) {
                for (ItemStack inner : (Iterable<ItemStack>) container.nonEmptyItemCopyStream()::iterator) total += of(inner, depth + 1);
            }
            BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
            if (bundle != null) {
                for (ItemStack inner : (Iterable<ItemStack>) bundle.itemCopyStream()::iterator) total += of(inner, depth + 1);
            }
        }
        return total;
    }

    /** One item, without contents. */
    public static float unit(ItemStack stack) {
        FBDataMaps.ItemWeight data = stack.getData(FBDataMaps.ITEM_WEIGHTS);
        if (data != null) return data.weight();
        ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable != null) {
            double armor = modifiers.compute(Attributes.ARMOR, 0.0, equippable.slot());
            double toughness = modifiers.compute(Attributes.ARMOR_TOUGHNESS, 0.0, equippable.slot());
            return (float) (0.3 + 0.6 * armor + 0.4 * toughness);
        }
        double damage = modifiers.compute(Attributes.ATTACK_DAMAGE, 0.0, EquipmentSlot.MAINHAND);
        if (damage > 0.0 || stack.has(DataComponents.TOOL) || stack.has(DataComponents.WEAPON)) return (float) (0.8 + 0.25 * damage);
        if (stack.has(DataComponents.FOOD)) return FOOD;
        if (stack.has(DataComponents.POTION_CONTENTS)) return POTION;
        if (stack.getItem() instanceof BlockItem) return BLOCK;
        return SMALL;
    }
}
