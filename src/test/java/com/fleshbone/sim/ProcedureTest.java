package com.fleshbone.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fleshbone.sim.procedure.AmputationProcedure;
import com.fleshbone.sim.procedure.BandageProcedure;
import com.fleshbone.sim.procedure.CauterizeProcedure;
import com.fleshbone.sim.procedure.ChestDrainProcedure;
import com.fleshbone.sim.procedure.CprProcedure;
import com.fleshbone.sim.procedure.DefibProcedure;
import com.fleshbone.sim.procedure.ExtractProcedure;
import com.fleshbone.sim.procedure.InfusionProcedure;
import com.fleshbone.sim.procedure.Mistakes;
import com.fleshbone.sim.procedure.Procedure;
import com.fleshbone.sim.procedure.ResetProcedure;
import com.fleshbone.sim.procedure.SplintProcedure;
import com.fleshbone.sim.procedure.SutureProcedure;

/** Each procedure played well and played badly, with scripted mouse input. */
class ProcedureTest {
    /** Advances time in small ticks while holding still. */
    private static float hold(Procedure p, float t, float seconds) {
        for (float s = 0f; s < seconds; s += 0.05f) {
            t += 0.05f;
            p.tick(t);
        }
        return t;
    }

    /** Drags from one point to another over {@code seconds}, ticking along. */
    private static float drag(Procedure p, float t, float x0, float y0, float x1, float y1, float seconds) {
        int steps = Math.max(2, Math.round(seconds / 0.02f));
        for (int i = 1; i <= steps; i++) {
            float f = i / (float) steps;
            t += seconds / steps;
            p.drag(x0 + (x1 - x0) * f, y0 + (y1 - y0) * f, t);
            p.tick(t);
        }
        return t;
    }

    private static float bandageTurn(BandageProcedure p, float t, float x, float seconds) {
        t = drag(p, t, x, 0.3f, x, 0.7f, seconds);
        return drag(p, t, x, 0.7f, x, 0.3f, seconds);
    }

    @Test
    void neatBandageBeatsAHastyOne() {
        BandageProcedure neat = new BandageProcedure(50f, 12);
        float t = 0f;
        neat.press(0.5f, 0.5f, t);
        t = hold(neat, t, 2.7f);
        assertEquals("wrap", neat.stepKey());
        t = drag(neat, t, 0.5f, 0.5f, neat.woundStart, 0.3f, 0.2f);
        float x = neat.woundStart;
        while (neat.stepKey().equals("wrap")) {
            t = bandageTurn(neat, t, x, 0.3f);
            x += BandageProcedure.STRIPE * 0.5f;
        }
        neat.release(x, 0.3f, t);
        assertEquals("tuck", neat.stepKey());
        java.util.List<Float> stripes = neat.stripes();
        neat.press(stripes.get(stripes.size() - 1), BandageProcedure.LIMB_BOTTOM + 0.06f, t + 0.5f);
        assertTrue(neat.done());
        assertTrue(neat.quality() > 0.85f, "neat: " + neat.quality());
        assertEquals(0, neat.mistakes().pain);

        BandageProcedure hasty = new BandageProcedure(50f, 12);
        t = 0f;
        hasty.press(0.5f, 0.5f, t);
        t = hold(hasty, t, 2.7f);
        t = drag(hasty, t, 0.5f, 0.5f, hasty.woundStart, 0.3f, 0.1f);
        x = hasty.woundStart;
        for (int i = 0; i < 4 && hasty.stepKey().equals("wrap"); i++) {
            t = bandageTurn(hasty, t, x, 0.05f);
            x += BandageProcedure.STRIPE * 1.6f;
        }
        t = hold(hasty, t, 5f);
        assertTrue(hasty.mistakes().pain > 0, "snapped too tight");
        assertTrue(hasty.quality() < neat.quality() - 0.25f, "gappy and tight: " + hasty.quality());
    }

