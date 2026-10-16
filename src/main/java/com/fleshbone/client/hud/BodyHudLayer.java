package com.fleshbone.client.hud;

import com.fleshbone.client.ClientBody;
import com.fleshbone.config.FBConfig;
import com.fleshbone.sim.Body;
import com.fleshbone.sim.Vitals;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * Takes the place of the vanilla hearts: a row of ten blood drops (0.5 L each) where the hearts were, and the body
 * silhouette in the bottom-left corner.
 */
public final class BodyHudLayer implements GuiLayer {
    /** 7×9 blood drop. */
    private static final String[] DROP = {
            "...#...",
            "..###..",
            "..###..",
            ".#####.",
            ".#####.",
            "#######",
            "#######",
            ".#####.",
            "..###..",
    };
    private static final float LITRES_PER_DROP = Vitals.NORMAL_BLOOD_VOLUME / 10f;

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        Body body = ClientBody.get();
        if (body == null) return;
        float time = ClientBody.time();

        int left = graphics.guiWidth() / 2 - 91;
        int top = graphics.guiHeight() - mc.gui.leftHeight;
        drawBloodRow(graphics, body, left, top, time);
        mc.gui.leftHeight += 10;

        int monitorX = 4;
        if (FBConfig.SHOW_BODY_DOLL.get()) {
            float scale = FBConfig.HUD_SCALE.get().floatValue();
            int dollY = graphics.guiHeight() - Math.round(BodyDoll.GRID_H * scale) - 4;
            BodyDoll.draw(graphics, body, 4, dollY, scale, null, time);
            monitorX += Math.round(BodyDoll.GRID_W * scale) + 4;
        }
        // The small monitor stands in for the UNI-HEALTH readout when it is hidden
        if (FBConfig.UNI_HEALTH.get() == FBConfig.UniHealthMode.HIDDEN && ClientBody.vitalsVisible(mc.player)) {
            VitalsMonitor.draw(graphics, mc.font, body, monitorX, graphics.guiHeight() - VitalsMonitor.HEIGHT - 4, time);
        }
    }

    private static void drawBloodRow(GuiGraphicsExtractor g, Body body, int left, int top, float time) {
        float litres = body.vitals.bloodVolume;
        boolean bleeding = body.derived.totalBleed() > 0.001f;
        boolean critical = litres < 3.0f;
        for (int i = 0; i < 10; i++) {
            float fill = Math.max(0f, Math.min(1f, (litres - i * LITRES_PER_DROP) / LITRES_PER_DROP));
            int x = left + i * 8;
            int y = top;
            // Drops tremble while bleeding; the row shakes when blood is critically low
            if (bleeding && fill > 0f && fill < 1f) y += (int) Math.round(Math.sin(time * 10.0) * 0.6);
            if (critical) y += (int) Math.round(Math.sin(time * 25.0 + i) * 0.8);
            drawDrop(g, x, y, fill);
        }
    }

    /** Dark outline drop, filled with red from the bottom up to {@code fill}. */
    private static void drawDrop(GuiGraphicsExtractor g, int x, int y, float fill) {
        int rows = DROP.length;
        int filledFrom = rows - Math.round(fill * rows);
        for (int row = 0; row < rows; row++) {
            String line = DROP[row];
            int start = -1;
            for (int col = 0; col <= line.length(); col++) {
                boolean on = col < line.length() && line.charAt(col) == '#';
                if (on && start < 0) start = col;
                if (!on && start >= 0) {
                    int color = row >= filledFrom ? (row == filledFrom ? 0xFFFF4A4A : 0xFFC81414) : 0xFF3A1010;
                    g.fill(x + start, y + row, x + col, y + row + 1, color);
                    start = -1;
                }
            }
        }
    }
}
