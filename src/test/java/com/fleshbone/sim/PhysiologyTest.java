package com.fleshbone.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fleshbone.sim.system.CirculationSystem;
import com.fleshbone.sim.system.RespirationSystem;
import com.fleshbone.sim.system.StaminaSystem;
import com.fleshbone.sim.treatment.Treatments;

/** Heart, vessels, lungs, brain and consciousness, and the chains between them. */
class PhysiologyTest {
    @Test
    void healthyBodyRestsAtCasualtiesUnknownValues() {
        Body body = SimTestUtil.body();
        SimTestUtil.run(body, SimTestUtil.env(), 60f);
        assertEquals(70f, body.vitals.heartRate, 3f);
        assertEquals(120f, body.vitals.systolic, 3f);
        assertEquals(80f, body.vitals.diastolic, 2f);
        assertEquals(25f, body.vitals.respRate, 1f);
        assertEquals(100f, body.vitals.spo2, 0.5f);
        assertEquals(37f, body.vitals.bodyTemp, 0.3f);
    }

    @Test
    void bloodLossLowersPressureAndTheBaroreflexRaisesHeartRate() {
        Body body = SimTestUtil.body();
        body.vitals.bloodVolume = 4.0f;
        body.needs.hunger = 0f; // no regeneration
        SimTestUtil.run(body, SimTestUtil.env(), 120f);
        assertTrue(body.vitals.systolic < 112f && body.vitals.systolic > 95f, "1 L lost: mild hypotension, got " + body.vitals.systolic);
        assertTrue(body.vitals.baroreflex > 25f, "the baroreflex speeds the heart up, got " + body.vitals.baroreflex);
        assertTrue(body.vitals.heartRate > 95f, "compensating tachycardia, got " + body.vitals.heartRate);
        assertFalse(body.vitals.cardiacArrest);
    }

    @Test
    void severeHemorrhageMakesTheHeartFibrillate() {
        Body body = SimTestUtil.body();
        body.vitals.bloodVolume = 2.3f;
        body.needs.hunger = 0f;
        SimTestUtil.run(body, SimTestUtil.env(), 60f);
        assertTrue(body.vitals.systolic < CirculationSystem.FIBRILLATION_TRIGGER, "decompensated: " + body.vitals.systolic);
        assertTrue(body.vitals.fibrillation > 10f, "fibrillation starts below 78 systolic, got " + body.vitals.fibrillation);
        float t = SimTestUtil.run(body, SimTestUtil.env(), 20 * 60f, b -> b.vitals.cardiacArrest);
        assertTrue(body.vitals.cardiacArrest, "the heart eventually stops");
        assertEquals(DeathCause.HEMORRHAGE, body.vitals.pendingCause);
        assertTrue(t < 5 * 60f, "within 5 minutes, took " + t);
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
        assertTrue(t > 60f && t < 5 * 60f, "brain death after 1–5 minutes, took " + t);
    }