    @Test
    void carefulSutureHoldsSloppyOneTears() {
        SutureProcedure careful = new SutureProcedure(40f);
        float t = 0f;
        for (int i = 0; i < careful.stitchCount; i++) {
            float x = careful.stitchX(i);
            careful.press(x, SutureProcedure.woundY(x) - SutureProcedure.EDGE, t);
            t = drag(careful, t, x, SutureProcedure.woundY(x) - SutureProcedure.EDGE, x, SutureProcedure.woundY(x) + SutureProcedure.EDGE, 0.6f);
            careful.release(x, SutureProcedure.woundY(x) + SutureProcedure.EDGE, t);
            careful.press(x, 0.7f, t);
            t = drag(careful, t, x, 0.7f, x, 0.7f - 0.75f * 0.25f, 0.3f);
            careful.release(x, 0.7f - 0.75f * 0.25f, t);
        }
        assertEquals("knot", careful.stepKey());
        for (int i = 0; i < 3; i++) {
            t += 0.5f;
            careful.press(0.5f, 0.5f, t);
            careful.release(0.5f, 0.5f, t);
        }
        assertTrue(careful.done());
        assertTrue(careful.quality() > 0.9f, "careful: " + careful.quality());
        assertEquals(0, careful.mistakes().total());

        SutureProcedure sloppy = new SutureProcedure(40f);
        sloppy.press(0.5f, 0.2f, 0f);
        sloppy.release(0.5f, 0.2f, 0f);
        assertEquals(1, sloppy.mistakes().tear, "stabbed beside the edge");
        float x = sloppy.stitchX(0);
        sloppy.press(x, SutureProcedure.woundY(x) - SutureProcedure.EDGE, 1f);
        sloppy.release(x + 0.1f, SutureProcedure.woundY(x) + SutureProcedure.EDGE, 1.5f);
        assertEquals("pierce", sloppy.stepKey(), "came out crooked: start over");
        assertEquals(2, sloppy.mistakes().tear);
    }

    @Test
    void carefulExtractionAvoidsTheWalls() {
        ExtractProcedure p = new ExtractProcedure(true, 4L);
        float t = 0f;
        for (float[] b : new java.util.ArrayList<>(p.blood())) p.press(b[0], b[1], t += 0.2f);
        assertEquals("probe", p.stepKey());
        p.press(p.tipX(), p.tipY(), t);
        for (float f = 0f; f <= 1f && p.stepKey().equals("probe"); f += 0.01f) {
            float[] pt = p.pointAlong(f);
            t += 0.05f;
            p.drag(pt[0], pt[1], t);
            p.tick(t);
        }
        assertEquals("grip", p.stepKey());
        t = hold(p, t, 1f);
        assertEquals("withdraw", p.stepKey());
        for (float f = 1f; f >= 0f && !p.done(); f -= 0.01f) {
            float[] pt = p.pointAlong(f);
            t += 0.06f;
            p.drag(pt[0], pt[1], t);
            p.tick(t);
        }
        assertTrue(p.succeeded(), "fragment out");
        assertEquals(0, p.mistakes().pain, "never touched the walls");
        assertEquals(1f, p.quality(), 1e-6);

        ExtractProcedure rough = new ExtractProcedure(false, 4L);
        for (float[] b : new java.util.ArrayList<>(rough.blood())) rough.press(b[0], b[1], 0f);
        rough.press(rough.tipX(), rough.tipY(), 0f);
        t = drag(rough, 0f, rough.tipX(), rough.tipY(), 0.95f, 0.5f, 2f);
        assertTrue(rough.mistakes().pain > 0, "scraped the walls");
    }

    /** Clears, probes and grips; returns the time once the withdraw step starts. */
    private static float gripFragment(ExtractProcedure p) {
        float t = 0f;
        for (float[] b : new java.util.ArrayList<>(p.blood())) p.press(b[0], b[1], t += 0.2f);
        p.press(p.tipX(), p.tipY(), t);
        for (float f = 0f; f <= 1f && p.stepKey().equals("probe"); f += 0.01f) {
            float[] pt = p.pointAlong(f);
            p.drag(pt[0], pt[1], t += 0.05f);
            p.tick(t);
        }
        t = hold(p, t, 2f);
        assertEquals("withdraw", p.stepKey());
        return t;
    }

    @Test
    void slowShakyWithdrawalKeepsTheGrip() {
        // A three-second pull with a shaking hand (10 Hz, along and across the channel) and stray mouse samples a
        // millisecond apart, a pixel off: none of that is pulling fast
        for (long seed = 1; seed <= 20; seed++) {
            ExtractProcedure p = new ExtractProcedure(false, seed);
            float t = gripFragment(p);
            java.util.Random jitter = new java.util.Random(seed);
            float start = t;
            while (!p.done() && t - start < 6f) {
                float f = Math.max(0f, 1f - (t - start) / 3f);
                float[] pt = p.pointAlong(f);
                float shake = 0.01f * (float) Math.sin((t - start) * 2 * Math.PI * 10);
                p.drag(pt[0] + shake, pt[1] + shake, t);
                p.drag(pt[0] + shake + (jitter.nextBoolean() ? 0.0045f : -0.0045f), pt[1] + shake, t + 0.0008f);
                p.tick(t);
                t += 1f / 60f;
            }
            assertTrue(p.succeeded(), "fragment out (seed " + seed + ")");
            assertEquals(0, p.slips(), "never slipped (seed " + seed + ")");
        }
    }

