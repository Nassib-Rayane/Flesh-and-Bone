package com.fleshbone.client;

import com.fleshbone.attach.FBAttachments;
import com.fleshbone.event.BodyTicker;
import com.fleshbone.sim.Body;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;

/** Access to the local player's synced body. */
public final class ClientBody {
    private ClientBody() {}

    /** The local player's body, or {@code null} when there is no player or its body is not simulated. */
    public static Body get() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.isAlive() || !BodyTicker.simulated(player)) return null;
        return player.getData(FBAttachments.BODY);
    }

    /** Whether exact vital signs are visible: config override, or a vitals monitor anywhere in the inventory. */
    public static boolean vitalsVisible(Player player) {
        if (com.fleshbone.config.FBConfig.ALWAYS_SHOW_VITALS.get()) return true;
        if (player == null) return false;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(com.fleshbone.item.FBItems.VITALS_MONITOR.get())) return true;
        }
        return false;
    }

    /** Seconds since the client started, for animations. */
    public static float time() {
        return (float) (net.minecraft.util.Util.getMillis() % 1_000_000L) / 1000f;
    }
}
