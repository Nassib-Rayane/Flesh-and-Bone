package com.fleshbone.client.procedure;

import java.util.List;

import com.fleshbone.sim.procedure.BandageProcedure;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Press the wound, wrap overlapping turns across the limb, tuck the end in. */
public class BandageScreen extends ProcedureScreen<BandageProcedure> {
    public BandageScreen(Screen parent, Completion completion, float skin, int turns) {
        super("bandage", new BandageProcedure(skin, turns), parent, completion);
    }

    @Override
    protected void drawWork(GuiGraphicsExtractor g) {
        BandageProcedure p = procedure;
        rect(g, 0f, BandageProcedure.LIMB_TOP, 1f, BandageProcedure.LIMB_BOTTOM, SKIN);
        rect(g, 0f, BandageProcedure.LIMB_BOTTOM - 0.03f, 1f, BandageProcedure.LIMB_BOTTOM, SKIN_DARK);
        float wy = BandageProcedure.WOUND_Y;
        rect(g, p.woundStart, wy - 0.03f, p.woundEnd, wy + 0.03f, BLOOD);
        rect(g, p.woundStart + 0.01f, wy - 0.008f, p.woundEnd - 0.01f, wy + 0.008f, BLOOD_DARK);
        String step = p.stepKey();
        if (step.equals("press")) {
            ring(g, p.woundCenter, wy, BandageProcedure.PRESS_RADIUS, alpha(HEADER, 0.4f + 0.4f * pulse(4f)));
        }
        List<Float> stripes = p.stripes();
        for (int i = 0; i < stripes.size(); i++) {
            float s = stripes.get(i);
            float half = BandageProcedure.STRIPE / 2f;
            rect(g, s - half, BandageProcedure.LIMB_TOP - 0.015f, s + half, BandageProcedure.LIMB_BOTTOM + 0.015f, 0xF0EEEAE0);
            rect(g, s - half, BandageProcedure.LIMB_TOP - 0.015f, s - half + 0.006f, BandageProcedure.LIMB_BOTTOM + 0.015f, 0xFFBDB8AC);
        }
        if (step.equals("wrap")) {
            float next = p.nextTurnX();
            dotted(g, next, BandageProcedure.LIMB_TOP - 0.12f, next, BandageProcedure.LIMB_BOTTOM + 0.12f, GUIDE);
            String arrow = p.goingDown() ? "▼" : "▲";
            float ay = p.goingDown() ? BandageProcedure.LIMB_TOP - 0.1f : BandageProcedure.LIMB_BOTTOM + 0.05f;
            g.centeredText(font, arrow, px(next), py(ay), HEADER);
        } else if (step.equals("tuck") && !stripes.isEmpty()) {
            float last = stripes.get(stripes.size() - 1);
            disc(g, last, BandageProcedure.LIMB_BOTTOM + 0.06f, 0.025f, alpha(HEADER, 0.5f + 0.5f * pulse(6f)));
            nline(g, last, BandageProcedure.LIMB_BOTTOM, last, BandageProcedure.LIMB_BOTTOM + 0.06f, size(0.03f), CLOTH);
        }
    }

    @Override
    protected void drawTool(GuiGraphicsExtractor g, int x, int y) {
        if (procedure.stepKey().equals("press") && procedure.holding()) {
            g.fill(x - 6, y - 6, x + 6, y + 6, 0xE0F4F0E8);
            g.outline(x - 6, y - 6, 12, 12, 0xFFBDB8AC);
        } else {
            super.drawTool(g, x, y);
        }
    }

    @Override
    protected Component status() {
        BandageProcedure p = procedure;
        return switch (p.stepKey()) {
            case "press" -> Component.translatable(key() + ".status.press", percent(Math.min(1f, p.pressure())));
            case "wrap" -> Component.translatable(key() + ".status.wrap", percent(p.coverage()), p.turnsLeft());
            default -> null;
        };
    }

    @Override
    protected float timeLimit() {
        return 40f;
    }

    /** Out of time, the turns already on still dress the wound. */
    @Override
    protected void onTimeout() {
        if (!procedure.stripes().isEmpty()) finish(procedure.quality(), procedure.amount());
        else abandon();
    }
}