    @Test
    void yankingTheFragmentLosesIt() {
        int slipped = 0;
        for (long seed = 1; seed <= 20; seed++) {
            ExtractProcedure p = new ExtractProcedure(true, seed);
            float t = gripFragment(p);
            float start = t;
            while (!p.done() && p.stepKey().equals("withdraw") && t - start < 0.3f) {
                float[] pt = p.pointAlong(Math.max(0f, 1f - (t - start) / 0.2f));
                p.drag(pt[0], pt[1], t);
                p.tick(t);
                t += 1f / 60f;
            }
            if (p.slips() > 0) {
                slipped++;
                assertEquals("grip", p.stepKey(), "back to the bottom");
                assertTrue(p.mistakes().tear > 0);
            }
        }
        assertTrue(slipped >= 18, "a yank nearly always slips: " + slipped + "/20");
    }

    @Test
    void steadyTractionThenSmoothRotationResetsTheJoint() {
        ResetProcedure p = new ResetProcedure(20f, 0f, 1f);
        float t = 0f;
        p.press(0.6f, 0.6f, t);
        // Keep the force on the band by pulsing the button
        while (p.stepKey().equals("traction") && t < 30f) {
            t += 0.05f;
            float center = ResetProcedure.bandCenter(t);
            if (p.force() < center) {
                if (!p.holding()) p.press(0.6f, 0.6f, t);
            } else if (p.holding()) {
                p.release(0.6f, 0.6f, t);
            }
            p.tick(t);
        }
        assertEquals("rotate", p.stepKey(), "relaxed the muscles");
        if (!p.holding()) p.press(0.6f, 0.6f, t);
        for (float a = 0f; a <= ResetProcedure.TARGET_DEGREES; a += 2f) {
            t += 0.03f;
            double r = Math.toRadians(a);
            p.drag(ResetProcedure.PIVOT_X + 0.3f * (float) Math.cos(r), ResetProcedure.PIVOT_Y - 0.3f * (float) Math.sin(r), t);
            float center = ResetProcedure.bandCenter(t);
            p.tick(t);
        }
        double r = Math.toRadians(ResetProcedure.TARGET_DEGREES);
        p.release(ResetProcedure.PIVOT_X + 0.3f * (float) Math.cos(r), ResetProcedure.PIVOT_Y - 0.3f * (float) Math.sin(r), t);
        assertTrue(p.succeeded(), "clunk");
        assertTrue(p.quality() > 0.7f, "smooth: " + p.quality());
    }

    @Test
    void painNarrowsTheRelaxationBand() {
        assertTrue(new ResetProcedure(80f, 0f, 1f).halfWidth() < new ResetProcedure(10f, 0f, 1f).halfWidth());
        assertTrue(new ResetProcedure(80f, 40f, 1f).halfWidth() > new ResetProcedure(80f, 0f, 1f).halfWidth(), "painkillers help");
    }

    @Test
    void amputationNeedsACleanCutAndTiedVessels() {
        AmputationProcedure p = new AmputationProcedure(true, 1f);
        float t = 0f;
        p.press(AmputationProcedure.CX + AmputationProcedure.RADIUS, AmputationProcedure.CY, t);
        for (int deg = 0; deg <= 360 && p.stepKey().equals("incise"); deg += 3) {
            double a = Math.toRadians(deg);
            t += 0.02f;
            p.drag(AmputationProcedure.CX + AmputationProcedure.RADIUS * (float) Math.cos(a), AmputationProcedure.CY + AmputationProcedure.RADIUS * (float) Math.sin(a), t);
            p.tick(t);
        }
        p.release(0.5f, 0.5f, t);
        assertEquals("saw", p.stepKey());
        p.press(0.3f, 0.5f, t);
        for (int stroke = 0; stroke < 20 && p.stepKey().equals("saw"); stroke++) {
            t = drag(p, t, stroke % 2 == 0 ? 0.3f : 0.7f, 0.5f, stroke % 2 == 0 ? 0.7f : 0.3f, 0.5f, 0.3f);
        }
        p.release(0.5f, 0.5f, t);
        assertEquals("ligate", p.stepKey());
        for (float[] v : p.vessels) {
            p.press(v[0], v[1], t);
            t = hold(p, t, 0.7f);
            p.release(v[0], v[1], t);
        }
        assertTrue(p.done());
        assertTrue(p.strokes() > 5);
        assertEquals(1f, p.quality(), 1e-6);
        assertEquals(0, p.mistakes().total());

        AmputationProcedure noTourniquet = new AmputationProcedure(false, 1f);
        assertEquals("check", noTourniquet.stepKey());
        assertEquals(2, noTourniquet.mistakes().tear, "it will bleed");
    }

