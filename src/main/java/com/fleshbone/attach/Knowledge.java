package com.fleshbone.attach;

import java.util.HashSet;
import java.util.Set;

import com.mojang.serialization.Codec;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * What a player has learnt, for intelligence experience: items already crafted once, and the deepest level reached.
 * Saved with the player and kept on death (knowledge is not lost with the body).
 */
public final class Knowledge {
    /** Intelligence XP for the first craft of an item (Casualties: Unknown: +10). */
    public static final float FIRST_CRAFT_XP = 10f;
    /** Intelligence XP for each new 16-block band reached below Y 48. */
    public static final float DEPTH_XP = 20f;
    public static final int DEPTH_START = 48;
    public static final int DEPTH_BAND = 16;

    public final Set<String> crafted = new HashSet<>();
    /** Lowest depth band reached (in blocks below {@link #DEPTH_START}, multiple of 16), -1 = none yet. */
    public int deepestBand = -1;

    public void save(ValueOutput output) {
        ValueOutput.TypedOutputList<String> list = output.list("crafted", Codec.STRING);
        for (String id : crafted) list.add(id);
        output.putInt("deepest_band", deepestBand);
    }

    public void load(ValueInput input) {
        crafted.clear();
        input.listOrEmpty("crafted", Codec.STRING).stream().forEach(crafted::add);
        deepestBand = input.getIntOr("deepest_band", -1);
    }

    public void copyFrom(Knowledge other) {
        crafted.clear();
        crafted.addAll(other.crafted);
        deepestBand = other.deepestBand;
    }
}
