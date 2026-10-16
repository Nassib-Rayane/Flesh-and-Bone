package com.fleshbone.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fleshbone.sim.system.CirculationSystem;

class PhysiologyTest {
    @Test
    void bloodLossLowersPressureAndRaisesHeartRate() {
        Body body = SimTestUtil.body();
        body.vitals.bloodVolume = 4.0f;
        Environment env = SimTestUtil.env();
        env.foodLevel = 0; // freeze regeneration
        SimTestUtil.run(body, env, 120f);
        assertTrue(body.vitals.systolic < 110f && body.vitals.systolic > 90f, "1 L lost: mild hypotension, got " + body.vitals.systolic);
        assertTrue(body.vitals.heartRate > 95f, "compensating tachycardia, got " + body.vitals.heartRate);
        assertFalse(body.vitals.cardiacArrest);
    }

    @Test
    void severeHemorrhageMakesTheHeartFibrillate() {
        Body body = SimTestUtil.body();
        body.vitals.bloodVolume = 2.3f;
        Environment env = SimTestUtil.env();
        env.foodLevel = 0;
        SimTestUtil.run(body, env, 60f);
        assertTrue(body.vitals.fibrillation > 10f, "fibrillation builds below 88 systolic, got " + body.vitals.fibrillation);
        float t = SimTestUtil.run(body, env, 20 * 60f, b -> b.vitals.cardiacArrest);
        assertTrue(body.vitals.cardiacArrest, "the heart eventually stops");
        assertEquals(DeathCause.HEMORRHAGE, body.vitals.pendingCause);
        assertTrue(t < 10 * 60f, "within 10 minutes, took " + t);
    }

    @Test
    void cardiacArrestLeadsToBrainDeathInAFewMinutes() {
        Body body = SimTestUtil.body();
        CirculationSystem.stopHeart(body);
        Environment env = SimTestUtil.env();
        SimTestUtil.run(body, env, 10f);
        assertTrue(body.isUnconscious(), "unconscious within seconds");
        float t = SimTestUtil.run(body, env, 10 * 60f, b -> b.deathCause() != null);
        assertEquals(DeathCause.CARDIAC_ARREST, body.deathCause());
        assertTrue(t > 90f && t < 5 * 60f, "brain death after 1.5–5 minutes, took " + t);
    }

    @Test
    void cprKeepsTheBrainAlive() {
        Body body = SimTestUtil.body();
        CirculationSystem.stopHeart(body);
        Environment env = SimTestUtil.env();
        java.util.Random rng = new java.util.Random(5);
        for (int tick = 0; tick < 20 * 60 * 3; tick++) { // 3 minutes of compressions
            body.vitals.cprPerfusion = 1f;
            body.tick(env, SimSettings.DEFAULT, rng);
        }
        assertTrue(body.vitals.brainHealth > 80f, "CPR slows brain damage, brain at " + body.vitals.brainHealth);
    }

    @Test
    void drowningKnocksOutThenKills() {
        Body body = SimTestUtil.body();
        Environment env = SimTestUtil.env();
        env.breathingBlocked = true;
        float knockedOut = SimTestUtil.run(body, env, 5 * 60f, Body::isIncapacitated);
        assertTrue(knockedOut < 70f, "incapacitated within about a minute without air, took " + knockedOut);
        SimTestUtil.run(body, env, 10 * 60f, b -> b.deathCause() != null);
        assertEquals(DeathCause.ASPHYXIA, body.deathCause());
    }

    @Test
    void agonyCausesPainShock() {
        Body body = SimTestUtil.body();
        body.part(BodyPart.SHIN_L).pain = 150f;
        body.part(BodyPart.SHIN_L).fracture = 1f;
        body.derived.compute(body);
        assertTrue(body.derived.totalPain > 80f);
        float t = SimTestUtil.run(body, SimTestUtil.env(), 120f, Body::isUnconscious);
        assertTrue(body.isUnconscious(), "pain shock knocks out");
        assertTrue(t < 60f, "within a minute, took " + t);
    }

    @Test
    void concussionCapsConsciousness() {
        Body body = SimTestUtil.body();
        body.part(BodyPart.HEAD).muscle = 10f;
        SimTestUtil.run(body, SimTestUtil.env(), 10f);
        assertTrue(body.vitals.consciousness <= 25f, "concussed: below the incapacitation threshold");
    }

    @Test
    void adrenalineDullsPain() {
        Body body = SimTestUtil.body();
        body.part(BodyPart.FOREARM_L).fracture = 1f;
        body.derived.compute(body);
        float calm = body.derived.totalPain;
        body.vitals.adrenaline = 40f;
        body.derived.compute(body);
        assertEquals(calm - 20f, body.derived.totalPain, 1e-3);
    }
}
