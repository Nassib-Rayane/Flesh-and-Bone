package com.fleshbone.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import com.fleshbone.sim.minigame.SawTracker;
import com.fleshbone.sim.system.InfectionSystem;
import com.fleshbone.sim.treatment.Treatments;

class InfectionTest {
    private static Body dirtyWound() {
        Body body = SimTestUtil.body();
        PartState s = body.part(BodyPart.FOREARM_L);
        s.skin = 40f;
        s.contamination = 80f;
        body.derived.compute(body);
        return body;
    }

    private static float runInfection(Body body, float seconds, long seed) {
        Environment env = SimTestUtil.env();
        Random rng = new Random(seed);
        float t = 0f;
        while (t < seconds) {
            body.tick(env, SimTestUtil.SETTINGS, rng);
            t += env.dt;
        }
        return body.part(BodyPart.FOREARM_L).infection;
    }

    @Test
    void dirtyOpenWoundGetsInfected() {
        int infected = 0;
        for (long seed = 0; seed < 20; seed++) {
            if (runInfection(dirtyWound(), 10 * 60f, seed) > 0f) infected++;
        }
        assertTrue(infected >= 14, "most dirty wounds left open for 10 minutes get infected: " + infected + "/20");
    }

    @Test
    void disinfectedWoundStaysClean() {
        for (long seed = 0; seed < 10; seed++) {
            Body body = dirtyWound();
            Treatments.disinfect(body, BodyPart.FOREARM_L, Treatments.ANTISEPTIC_SECONDS);
            assertEquals(0f, runInfection(body, 4 * 60f, seed), 1e-6, "antiseptic protects the wound for 5 minutes");
        }
    }

    @Test
    void infectionBlocksHealingAndHurts() {
        Body body = SimTestUtil.body();
        PartState s = body.part(BodyPart.THIGH_R);
        s.skin = 60f;
        s.sutured = true;
        s.infection = 50f;
        body.derived.compute(body);
        float painBefore = body.derived.partPain[BodyPart.THIGH_R.ordinal()];
        assertTrue(painBefore >= 5f, "a 50 % infection hurts");
        runFor(body, 120f);
        assertEquals(60f, s.skin, 0.01f, "no regeneration while infected");
        assertTrue(s.infection > 50f, "untreated infection keeps growing");
    }

    @Test
    void antibioticsBeatTheInfection() {
        Body body = SimTestUtil.body();
        PartState s = body.part(BodyPart.THIGH_R);
        s.skin = 90f;
        s.infection = 30f;
        Treatments.takeAntibiotics(body, 100f);
        runFor(body, 10 * 60f);
        assertTrue(s.infection < 30f, "antibiotics push the infection back: " + s.infection);
    }

    @Test
    void spreadingInfectionCausesSepsisAndFever() {
        Body body = SimTestUtil.body();
        body.part(BodyPart.THIGH_L).infection = 100f;
        body.part(BodyPart.SHIN_L).infection = 60f;
        body.part(BodyPart.ABDOMEN).infection = 30f;
        runFor(body, 10 * 60f);
        assertTrue(body.vitals.sepsis > 10f, "total infection above 100 feeds sepsis: " + body.vitals.sepsis);
        assertTrue(body.vitals.bodyTemp > 38f, "fever: " + body.vitals.bodyTemp);
        assertTrue(InfectionSystem.feverDrive(body) > 1f);
    }

    @Test
    void amputationRemovesTheLimbAndOpensTheStump() {
        Body body = SimTestUtil.body();
        body.part(BodyPart.FOREARM_R).infection = 80f;
        assertTrue(Treatments.canAmputate(BodyPart.FOREARM_R, body.part(BodyPart.FOREARM_R)));
        assertFalse(Treatments.canAmputate(BodyPart.THORAX, body.part(BodyPart.THORAX)));
        Treatments.amputate(body, BodyPart.FOREARM_R, 1f);
        assertTrue(body.part(BodyPart.FOREARM_R).amputated && body.part(BodyPart.HAND_R).amputated);
        assertEquals(0f, body.part(BodyPart.FOREARM_R).infection, 1e-6);
        assertTrue(body.derived.partBleed[BodyPart.UPPER_ARM_R.ordinal()] > 0.2f, "the stump bleeds heavily");
        assertTrue(body.mind.trauma >= 30f);
        Capabilities caps = Capabilities.compute(body, BodyPart.Side.RIGHT);
        assertFalse(caps.mainHandUsable(), "no right hand anymore");
    }

    @Test
    void sawStrokesNeedLengthAndReversals() {
        SawTracker saw = new SawTracker(0f, 30f, 4);
        for (int stroke = 0; stroke < 5; stroke++) {
            for (int i = 0; i <= 40; i++) saw.sample(stroke % 2 == 0 ? i : 40 - i, 1f);
        }
        assertTrue(saw.done(), "five long strokes complete a four-stroke cut: " + saw.strokes());
        assertTrue(saw.quality() > 0.9f);
        SawTracker jitter = new SawTracker(0f, 30f, 4);
        for (int i = 0; i < 100; i++) jitter.sample(i % 2 == 0 ? 0 : 5, 0f);
        assertEquals(0, jitter.strokes(), "tiny jitters do not saw");
    }

    private static void runFor(Body body, float seconds) {
        SimTestUtil.run(body, SimTestUtil.env(), seconds);
    }
}
