package com.fleshbone.client.procedure;

import com.fleshbone.sim.procedure.CauterizeProcedure;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Hold the flame on each bleeding point until it seals, and no longer. */
public class CauterizeScreen extends ProcedureScreen<CauterizeProcedure> {
    public CauterizeScreen(Screen parent, Completion completion, float bleed) {
        super("cauterize", new CauterizeProcedure(bleed, System.nanoTime()), parent, completion);
    }

    @Override
    protected void drawWork(GuiGraphicsExtractor g) {
        rect(g, 0.1f, 0.15f, 0.9f, 0.85f, SKIN);
        rect(g, 0.2f, 0.25f, 0.8f, 0.75f, FLESH);
        for (float[] point : procedure.points) {
            float heat = point[2];
            if (heat >= CauterizeProcedure.DEEP_BURN) {
                disc(g, point[0], point[1], 0.03f, 0xFF140C08);
            } else if (heat >= CauterizeProcedure.SEAL) {
                disc(g, point[0], point[1], 0.026f, 0xFF4A2A18);
                if (heat > CauterizeProcedure.SEAL + 0.2f) ring(g, point[0], point[1], 0.035f, alpha(BAD, pulse(14f)));
            } else {
                disc(g, point[0], point[1], 0.02f + 0.008f * pulse(8f), BLOOD);
                float drip = (time * 0.6f + point[0] * 7f) % 1f;
                disc(g, point[0], point[1] + 0.02f + 0.06f * drip, 0.006f, alpha(BLOOD, 1f - drip));
                if (heat > 0f) rect(g, point[0] - 0.03f, point[1] - 0.045f, point[0] - 0.03f + 0.06f * heat / CauterizeProcedure.SEAL, point[1] - 0.038f, 0xFFFF9020);
            }
        }
    }

    @Override
    protected void drawTool(GuiGraphicsExtractor g, int x, int y) {
        if (procedure.holding()) {
            float flicker = pulse(23f);
            disc(g, x, y - 2, 4 + Math.round(flicker * 2f), 0xA0FF7010);
            disc(g, x, y - 1, 2, 0xE0FFE060);
        } else {
            super.drawTool(g, x, y);
        }
    }

    @Override
    protected Component status() {
        int sealed = 0;
        for (float[] point : procedure.points) if (point[2] >= CauterizeProcedure.SEAL) sealed++;
        return Component.translatable(key() + ".status", sealed, procedure.points.length);
    }

    @Override
    protected float timeLimit() {
        return 30f;
    }
}
