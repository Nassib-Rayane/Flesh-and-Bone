package com.fleshbone.client.hud;

import java.util.List;

import com.fleshbone.client.ClientBody;
import com.fleshbone.config.FBConfig;
import com.fleshbone.sim.Body;
import com.fleshbone.sim.Moodle;
import com.fleshbone.sim.Moodles;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.gui.GuiLayer;

/** Column of status icons on the right edge of the screen, worst first. */
public final class MoodleLayer implements GuiLayer {
    private static final int SIZE = 16;
    private static final int SPACING = 18;

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || !FBConfig.SHOW_MOODLES.get()) return;
        Body body = ClientBody.get();
        if (body == null) return;
        List<Moodle> moodles = Moodles.compute(body);
        int max = Math.min(moodles.size(), FBConfig.MAX_MOODLES.get());
        int x = graphics.guiWidth() - SIZE - 3;
        int y = 54;
        float time = ClientBody.time();
        for (int i = 0; i < max; i++) {
            draw(graphics, mc.font, moodles.get(i), x, y + i * SPACING, time);
        }
    }

    public static void draw(GuiGraphicsExtractor g, Font font, Moodle moodle, int x, int y, float time) {
        int background = background(moodle);
        if (moodle.level() >= 4 && !moodle.type().positive) {
            // Critical moodles pulse
            float pulse = 0.6f + 0.4f * (float) Math.sin(time * 7.0);
            background = (background & 0x00FFFFFF) | ((int) (0xE0 * pulse) << 24);
        }
        g.fill(x + 1, y, x + SIZE - 1, y + SIZE, background);
        g.fill(x, y + 1, x + SIZE, y + SIZE - 1, background);
        g.outline(x, y, SIZE, SIZE, 0x90000000);
        g.centeredText(font, moodle.type().symbol, x + SIZE / 2, y + 4, 0xFFFFFFFF);
    }

    static int background(Moodle moodle) {
        if (moodle.type().positive) {
            return switch (moodle.level()) {
                case 1 -> 0xE04E8A3E;
                case 2 -> 0xE0357A2E;
                case 3 -> 0xE0246A3A;
                default -> 0xE01A5A6A;
            };
        }
        return switch (moodle.level()) {
            case 1 -> 0xE0B89A2A;
            case 2 -> 0xE0C8641E;
            case 3 -> 0xE0B82020;
            default -> 0xE0700000;
        };
    }
}
