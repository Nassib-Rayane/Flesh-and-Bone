package com.fleshbone.client.procedure;

import com.fleshbone.sim.procedure.AmputationProcedure;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Check the tourniquet, cut round the limb, saw the bone in long straight strokes, tie off the vessels. */
public class AmputationScreen extends ProcedureScreen<AmputationProcedure> {
    private static final float CX = AmputationProcedure.CX;
    private static final float CY = AmputationProcedure.CY;
    private static final float R = AmputationProcedure.RADIUS;

    public AmputationScreen(Screen parent, Completion completion, boolean tourniquet) {
        super("amputation", new AmputationProcedure(tourniquet, strength()), parent, completion);
    }

    @Override
    protected void drawWork(GuiGraphicsExtractor g) {
        AmputationProcedure p = procedure;
        String step = p.stepKey();
        // Cross-section of the limb: skin, muscle, bone
        disc(g, CX, CY, R + 0.02f, SKIN);
        disc(g, CX, CY, R - 0.03f, FLESH);
        disc(g, CX, CY, 0.09f, BONE);
        disc(g, CX, CY, 0.05f, 0xFFC8B890);
        if (step.equals("check") || step.equals("incise")) {
            ring(g, CX, CY, R, alpha(HEADER, 0.4f + 0.4f * pulse(4f)));
        }
        // The incision so far
        float cut = step.equals("check") ? 0f : step.equals("incise") ? p.incised() : 1f;
        for (float a = 0f; a < 360f * cut; a += 2f) {
            double r = Math.toRadians(a - 90f);
            disc(g, px(CX + R * (float) Math.cos(r)), py(CY + R * (float) Math.sin(r)), 1, BLOOD);
        }
        if (step.equals("saw") || step.equals("ligate")) {
            dotted(g, 0.08f, AmputationProcedure.SAW_LINE, 0.92f, AmputationProcedure.SAW_LINE, alpha(HEADER, 0.7f));
            float depth = step.equals("saw") ? p.depth() : 1f;
            rect(g, CX - 0.09f, AmputationProcedure.SAW_LINE - 0.004f, CX - 0.09f + 0.18f * Math.min(1f, depth), AmputationProcedure.SAW_LINE + 0.004f, BLOOD_DARK);
        }
        if (step.equals("ligate")) {
            for (float[] v : p.vessels) {
                if (v[2] >= 1f) {
                    disc(g, v[0], v[1], 0.018f, 0xFF5A1010);
                    nline(g, v[0] - 0.02f, v[1], v[0] + 0.02f, v[1], 1, CLOTH);
                } else {
                    disc(g, v[0], v[1], 0.02f + 0.01f * pulse(9f), BLOOD);
                    if (v[2] > 0f) rect(g, v[0] - 0.03f, v[1] + 0.035f, v[0] - 0.03f + 0.06f * v[2], v[1] + 0.042f, GOOD);
                }
            }
        }
    }

    @Override
    protected void drawTool(GuiGraphicsExtractor g, int x, int y) {
        String step = procedure.stepKey();
        if (step.equals("saw")) {
            g.fill(x - size(0.15f), y - 2, x + size(0.15f), y + 2, METAL);
            for (int i = x - size(0.15f); i < x + size(0.15f); i += 3) g.fill(i, y + 2, i + 1, y + 3, METAL);
        } else if (step.equals("incise")) {
            line(g, x, y, x + 8, y - 8, 1, METAL);
        } else {
            super.drawTool(g, x, y);
        }
    }

    @Override
    protected Component status() {
        AmputationProcedure p = procedure;
        return switch (p.stepKey()) {
            case "incise" -> Component.translatable(key() + ".status.incise", percent(Math.min(1f, p.incised())));
            case "saw" -> Component.translatable(key() + ".status.saw", percent(Math.min(1f, p.depth())), p.strokes());
            case "ligate" -> {
                int tied = 0;
                for (float[] v : p.vessels) if (v[2] >= 1f) tied++;
                yield Component.translatable(key() + ".status.ligate", tied, p.vessels.length);
            }
            default -> null;
        };
    }

    @Override
    protected Component[] choices() {
        if (!procedure.stepKey().equals("check")) return null;
        return new Component[] {Component.translatable(key() + ".choice.go_on"), Component.translatable(key() + ".choice.stop")};
    }

    @Override
    protected void choose(int option) {
        // Stopping before the first cut harms no one
        if (option == 1) close();
        else super.choose(option);
    }

    @Override
    public void onClose() {
        if (procedure.stepKey().equals("check")) close();
        else super.onClose();
    }

    @Override
    protected float timeLimit() {
        return 90f;
    }
}
