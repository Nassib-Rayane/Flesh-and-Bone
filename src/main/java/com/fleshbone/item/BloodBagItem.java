package com.fleshbone.item;

import java.util.function.Consumer;

import com.fleshbone.sim.BloodType;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A filled blood bag: shows the donor's blood group. */
public class BloodBagItem extends MedicalItem {
    public BloodBagItem(Properties properties, MedicalAction action) {
        super(properties, action);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        BloodType type = stack.get(FBDataComponents.BLOOD_TYPE.get());
        tooltip.accept(Component.translatable("fleshbone.tooltip.blood_type", type == null ? "?" : type.display())
                .withStyle(ChatFormatting.DARK_RED));
        super.appendHoverText(stack, context, display, tooltip, flag);
    }
}
