package com.fleshbone.client.procedure;

import com.fleshbone.sim.procedure.ResetProcedure;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Pull in the band where the muscles let go, then swing the limb along the arc until it clunks back in. */
public class ResetScreen extends ProcedureScreen<ResetProcedure> {
    private static final float LIMB = 0.42f;

    public ResetScreen(Screen parent, Completion completion, float pain, float relief) {
        super("reset", new ResetProcedure(pain, relief, strength()), parent, completion);
    }

    @Override
    protected void drawWork(GuiGraphicsExtractor g) {
        ResetProcedure p = procedure;
        float pxv = ResetProcedure.PIVOT_X;
        float pyv = ResetProcedure.PIVOT_Y;
        boolean rotating = p.stepKey().equals("rotate");
        // The target arc and mark
        for (float a = -10f; a <= ResetProcedure.TARGET_DEGREES + 30f; a += 4f) {
            double r = Math.toRadians(a);
            int color = Math.abs(a - ResetProcedure.TARGET_DEGREES) <= 10f ? alpha(GOOD, 0.8f) : a > ResetProcedure.TARGET_DEGREES + 15f ? alpha(BAD, 0.6f) : GUIDE;
            disc(g, px(pxv + LIMB * (float) Math.cos(r)), py(pyv - LIMB * (float) Math.sin(r)), 0, color);
        }
        // Upper bone and the joint
        rect(g, 0.02f, pyv - 0.06f, pxv, pyv + 0.06f, SKIN);
        disc(g, pxv, pyv, 0.07f, SKIN_DARK);
        // The dislocated limb hangs off its socket until the traction holds
        float angle = rotating ? p.angle() : -8f;
        float offset = rotating ? 0f : 0.03f;
        double r = Math.toRadians(angle);
        float ex = pxv + offset + LIMB * (float) Math.cos(r);
        float ey = pyv + offset - LIMB * (float) Math.sin(r);
        nline(g, pxv + offset, pyv + offset, ex, ey, size(0.1f), SKIN);
        disc(g, ex, ey, 0.05f, SKIN);
        // Force gauge
        float center = ResetProcedure.bandCenter(time);
        float half = p.halfWidth();
        gauge(g, 0.1f, 0.88f, 0.9f, 0.93f, p.force(), 1.25f, Math.max(0f, center - half), Math.min(1.25f, center + half),
                Math.abs(p.force() - center) < half ? GOOD : p.force() > center + half ? BAD : WARN);
        if (!rotating) rect(g, 0.1f, 0.95f, 0.1f + 0.8f * Math.min(1f, p.inBand()), 0.96f, HEADER);
    }

    @Override
    protected Component status() {
        ResetProcedure p = procedure;
        if (p.stepKey().equals("traction")) return Component.translatable(key() + ".status.traction", percent(Math.min(1f, p.inBand())));
        return Component.translatable(key() + ".status.rotate", Math.round(p.angle()), Math.round(ResetProcedure.TARGET_DEGREES));
    }

    @Override
    protected float timeLimit() {
        return 40f;
    }
}