    @Test
    void cprKeepsTheBrainAlive() {
        Body body = SimTestUtil.body();
        CirculationSystem.stopHeart(body);
        Environment env = SimTestUtil.env();
        java.util.Random rng = new java.util.Random(5);
        for (int tick = 0; tick < 20 * 60 * 3; tick++) { // 3 minutes of compressions
            body.vitals.cprPerfusion = 1f;
            body.tick(env, SimTestUtil.SETTINGS, rng);
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
    void exertionRaisesHeartRateAndBreathingThenRestBringsThemBack() {
        Body body = SimTestUtil.body();
        Environment env = SimTestUtil.env();
        env.sprinting = true;
        env.moveSpeed = 5.6f;
        SimTestUtil.run(body, env, 40f);
        float stamina = body.needs.stamina;
        assertTrue(stamina < 70f, "40 s of sprinting at 1 %/s: " + stamina);
        assertTrue(body.vitals.heartRate > 85f, "missing stamina adds 0.6 bpm a point: " + body.vitals.heartRate);
        assertTrue(body.vitals.respRate > 27f, "and 0.1 breath a point: " + body.vitals.respRate);
        env.sprinting = false;
        env.moveSpeed = 0f;
        SimTestUtil.run(body, env, 60f);
        assertTrue(body.needs.stamina > 95f, "1.4 %/s standing still: " + body.needs.stamina);
        assertTrue(body.vitals.heartRate < 80f, "heart back down: " + body.vitals.heartRate);
    }

    @Test
    void staminaCannotExceedBloodOxygen() {
        Body body = SimTestUtil.body();
        body.vitals.hemothorax = 50f; // SpO2 max 85
        SimTestUtil.run(body, SimTestUtil.env(), 30f);
        assertTrue(body.vitals.spo2 <= RespirationSystem.maxSaturation(body) + 0.01f);
        assertTrue(body.needs.stamina <= Math.max(StaminaSystem.MIN_CAP, body.vitals.spo2) + 0.01f,
                "stamina " + body.needs.stamina + " capped by SpO2 " + body.vitals.spo2);
        assertTrue(body.needs.stamina < 90f);
    }

    @Test
    void staminaStrengthIsCubic() {
        assertEquals(1f, StaminaSystem.strength(100f), 1e-6);
        assertEquals(0.973f, StaminaSystem.strength(70f), 0.005f);
        assertEquals(0.725f, StaminaSystem.strength(35f), 0.01f);
        assertEquals(0.386f, StaminaSystem.strength(15f), 0.01f);
        assertEquals(0f, StaminaSystem.strength(0f), 1e-6);
    }

    @Test
    void opioidOverdoseStopsBreathingAndStarvesTheBlood() {
        Body body = SimTestUtil.body();
        Treatments.injectOpiate(body, 130f);
        SimTestUtil.run(body, SimTestUtil.env(), 30f);
        assertTrue(body.vitals.respRate < 10f, "respiratory depression: " + body.vitals.respRate);
        SimTestUtil.run(body, SimTestUtil.env(), 60f);
        assertTrue(body.vitals.spo2 < 90f, "below 12.5 breaths a minute the blood loses oxygen: " + body.vitals.spo2);
        assertEquals(DeathCause.OVERDOSE, DeathCheck.classify(body));
    }

    @Test
    void coldTightensVesselsAndRaisesPressure() {
        Body body = SimTestUtil.body();
        body.vitals.bodyTemp = 34f;
        Environment env = SimTestUtil.env();
        env.ambientTemp = -25f;
        SimTestUtil.run(body, env, 60f);
        assertTrue(body.vitals.vesselTone > 0.4f, "vasoconstriction: " + body.vitals.vesselTone);
        assertTrue(body.vitals.systolic > 124f, "pressure up: " + body.vitals.systolic);
    }

    @Test
    void heavyBodyRunsHighPressure() {
        Body body = SimTestUtil.body();
        body.needs.weight = 95f;
        SimTestUtil.run(body, SimTestUtil.env(), 60f);
        assertEquals(WeightClass.OBESE, body.derived.weightClass);
        assertTrue(body.vitals.systolic > 135f, "obese: +48 before the baroreflex, got " + body.vitals.systolic);
        assertTrue(body.vitals.heartRate < 60f, "the baroreflex slows the heart: " + body.vitals.heartRate);
    }

    @Test
    void dehydrationThickensTheBlood() {
        Body body = SimTestUtil.body();
        body.needs.thirst = -30f;
        SimTestUtil.run(body, SimTestUtil.env(), 300f);
        assertTrue(body.vitals.viscosity > 20f, "viscosity " + body.vitals.viscosity);
        assertTrue(body.vitals.vesselTone > 0.2f, "dehydrated vessels clamp down: " + body.vitals.vesselTone);
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

    @Test
    void resilienceScalesPain() {
        Body body = SimTestUtil.body();
        body.part(BodyPart.FOREARM_L).fracture = 1f;
        body.derived.compute(body);
        float average = body.derived.totalPain;
        body.skills.setLevel(Skills.Stat.RESILIENCE, 20);
        body.derived.compute(body);
        assertEquals(average * 0.75f, body.derived.totalPain, 1e-3);
    }
}
