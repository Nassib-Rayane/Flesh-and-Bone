package com.fleshbone.client.procedure;

import com.fleshbone.sim.procedure.ChestDrainProcedure;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Count the ribs down to the 5th space, cut, slide the tube in over the lower rib. */
public class ChestDrainScreen extends ProcedureScreen<ChestDrainProcedure> {
    public ChestDrainScreen(Screen parent, Completion completion) {
        super("chest_drain", new ChestDrainProcedure(), parent, completion);
    }

    @Override
    protected void drawWork(GuiGraphicsExtractor g) {
        ChestDrainProcedure p = procedure;
        // Side of the chest, the arm raised above
        rect(g, 0.05f, 0.05f, 0.95f, 0.95f, SKIN);
        rect(g, 0.3f, 0f, 0.7f, 0.08f, SKIN_DARK);
        int felt = p.feltRib();
        for (int i = 0; i < ChestDrainProcedure.RIBS; i++) {
            float top = ChestDrainProcedure.ribTop(i);
            int color = i == felt ? alpha(BONE, 0.7f) : 0x24000000;
            rect(g, 0.08f, top, 0.92f, top + ChestDrainProcedure.RIB_THICKNESS, color);
        }
        if (felt >= 0) {
            float y = ChestDrainProcedure.ribTop(felt) + ChestDrainProcedure.RIB_THICKNESS / 2f;
            g.text(font, Component.translatable(key() + ".rib", felt + 1), px(0.08f), py(y) - 4, 0xFF3A2010, false);
        }
        dotted(g, ChestDrainProcedure.COLUMN_MIN, 0.08f, ChestDrainProcedure.COLUMN_MIN, 0.92f, 0x30000000);
        dotted(g, ChestDrainProcedure.COLUMN_MAX, 0.08f, ChestDrainProcedure.COLUMN_MAX, 0.92f, 0x30000000);
        if (!p.stepKey().equals("palpate")) {
            float mx = p.markX();
            float my = p.markY();
            if (p.stepKey().equals("incise")) {
                ring(g, mx, my, 0.03f, alpha(HEADER, 0.5f + 0.5f * pulse(5f)));
                nline(g, mx - 0.012f, my - 0.012f, mx + 0.012f, my + 0.012f, 1, 0xFF203080);
                nline(g, mx - 0.012f, my + 0.012f, mx + 0.012f, my - 0.012f, 1, 0xFF203080);
            } else {
                rect(g, mx - 0.03f, my - 0.005f, mx + 0.03f, my + 0.005f, BLOOD);
            }
        }
    }

    @Override
    protected void drawTool(GuiGraphicsExtractor g, int x, int y) {
        ChestDrainProcedure p = procedure;
        if (p.stepKey().equals("insert") && p.holding()) {
            line(g, px(p.markX()), py(p.markY()), x, y, 3, 0xE0E8F0F0);
        } else if (p.stepKey().equals("incise")) {
            line(g, x, y, x + 8, y - 8, 1, METAL);
        } else {
            super.drawTool(g, x, y);
        }
    }

    @Override
    protected float timeLimit() {
        return 50f;
    }
}
