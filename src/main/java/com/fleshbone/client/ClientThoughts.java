package com.fleshbone.client;

import java.util.concurrent.ThreadLocalRandom;

import com.fleshbone.FleshBone;
import com.fleshbone.network.FBPayloads;
import com.fleshbone.sim.Body;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** Shows the character's thoughts above the hotbar. A damaged brain scrambles them into random symbols. */
@EventBusSubscriber(modid = FleshBone.MODID, value = Dist.CLIENT)
public final class ClientThoughts {
    private static final String SYMBOLS = "#%&*@?!~^$+=<>/\\|";
    /** Below this brain health, thoughts start to break up. */
    private static final float SCRAMBLE_BELOW = 94f;

    private ClientThoughts() {}

    @SubscribeEvent
    static void registerHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(FBPayloads.Thought.TYPE, (payload, context) -> show(payload));
    }

    private static void show(FBPayloads.Thought thought) {
        Minecraft mc = Minecraft.getInstance();
        String part = thought.partKey().isEmpty() ? "" : I18n.get(thought.partKey());
        String text = I18n.get(thought.key(), part);
        Body body = ClientBody.get();
        if (body != null && body.vitals.brainHealth < SCRAMBLE_BELOW) {
            text = scramble(text, Math.min(1f, (SCRAMBLE_BELOW - body.vitals.brainHealth) / 64f));
        }
        mc.gui.setOverlayMessage(Component.literal(text), false);
    }

    /** Replaces a fraction of the letters with random symbols. */
    static String scramble(String text, float fraction) {
        StringBuilder out = new StringBuilder(text.length());
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            out.append(Character.isLetter(c) && rng.nextFloat() < fraction ? SYMBOLS.charAt(rng.nextInt(SYMBOLS.length())) : c);
        }
        return out.toString();
    }
}
