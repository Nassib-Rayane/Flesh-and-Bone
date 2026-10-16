package com.fleshbone.event;

import java.util.Random;

import com.fleshbone.FleshBone;
import com.fleshbone.attach.FBAttachments;
import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.system.BrainSystem;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;

/**
 * What the body lets a player say (Casualties: Unknown): nothing while unconscious, groans below 50 % brain health,
 * slurred words below 82.5 % or with a dislocated jaw.
 */
@EventBusSubscriber(modid = FleshBone.MODID)
public final class SpeechEvents {
    private SpeechEvents() {}

    @SubscribeEvent
    static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        if (!BodyTicker.simulated(player)) return;
        Body body = player.getData(FBAttachments.BODY);
        if (body.isUnconscious() || body.vitals.cardiacArrest) {
            event.setCanceled(true);
            return;
        }
        float brain = body.vitals.brainHealth;
        if (brain < BrainSystem.MUTE) {
            event.setMessage(Component.translatable("fleshbone.chat.groan"));
            return;
        }
        float severity = 0f;
        if (brain < BrainSystem.SLURRED) severity = (BrainSystem.SLURRED - brain) / (BrainSystem.SLURRED - BrainSystem.MUTE);
        if (body.part(BodyPart.HEAD).isDislocated()) severity = Math.max(severity, 0.35f);
        if (severity > 0f) event.setMessage(Component.literal(slur(event.getRawText(), severity)));
    }

    /** Swallowed consonants, stretched vowels and stumbles, more the worse it gets (0–1). */
    public static String slur(String text, float severity) {
        Random rng = new Random(text.hashCode());
        StringBuilder out = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean vowel = "aeiouyAEIOUYéèêàùôî".indexOf(c) >= 0;
            float roll = rng.nextFloat();
            if (Character.isLetter(c) && !vowel && roll < severity * 0.25f) {
                if (rng.nextBoolean()) out.append('h');
                continue;
            }
            out.append(c);
            if (vowel && roll < severity * 0.35f) out.append(c);
            if (c == ' ' && roll < severity * 0.2f) out.append("... ");
        }
        return out.toString();
    }
}
