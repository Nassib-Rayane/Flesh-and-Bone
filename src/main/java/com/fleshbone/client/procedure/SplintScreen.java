package com.fleshbone.client.procedure;

import com.fleshbone.sim.procedure.SplintProcedure;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Bring the broken limb back in line, slowly, then strap the splint above and below the break. */
public class SplintScreen extends ProcedureScreen<SplintProcedure> {
    private static final float SEGMENT = 0.32f;
    private static final int WOOD = 0xFF9A7040;

    public SplintScreen(Screen parent, Completion completion, float crookedDegrees) {
        super("splint", new SplintProcedure(crookedDegrees), parent, completion);
    }

    @Override
    protected void drawWork(GuiGraphicsExtractor g) {
        SplintProcedure p = procedure;
        float cx = SplintProcedure.PIVOT_X;
        float cy = SplintProcedure.LIMB_Y;
        boolean strapping = p.stepKey().equals("strap");
        if (strapping) rect(g, cx - SEGMENT - 0.02f, cy + 0.045f, cx + SEGMENT + 0.02f, cy + 0.07f, WOOD);
        else dotted(g, cx, cy, cx + SEGMENT, cy, GUIDE);
        rect(g, cx - SEGMENT, cy - 0.045f, cx, cy + 0.045f, SKIN);
        float[] end = p.distalEnd();
        nline(g, cx, cy, end[0], end[1], size(0.09f), SKIN);
        disc(g, end[0], end[1], 0.045f, SKIN);
        // The break
        nline(g, cx - 0.01f, cy - 0.03f, cx + 0.01f, cy, 1, BLOOD);
        nline(g, cx + 0.01f, cy, cx - 0.01f, cy + 0.03f, 1, BLOOD);
        if (strapping) {
            rect(g, cx - SplintProcedure.BREAK_HALF, cy - 0.08f, cx + SplintProcedure.BREAK_HALF, cy + 0.08f, 0x30FF4040);
            if (p.strapAbove()) rect(g, cx - 0.2f, cy - 0.055f, cx - 0.16f, cy + 0.075f, 0xFF5A4A3A);
            if (p.strapBelow()) rect(g, cx + 0.16f, cy - 0.055f, cx + 0.2f, cy + 0.075f, 0xFF5A4A3A);
        } else {
            ring(g, end[0], end[1], 0.06f, alpha(HEADER, 0.4f + 0.4f * pulse(4f)));
        }
    }

    @Override
    protected Component status() {
        if (procedure.stepKey().equals("align")) return Component.translatable(key() + ".status.angle", Math.round(Math.abs(procedure.angle())));
        return null;
    }

    @Override
    protected float timeLimit() {
        return 40f;
    }
}
