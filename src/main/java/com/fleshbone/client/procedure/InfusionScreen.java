package com.fleshbone.client.procedure;

import com.fleshbone.sim.procedure.InfusionProcedure;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Tourniquet on the arm, feel for the vein, slide the needle in at a shallow angle, release the tourniquet. */
public class InfusionScreen extends ProcedureScreen<InfusionProcedure> {
    private static final float ENTRY_X = 0.3f;
    private static final float VEIN_DEPTH = 0.15f;

    public InfusionScreen(Screen parent, Completion completion) {
        super("infusion", new InfusionProcedure(System.nanoTime()), parent, completion);
    }

    @Override
    protected void drawWork(GuiGraphicsExtractor g) {
        InfusionProcedure p = procedure;
        if (p.stepKey().equals("needle")) {
            drawSideView(g, p);
            return;
        }
        // Forearm from above, the vein hidden under the skin
        rect(g, 0f, InfusionProcedure.ARM_TOP, 1f, InfusionProcedure.ARM_BOTTOM, SKIN);
        rect(g, 0f, InfusionProcedure.ARM_BOTTOM - 0.03f, 1f, InfusionProcedure.ARM_BOTTOM, SKIN_DARK);
        boolean tight = !p.stepKey().equals("tourniquet");
        if (tight) rect(g, 0.2f, p.veinY - 0.006f, 0.85f, p.veinY + 0.006f, 0x187080D0);
        float bx = InfusionProcedure.BAND[0];
        int bandColor = tight ? 0xFF4060B0 : 0xFF7080A0;
        rect(g, bx - 0.03f, InfusionProcedure.ARM_TOP - (tight ? 0.0f : 0.03f), bx + 0.03f, InfusionProcedure.ARM_BOTTOM + (tight ? 0.0f : 0.03f), bandColor);
        if (p.stepKey().equals("tourniquet") || p.stepKey().equals("release")) {
            ring(g, bx, InfusionProcedure.BAND[1], 0.06f, alpha(HEADER, 0.5f + 0.5f * pulse(5f)));
        }
        if (p.markX() > 0f) {
            float mx = p.markX();
            float my = p.markY();
            nline(g, mx - 0.015f, my - 0.015f, mx + 0.015f, my + 0.015f, 1, 0xFF203080);
            nline(g, mx - 0.015f, my + 0.015f, mx + 0.015f, my - 0.015f, 1, 0xFF203080);
        }
    }

    private void drawSideView(GuiGraphicsExtractor g, InfusionProcedure p) {
        float skin = InfusionProcedure.SKIN_Y;
        rect(g, 0f, skin - 0.12f, 1f, skin, 0xFF201A16);
        rect(g, 0f, skin, 1f, 1f, SKIN_DARK);
        rect(g, 0f, skin, 1f, skin + 0.02f, SKIN);
        float veinTop = skin + VEIN_DEPTH - 0.035f;
        rect(g, 0f, veinTop, 1f, veinTop + 0.07f, 0xFF3A4A90);
        rect(g, 0f, veinTop + 0.02f, 1f, veinTop + 0.05f, 0xFF5A1A40);
        // Ideal angle wedge
        for (float a = 12f; a <= 32f; a += 4f) {
            double r = Math.toRadians(a);
            dotted(g, ENTRY_X, skin, ENTRY_X + 0.35f * (float) Math.cos(r), skin + 0.35f * (float) Math.sin(r), alpha(GOOD, 0.25f));
        }
        disc(g, ENTRY_X, skin, 0.008f, 0xFF203080);
        // The needle
        float depth = p.holding() ? p.needleDepth() : 0f;
        double r = Math.toRadians(p.needleAngle());
        float tipX = ENTRY_X + depth * (float) Math.cos(r);
        float tipY = skin + depth * (float) Math.sin(r);
        float backX = tipX - 0.3f * (float) Math.cos(r);
        float backY = tipY - 0.3f * (float) Math.sin(r);
        if (depth > 0f) {
            nline(g, backX, backY, tipX, tipY, 1, METAL);
            nline(g, backX - 0.08f * (float) Math.cos(r), backY - 0.08f * (float) Math.sin(r), backX, backY, 3, 0xFFD8D0B0);
        }
    }

    @Override
    protected Component status() {
        InfusionProcedure p = procedure;
        return switch (p.stepKey()) {
            case "palpate" -> Component.translatable(key() + ".status.feel", "▮".repeat(Math.round(p.feel() * 5f)) + "▯".repeat(5 - Math.round(p.feel() * 5f)));
            case "needle" -> Component.translatable(key() + ".status.needle", Math.round(p.holding() ? p.needleAngle() : 0f),
                    InfusionProcedure.TRIES - p.failures());
            default -> null;
        };
    }

    @Override
    protected void drawTool(GuiGraphicsExtractor g, int x, int y) {
        if (procedure.stepKey().equals("palpate")) {
            float feel = procedure.feel();
            int r = 3 + Math.round(4f * feel * pulse(9f));
            disc(g, x, y, r, alpha(0xFFFF8080, 0.25f + 0.5f * feel));
        } else {
            super.drawTool(g, x, y);
        }
    }
}
