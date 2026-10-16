package com.fleshbone.item;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import com.fleshbone.attach.FBAttachments;
import com.fleshbone.event.BodyTicker;
import com.fleshbone.sim.treatment.Treatments;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Holds three sips of water. Right-click a water source to fill it, right-click elsewhere to drink. The water is not
 * treated: each sip has a small chance of making you sick.
 */
public class CanteenItem extends Item {
    public static final int CAPACITY = 3;
    private static final float THIRST_PER_SIP = 25f;
    private static final float SICKNESS_CHANCE = 0.15f;

    public CanteenItem(Properties properties) {
        super(properties);
    }

    public static int sips(ItemStack stack) {
        Integer sips = stack.get(FBDataComponents.SIPS.get());
        return sips == null ? 0 : sips;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() == HitResult.Type.BLOCK && level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER)) {
            if (sips(stack) < CAPACITY) {
                stack.set(FBDataComponents.SIPS.get(), CAPACITY);
                level.playSound(player, player.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1f, 1f);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }
        if (sips(stack) > 0 && BodyTicker.simulated(player)) {
            player.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player && sips(stack) > 0) {
            stack.set(FBDataComponents.SIPS.get(), sips(stack) - 1);
            boolean sick = Treatments.drink(player.getData(FBAttachments.BODY), THIRST_PER_SIP, 12f, SICKNESS_CHANCE, 0f, 0f,
                    ThreadLocalRandom.current());
            if (sick) player.sendOverlayMessage(Component.translatable("fleshbone.message.bad_water"));
            player.getData(FBAttachments.RUNTIME).dirty = true;
        }
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 32;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.DRINK;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return sips(stack) < CAPACITY;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * sips(stack) / CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x3F76E4;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.fleshbone.canteen.desc").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("fleshbone.tooltip.sips", sips(stack), CAPACITY).withStyle(ChatFormatting.BLUE));
    }
}
