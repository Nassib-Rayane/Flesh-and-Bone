package com.fleshbone.client.procedure;

import com.fleshbone.sim.procedure.ExtractProcedure;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Clear the blood, guide the tip down the wound channel, grip the fragment, bring it back up slowly. */
public class ExtractScreen extends ProcedureScreen<ExtractProcedure> {
    public ExtractScreen(Screen parent, Completion completion, boolean tweezers) {
        super("extract", Component.translatable(tweezers ? "fleshbone.procedure.extract" : "fleshbone.procedure.extract_hands"),
                new ExtractProcedure(tweezers, System.nanoTime()), parent, completion);
    }

    @Override
    protected void drawWork(GuiGraphicsExtractor g) {
        ExtractProcedure p = procedure;
        rect(g, 0f, 0f, 1f, 1f, 0xFF8E3A32);
        float[][] channel = p.channel();
        int wall = size(p.halfWidth * 2f + 0.03f);
        int inside = size(p.halfWidth * 2f);
        for (int i = 0; i + 1 < channel.length; i++) nline(g, channel[i][0], channel[i][1], channel[i + 1][0], channel[i + 1][1], wall, 0xFF6A1E1A);
        for (int i = 0; i + 1 < channel.length; i++) nline(g, channel[i][0], channel[i][1], channel[i + 1][0], channel[i + 1][1], inside, 0xFF2A0606);
        disc(g, channel[0][0], channel[0][1], p.halfWidth * 1.4f, 0xFF3A0A08);
        // The fragment
        float fx = p.fragmentX();
        float fy = p.fragmentY();
        if (!p.stepKey().equals("clear")) {
            rect(g, fx - 0.018f, fy - 0.012f, fx + 0.02f, fy + 0.014f, 0xFF707680);
            rect(g, fx - 0.01f, fy - 0.006f, fx + 0.008f, fy + 0.002f, 0xFFA0A8B4);
        }
        for (float[] b : p.blood()) disc(g, b[0], b[1], 0.055f, BLOOD);
        if (p.stepKey().equals("grip")) {
            ring(g, channel[channel.length - 1][0], channel[channel.length - 1][1], 0.05f, alpha(HEADER, 0.4f + 0.4f * pulse(5f)));
            float grip = Math.min(1f, p.grip());
            rect(g, fx - 0.06f, fy + 0.06f, fx - 0.06f + 0.12f * grip, fy + 0.07f, GOOD);
        } else if (p.stepKey().equals("withdraw")) {
            // Pull gauge beside the fragment: the mark is the safe speed
            float speed = Math.min(1.5f, p.pullSpeed() / ExtractProcedure.MAX_SPEED);
            float gx = fx + 0.06f;
            rect(g, gx, fy - 0.06f, gx + 0.015f, fy + 0.06f, 0xFF2A0606);
            int color = speed > 1f ? BAD : speed > 0.7f ? WARN : GOOD;
            rect(g, gx, fy + 0.06f - 0.08f * speed, gx + 0.015f, fy + 0.06f, color);
            rect(g, gx - 0.005f, fy - 0.021f, gx + 0.02f, fy - 0.019f, TEXT);
        }
        // The instrument, from the tip up out of the wound
        if (!p.stepKey().equals("clear")) {
            float tx = p.tipX();
            float ty = p.tipY();
            int color = p.touching() ? BAD : p.tweezers ? METAL : SKIN;
            int width = p.tweezers ? 2 : size(0.035f);
            float spread = p.stepKey().equals("withdraw") || p.stepKey().equals("grip") ? 0.008f : 0.02f;
            nline(g, tx - spread, ty, tx - 0.03f, -0.1f, width, color);
            nline(g, tx + spread, ty, tx + 0.03f, -0.1f, width, color);
        }
    }

    @Override
    protected void drawTool(GuiGraphicsExtractor g, int x, int y) {
        if (procedure.stepKey().equals("clear")) {
            g.fill(x - 5, y - 5, x + 5, y + 5, 0xE0F4F0E8);
        } else {
            super.drawTool(g, x, y);
        }
    }

    @Override
    protected Component status() {
        return switch (procedure.stepKey()) {
            case "clear" -> Component.translatable(key() + ".status.clear", procedure.blood().size());
            case "grip" -> Component.translatable(key() + ".status.grip", percent(Math.min(1f, procedure.grip())));
            case "withdraw" -> procedure.touching() ? Component.translatable(key() + ".status.touching")
                    : procedure.pullSpeed() > ExtractProcedure.MAX_SPEED ? Component.translatable(key() + ".status.too_fast")
                    : Component.translatable(key() + ".status.withdraw", percent(procedure.withdrawn()));
            default -> procedure.touching() ? Component.translatable(key() + ".status.touching") : null;
        };
    }
}
