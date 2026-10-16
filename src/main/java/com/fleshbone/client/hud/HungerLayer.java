package com.fleshbone.client.hud;

import com.fleshbone.client.ClientBody;
import com.fleshbone.config.FBSettings;
import com.fleshbone.sim.Body;
import com.fleshbone.sim.Needs;
import com.fleshbone.sim.system.HungerSystem;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * Casualties: Unknown hunger in place of the vanilla food bar: ten drumsticks of 10 points each, filling from the
 * right; past 100 (satiated) they get a golden rim. The vanilla bar comes back if the C:U hunger is turned off.
 */
public final class HungerLayer implements GuiLayer {
    private static final String[] MEAT = {
            "...###.",
            "..#####",
            "..#####",
            ".####..",
            "###....",
            "#.#....",
    };
    private final GuiLayer vanilla;

    public HungerLayer(GuiLayer vanilla) {
        this.vanilla = vanilla;
    }

    @Override
    public void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        Body body = ClientBody.get();
        if (body == null || !FBSettings.hunger()) {
            vanilla.render(g, deltaTracker);
            return;
        }
        if (mc.options.hideGui || mc.player == null || mc.player.isCreative() || mc.player.isSpectator()) return;
        int right = g.guiWidth() / 2 + 91;
        int top = g.guiHeight() - mc.gui.rightHeight;
        float hunger = body.needs.hunger;
        float extra = Math.max(0f, (hunger - HungerSystem.SATIATED) / (Needs.MAX_HUNGER - HungerSystem.SATIATED));
        float shake = hunger < 10f ? (float) Math.sin(ClientBody.time() * 18.0) : 0f;
        for (int i = 0; i < 10; i++) {
            float fill = Math.max(0f, Math.min(1f, (hunger - i * 10f) / 10f));
            int x = right - 8 - i * 8;
            int y = top + 2 + Math.round(shake);
            drawMeat(g, x, y, fill, i < Math.round(extra * 10f));
        }
        mc.gui.rightHeight += 10;
    }

    private static void drawMeat(GuiGraphicsExtractor g, int x, int y, float fill, boolean golden) {
        int rows = MEAT.length;
        int filledFrom = rows - Math.round(fill * rows);
        for (int row = 0; row < rows; row++) {
            String line = MEAT[row];
            for (int col = 0; col < line.length(); col++) {
                if (line.charAt(col) != '#') continue;
                boolean bone = row >= 4;
                int color = row >= filledFrom ? (bone ? 0xFFF0E6D0 : 0xFFC2662E) : (bone ? 0xFF4A4438 : 0xFF3A2414);
                g.fill(x + col, y + row + 1, x + col + 1, y + row + 2, color);
            }
        }
        if (golden) g.fill(x + 2, y, x + 7, y + 1, 0xFFFFD84A);
    }
}
