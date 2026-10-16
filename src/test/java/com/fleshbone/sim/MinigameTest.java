package com.fleshbone.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fleshbone.sim.minigame.PullTracker;
import com.fleshbone.sim.minigame.StitchSequence;
import com.fleshbone.sim.minigame.TimingGauge;
import com.fleshbone.sim.minigame.WrapTracker;

class MinigameTest {
    /** Feeds {@code turns} circles of radius {@code r} around (0, 0). */
    private static void circle(WrapTracker tracker, float r, float turns) {
        int steps = (int) (turns * 64);
        for (int i = 0; i <= steps; i++) {
            double a = i / 64.0 * 2 * Math.PI;
            tracker.sample((float) (Math.cos(a) * r), (float) (Math.sin(a) * r));
        }
    }

    @Test
    void neatWrapsOnTheRingArePerfect() {
        WrapTracker tracker = new WrapTracker(0, 0, 30);
        circle(tracker, 30, 4.2f);
        assertEquals(4, tracker.wraps());
        assertEquals(1f, tracker.quality(), 0.01f);
        assertEquals(4, tracker.lengthUsed());
    }

    @Test
    void sloppyOrTooFewWrapsAreWorse() {
        WrapTracker sloppy = new WrapTracker(0, 0, 30);
        circle(sloppy, 55, 4.2f);
        assertTrue(sloppy.quality() < 0.6f, "far from the ring: " + sloppy.quality());
        WrapTracker few = new WrapTracker(0, 0, 30);
        circle(few, 30, 1.5f);
        assertEquals(0.3f, few.quality(), 0.01f);
    }

    @Test
    void goingBackAndForthDoesNotCount() {
        WrapTracker tracker = new WrapTracker(0, 0, 30);
        for (int n = 0; n < 10; n++) {
            for (int i = 0; i < 16; i++) tracker.sample((float) Math.cos(i / 32.0 * Math.PI) * 30, (float) Math.sin(i / 32.0 * Math.PI) * 30);
            for (int i = 16; i >= 0; i--) tracker.sample((float) Math.cos(i / 32.0 * Math.PI) * 30, (float) Math.sin(i / 32.0 * Math.PI) * 30);
        }
        assertEquals(0, tracker.wraps());
    }

    @Test
    void gaugeRewardsTheGreenZone() {
        TimingGauge gauge = new TimingGauge(2f, 0.5f, 0f);
        assertEquals(0.5f, gauge.needle(0.5f), 1e-4);
        assertEquals(1f, gauge.quality(0.5f), 1e-4);
        assertEquals(0f, gauge.quality(0f), 1e-4);
        TimingGauge painful = new TimingGauge(2f, 0.5f, 120f);
        assertTrue(painful.zoneHalfWidth() < gauge.zoneHalfWidth());
    }

    @Test
    void slowPullSucceedsFastPullSlips() {
        PullTracker slow = new PullTracker(100, 100, 0f, 40f, 200f, 8f);
        assertTrue(slow.grab(100, 100, 0f));
        for (int i = 1; i <= 40; i++) slow.drag(100 + i, 100, i * 0.02f); // 50 px/s
        assertTrue(slow.done());
        assertEquals(1f, slow.quality(), 1e-6);

        PullTracker fast = new PullTracker(100, 100, 0f, 40f, 200f, 8f);
        fast.grab(100, 100, 0f);
        fast.drag(130, 100, 0.05f); // 600 px/s
        assertTrue(fast.slipped());
        assertEquals(0f, fast.quality(), 1e-6);
        assertFalse(fast.grab(100, 100, 0.1f), "a slipped fragment cannot be grabbed again");
    }

    @Test
    void preciseStitchesMakeAGoodSuture() {
        StitchSequence seq = new StitchSequence(0, 100, 50, 8, 3, 6);
        for (int i = 0; i < seq.points(); i++) assertTrue(seq.click(seq.x(i), seq.y(i)));
        assertTrue(seq.done());
        assertEquals(1f, seq.quality(), 1e-6);

        StitchSequence clumsy = new StitchSequence(0, 100, 50, 8, 3, 6);
        clumsy.click(-50, -50);
        clumsy.click(-50, -50);
        for (int i = 0; i < clumsy.points(); i++) clumsy.click(clumsy.x(i) + 4, clumsy.y(i));
        assertTrue(clumsy.quality() < 0.6f, "misses and sloppy stitches: " + clumsy.quality());
    }
}
