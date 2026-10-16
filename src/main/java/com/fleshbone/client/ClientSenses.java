package com.fleshbone.client;

import com.fleshbone.FleshBone;
import com.fleshbone.sim.Body;
import com.fleshbone.sim.system.Encumbrance;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Damaged hearing muffles every sound and rings in the ears; also keeps the client's carrying capacity in step. */
@EventBusSubscriber(modid = FleshBone.MODID, value = Dist.CLIENT)
public final class ClientSenses {
    private static float appliedGain = 1f;
    private static int ringTimer;

    private ClientSenses() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Body body = ClientBody.get();
        if (mc.player != null) Encumbrance.baseCapacity = com.fleshbone.config.FBConfig.CARRY_CAPACITY.get().floatValue();
        float gain = body == null ? 1f : body.senses.hearing();
        if (Math.abs(gain - appliedGain) > 0.001f) {
            for (SoundSource source : SoundSource.values()) {
                if (source != SoundSource.UI) mc.getSoundManager().updateCategoryVolume(source, gain);
            }
            appliedGain = gain;
        }
        if (body != null && body.senses.tinnitus > 0f && !mc.isPaused() && --ringTimer <= 0) {
            float volume = Math.min(0.25f, body.senses.tinnitus / 240f);
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 2f, volume));
            ringTimer = 30;
        }
    }
}