    @Test
    void defibrillationNeedsTheRightDecision() {
        DefibProcedure vf = new DefibProcedure(DefibProcedure.Rhythm.FIBRILLATION, 0f);
        place(vf);
        vf.choose(0, 2f);
        vf.choose(2, 3f);
        vf.press(DefibProcedure.CHARGE_BUTTON[0], DefibProcedure.CHARGE_BUTTON[1], 3f);
        float t = hold(vf, 3f, 1.6f);
        vf.release(DefibProcedure.CHARGE_BUTTON[0], DefibProcedure.CHARGE_BUTTON[1], t);
        assertEquals("shock", vf.stepKey());
        vf.press(DefibProcedure.CHARGE_BUTTON[0], DefibProcedure.CHARGE_BUTTON[1], t + 0.3f);
        assertTrue(vf.succeeded() && !vf.noShock());
        assertTrue(vf.correctDecision());
        assertEquals(3, vf.amount(), "360 J");
        assertTrue(vf.quality() > 0.9f);

        DefibProcedure flat = new DefibProcedure(DefibProcedure.Rhythm.ASYSTOLE, 0f);
        place(flat);
        flat.choose(1, 2f);
        assertTrue(flat.noShock() && flat.correctDecision(), "no shock for asystole");
    }

    private static void place(DefibProcedure p) {
        p.press(p.padA[0], p.padA[1], 0f);
        p.drag(DefibProcedure.PAD_TARGET_A[0], DefibProcedure.PAD_TARGET_A[1], 0.5f);
        p.release(DefibProcedure.PAD_TARGET_A[0], DefibProcedure.PAD_TARGET_A[1], 0.5f);
        p.press(p.padB[0], p.padB[1], 1f);
        p.drag(DefibProcedure.PAD_TARGET_B[0], DefibProcedure.PAD_TARGET_B[1], 1.5f);
        p.release(DefibProcedure.PAD_TARGET_B[0], DefibProcedure.PAD_TARGET_B[1], 1.5f);
        assertEquals("analyze", p.stepKey());
        assertEquals(1f, p.padAccuracy(), 1e-6);
    }

    @Test
    void goodCompressionsKeepBloodFlowing() {
        CprProcedure p = new CprProcedure();
        float t = 0f;
        for (int i = 0; i < 30; i++) {
            p.press(0.5f, 0.5f, t);
            t = drag(p, t, 0.5f, 0.5f, 0.5f, 0.5f + 0.55f * 0.4f, 0.2f);
            p.release(0.5f, 0.72f, t);
            t = hold(p, t, 0.33f);
        }
        assertEquals("tilt", p.stepKey());
        assertTrue(p.perfusion() > 0.6f, "perfusion " + p.perfusion());
        p.press(CprProcedure.HEAD[0], CprProcedure.HEAD[1], t);
        p.release(CprProcedure.HEAD[0], CprProcedure.HEAD[1], t);
        for (int b = 0; b < 2; b++) {
            p.press(0.5f, 0.3f, t);
            t = hold(p, t, 1.1f);
            p.release(0.5f, 0.3f, t);
        }
        assertEquals("compress", p.stepKey(), "next cycle");

        CprProcedure weak = new CprProcedure();
        t = 0f;
        for (int i = 0; i < 20; i++) {
            weak.press(0.5f, 0.5f, t);
            t = drag(weak, t, 0.5f, 0.5f, 0.5f, 0.55f, 0.1f);
            weak.release(0.5f, 0.55f, t);
            t = hold(weak, t, 1f);
        }
        assertTrue(weak.perfusion() < 0.3f, "too shallow and too slow: " + weak.perfusion());
    }

