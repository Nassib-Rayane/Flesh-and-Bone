package com.fleshbone.client.hud;

import com.fleshbone.client.ClientBody;
import com.fleshbone.config.FBConfig;
import com.fleshbone.sim.Body;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.gui.GuiLayer;

/** Ten water drops above the hunger bar, emptying from the left like the vanilla food bar. */
public final class ThirstLayer implements GuiLayer {
    private static final String[] DROP = {
            "..#..",
            ".###.",
            "#####",
            "#####",
            ".###.",
    };

    @Override
    public void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || !FBConfig.THIRST.get()) return;
        Body body = ClientBody.get();
        if (body == null) return;
        int right = g.guiWidth() / 2 + 91;
        int top = g.guiHeight() - mc.gui.rightHeight;
        float thirst = Math.min(100f, body.needs.thirst);
        float shake = body.needs.thirst < 20f ? (float) Math.sin(ClientBody.time() * 20.0) : 0f;
        for (int i = 0; i < 10; i++) {
            // Rightmost drop is the first to fill, as with hunger
            float fill = Math.max(0f, Math.min(1f, (thirst - i * 10f) / 10f));
            int x = right - 8 - i * 8;
            int y = top + 2 + Math.round(shake);
            drawDrop(g, x, y, fill);
        }
        mc.gui.rightHeight += 10;
    }

    private static void drawDrop(GuiGraphicsExtractor g, int x, int y, float fill) {
        for (int row = 0; row < DROP.length; row++) {
            String line = DROP[row];
            int start = -1;
            for (int col = 0; col <= line.length(); col++) {
                boolean on = col < line.length() && line.charAt(col) == '#';
                if (on && start < 0) start = col;
                if (!on && start >= 0) {
                    boolean filled = (DROP.length - row) <= Math.round(fill * DROP.length);
                    g.fill(x + start, y + row, x + col, y + row + 1, filled ? 0xFF3F8CFF : 0xFF14243A);
                    start = -1;
                }
            }
        }
    }
}
