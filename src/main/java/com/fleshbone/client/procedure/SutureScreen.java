package com.fleshbone.client.procedure;

import com.fleshbone.sim.procedure.SutureProcedure;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Pierce the upper edge, pass under the wound, come out on the lower edge, pull to the right tension; then a knot. */
public class SutureScreen extends ProcedureScreen<SutureProcedure> {
    private static final int THREAD = 0xFF2A3A8A;
    private String lastStep = "";
    private float passStarted;

    public SutureScreen(Screen parent, Completion completion, float skin) {
        super("suture", new SutureProcedure(skin), parent, completion);
    }

    @Override
    protected void update(float t) {
        String step = procedure.stepKey();
        if (step.equals("pass") && !lastStep.equals("pass")) passStarted = t;
        lastStep = step;
    }

    @Override
    protected void drawWork(GuiGraphicsExtractor g) {
        SutureProcedure p = procedure;
        rect(g, 0.04f, 0.2f, 0.96f, 0.8f, SKIN);
        float start = SutureProcedure.START - 0.03f;
        float end = SutureProcedure.END + 0.03f;
        for (float x = start; x <= end; x += 1f / ws) {
            float y = SutureProcedure.woundY(x);
            float open = 0.022f * (float) Math.sin((x - start) / (end - start) * Math.PI);
            rect(g, x, y - open - 0.004f, x + 1.5f / ws, y + open + 0.004f, BLOOD);
            rect(g, x, y - open * 0.5f, x + 1.5f / ws, y + open * 0.5f, BLOOD_DARK);
        }
        for (float x = start; x <= end; x += 0.02f) {
            disc(g, px(x), py(SutureProcedure.woundY(x) - SutureProcedure.EDGE), 0, alpha(HEADER, 0.35f));
            disc(g, px(x), py(SutureProcedure.woundY(x) + SutureProcedure.EDGE), 0, alpha(HEADER, 0.35f));
        }
        for (int i = 0; i < p.stitch(); i++) {
            float x = p.stitchX(i);
            float y = SutureProcedure.woundY(x);
            int color = p.stitchQuality(i) > 0.8f ? THREAD : 0xFF6A5AA0;
            nline(g, x, y - SutureProcedure.EDGE, x, y + SutureProcedure.EDGE, 2, color);
            nline(g, x - 0.01f, y - SutureProcedure.EDGE, x + 0.01f, y - SutureProcedure.EDGE, 1, color);
        }
        String step = p.stepKey();
        if (p.stitch() < p.stitchCount && step.equals("pierce")) {
            float x = p.stitchX(p.stitch());
            ring(g, x, SutureProcedure.woundY(x) - SutureProcedure.EDGE, 0.02f, alpha(HEADER, 0.5f + 0.5f * pulse(5f)));
        } else if (step.equals("pass") || step.equals("pull")) {
            float x = p.entryX();
            float top = SutureProcedure.woundY(x) - SutureProcedure.EDGE;
            float bottom = SutureProcedure.woundY(x) + SutureProcedure.EDGE;
            disc(g, x, top, 0.008f, THREAD);
            if (step.equals("pass")) ring(g, x, bottom, 0.02f, alpha(HEADER, 0.5f + 0.5f * pulse(5f)));
            else nline(g, x, top, x, bottom, 2, THREAD);
        }
        if (step.equals("pull")) {
            float tension = p.tension();
            rect(g, 0.9f, 0.25f, 0.94f, 0.75f, 0xFF2A2420);
            rect(g, 0.9f, 0.75f - 0.5f * 0.95f / 1.2f, 0.94f, 0.75f - 0.5f * 0.55f / 1.2f, 0xFF2E5A24);
            float h = 0.5f * Math.min(1.2f, tension) / 1.2f;
            rect(g, 0.905f, 0.75f - h, 0.935f, 0.75f, tension > 1.05f ? BAD : tension >= 0.55f && tension <= 0.95f ? GOOD : WARN);
        } else if (step.equals("knot")) {
            float x = p.stitchX(p.stitchCount - 1) + 0.06f;
            g.centeredText(font, p.knotTugs() + "/3", px(x), py(0.3f), HEADER);
        }
    }

    @Override
    protected void drawTool(GuiGraphicsExtractor g, int x, int y) {
        line(g, x, y, x + 9, y - 9, 1, METAL);
        g.fill(x, y, x + 1, y + 1, 0xFFFFFFFF);
    }

    @Override
    protected Component status() {
        SutureProcedure p = procedure;
        Component stitches = Component.translatable(key() + ".status.stitches", p.stitch(), p.stitchCount);
        if (p.stepKey().equals("pass") && procedure.holding()) {
            float held = time - passStarted;
            String depth = held < SutureProcedure.SHALLOW_SECONDS ? "shallow" : held > SutureProcedure.DEEP_SECONDS ? "deep" : "good";
            return stitches.copy().append(" · ").append(Component.translatable(key() + ".depth." + depth));
        }
        if (p.stepKey().equals("pull")) {
            return stitches.copy().append(" · ").append(Component.translatable(key() + ".status.tension", percent(p.tension())));
        }
        return stitches;
    }

    @Override
    protected float timeLimit() {
        return 60f;
    }

    /** Out of time, the stitches already in hold what they can. */
    @Override
    protected void onTimeout() {
        if (procedure.stitch() > 0) finish(procedure.quality(), procedure.stitch());
        else abandon();
    }
}