    @Test
    void infusionNeedsTheVeinAndAShallowNeedle() {
        InfusionProcedure p = new InfusionProcedure(3L);
        p.press(InfusionProcedure.BAND[0], InfusionProcedure.BAND[1], 0f);
        p.move(0.5f, p.veinY, 0.5f);
        assertEquals(1f, p.feel(), 1e-6);
        p.press(0.5f, p.veinY, 1f);
        assertEquals("needle", p.stepKey());
        p.press(0.3f, InfusionProcedure.SKIN_Y, 2f);
        p.drag(0.6f, InfusionProcedure.SKIN_Y + 0.4f, 2.5f);
        p.release(0.6f, InfusionProcedure.SKIN_Y + 0.4f, 2.5f);
        assertEquals(1, p.failures(), "too steep: through the vein");
        assertEquals(1, p.mistakes().tear);
        double a = Math.toRadians(22);
        p.press(0.3f, InfusionProcedure.SKIN_Y, 3f);
        p.drag(0.3f + 0.2f * (float) Math.cos(a), InfusionProcedure.SKIN_Y + 0.2f * (float) Math.sin(a), 3.5f);
        p.release(0.3f + 0.2f * (float) Math.cos(a), InfusionProcedure.SKIN_Y + 0.2f * (float) Math.sin(a), 3.5f);
        assertEquals("release", p.stepKey());
        p.press(InfusionProcedure.BAND[0], InfusionProcedure.BAND[1], 4f);
        assertTrue(p.succeeded());
        assertEquals(0.8f, p.quality(), 1e-6);
    }

    @Test
    void chestDrainGoesInTheFifthSpace() {
        ChestDrainProcedure p = new ChestDrainProcedure();
        float spaceY = ChestDrainProcedure.ribTop(4) + ChestDrainProcedure.RIB_THICKNESS + 0.03f;
        p.press(0.5f, spaceY, 0f);
        assertEquals("incise", p.stepKey());
        p.press(0.5f, spaceY, 1f);
        p.drag(0.56f, spaceY, 1.2f);
        assertEquals("insert", p.stepKey());
        p.press(0.5f, spaceY, 2f);
        p.drag(0.35f, spaceY + 0.02f, 2.5f);
        assertTrue(p.succeeded());
        assertEquals(1f, p.quality(), 1e-6);

        ChestDrainProcedure wrong = new ChestDrainProcedure();
        float lowY = ChestDrainProcedure.ribTop(7) - 0.02f;
        wrong.press(0.5f, lowY, 0f);
        assertTrue(wrong.mistakes().tear > 0, "too low: an organ");
    }

    @Test
    void splintAlignedSlowlyHoldsBest() {
        SplintProcedure p = new SplintProcedure(30f);
        float[] end = p.distalEnd();
        p.press(end[0], end[1], 0f);
        float t = 0f;
        for (float a = 30f; a >= 0f; a -= 1f) {
            t += 0.05f;
            double r = Math.toRadians(a);
            p.drag(SplintProcedure.PIVOT_X + 0.32f * (float) Math.cos(r), SplintProcedure.LIMB_Y + 0.32f * (float) Math.sin(r), t);
        }
        p.release(SplintProcedure.PIVOT_X + 0.32f, SplintProcedure.LIMB_Y, t);
        assertEquals("strap", p.stepKey());
        p.press(0.3f, SplintProcedure.LIMB_Y, t + 1f);
        p.press(0.7f, SplintProcedure.LIMB_Y, t + 2f);
        assertTrue(p.succeeded());
        assertEquals(1f, p.quality(), 1e-3);

        SplintProcedure rough = new SplintProcedure(30f);
        end = rough.distalEnd();
        rough.press(end[0], end[1], 0f);
        rough.drag(SplintProcedure.PIVOT_X + 0.32f, SplintProcedure.LIMB_Y, 0.05f);
        assertTrue(rough.mistakes().pain > 0, "yanked into place");
    }

    @Test
    void searingTooLongBurnsDeep() {
        CauterizeProcedure p = new CauterizeProcedure(40f, 2L);
        float t = 0f;
        for (float[] point : p.points) {
            p.press(point[0], point[1], t);
            t = hold(p, t, 1.1f);
            p.release(point[0], point[1], t);
            t = hold(p, t, 0.1f);
        }
        assertTrue(p.succeeded());
        assertEquals(1f, p.quality(), 1e-6);

        CauterizeProcedure slow = new CauterizeProcedure(40f, 2L);
        float[] first = slow.points[0];
        slow.press(first[0], first[1], 0f);
        hold(slow, 0f, 2f);
        assertEquals(1, slow.mistakes().tear, "a deep burn");
        assertFalse(slow.done(), "the other points still bleed");
    }

    @Test
    void mistakesSurviveTheNetwork() {
        Mistakes m = new Mistakes();
        m.pain = 3;
        m.tear = 2;
        m.dirt = 7;
        Mistakes back = Mistakes.unpack(m.pack());
        assertEquals(3, back.pain);
        assertEquals(2, back.tear);
        assertEquals(7, back.dirt);
    }
}
