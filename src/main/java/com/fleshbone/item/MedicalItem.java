package com.fleshbone.item;

import java.util.function.Consumer;

import com.fleshbone.attach.FBAttachments;
import com.fleshbone.event.BodyTicker;
import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyPart;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * A medical supply. Right-click to treat yourself (the most relevant part is chosen for you, in a hurry); use the
 * health panel (H) to pick the part and do it properly; right-click another player to open their health panel.
 */
public class MedicalItem extends Item {
    private final MedicalAction action;

    public MedicalItem(Properties properties, MedicalAction action) {
        super(properties);
        this.action = action;
    }

    public MedicalAction action() {
        return action;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!BodyTicker.simulated(player)) return InteractionResult.PASS;
        Body body = player.getData(FBAttachments.BODY);
        boolean possible = action.target() == MedicalAction.Target.BODY || action.autoTarget(body) != null;
        if (!possible) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendOverlayMessage(Component.translatable("fleshbone.message.nothing_to_treat"));
            }
            return InteractionResult.FAIL;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player && BodyTicker.simulated(player)) {
            Body body = player.getData(FBAttachments.BODY);
            BodyPart part = action.autoTarget(body);
            TreatmentService.treat(player, player, stack, part, action.quickQuality());
        }
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return action.useTicks();
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return action.animation();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        if (stack.isDamageableItem()) {
            int left = stack.getMaxDamage() - stack.getDamageValue();
            tooltip.accept(Component.translatable("fleshbone.tooltip.uses", left).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
