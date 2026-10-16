package com.fleshbone.sim;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import com.fleshbone.sim.system.ChemistrySystem;
import com.fleshbone.sim.system.DrugEffects;
import com.fleshbone.sim.system.SleepSystem;
import com.fleshbone.sim.treatment.Treatments;

/** Absorption, duration, tolerance, dependence, withdrawal and side effects. */
class DrugsTest {
    @Test
    void morphineTakesAMinuteAndLastsHalfAnHour() {
        Body body = SimTestUtil.body();
        Treatments.injectOpiate(body, 35f);
        SimTestUtil.run(body, SimTestUtil.env(), SimTestUtil.STEADY, 5f, null);
        assertTrue(ChemistrySystem.relief(body.chem) < 10f, "not yet: " + ChemistrySystem.relief(body.chem));
        SimTestUtil.run(body, SimTestUtil.env(), SimTestUtil.STEADY, 115f, null);
        assertTrue(ChemistrySystem.relief(body.chem) > 25f, "after two minutes: " + ChemistrySystem.relief(body.chem));
        SimTestUtil.run(body, SimTestUtil.env(), SimTestUtil.STEADY, 28 * 60f, null);
        float halfHour = ChemistrySystem.relief(body.chem);
        assertTrue(halfHour > 10f, "still working after half an hour: " + halfHour);
        SimTestUtil.run(body, SimTestUtil.env(), SimTestUtil.STEADY, 30 * 60f, null);
        assertTrue(ChemistrySystem.relief(body.chem) < halfHour / 2f, "mostly gone after an hour: " + ChemistrySystem.relief(body.chem));
    }

    @Test
    void oneShotLeavesOnlyAMildWithdrawal() {
        Body body = SimTestUtil.body();
        Treatments.injectOpiate(body, 35f);
        SimTestUtil.run(body, SimTestUtil.env(), SimTestUtil.STEADY, 90 * 60f, null);
        float withdrawal = ChemistrySystem.withdrawal(body.chem);
        assertTrue(withdrawal > 2f && withdrawal < ChemistrySystem.NAUSEOUS_WITHDRAWAL, "a little craving, no sickness: " + withdrawal);
    }

    @Test
    void repeatedShotsHookTheBody() {
        Body body = SimTestUtil.body();
        float firstRelief = 0f;
        for (int dose = 0; dose < 4; dose++) {
            Treatments.injectOpiate(body, 35f);
            SimTestUtil.run(body, SimTestUtil.env(), SimTestUtil.STEADY, 2 * 60f, null);
            if (dose == 0) firstRelief = ChemistrySystem.relief(body.chem);
            SimTestUtil.run(body, SimTestUtil.env(), SimTestUtil.STEADY, 13 * 60f, null);
        }
        assertTrue(body.chem.opiateTolerance > 25f, "tolerance: " + body.chem.opiateTolerance);
        assertTrue(body.chem.opiateDependence > 35f, "dependence: " + body.chem.opiateDependence);
        // Withdrawal sets in as the last shot wears off
        SimTestUtil.run(body, SimTestUtil.env(), SimTestUtil.STEADY, 45 * 60f, null);
        float withdrawal = ChemistrySystem.withdrawal(body.chem);
        assertTrue(withdrawal > ChemistrySystem.SLEEPLESS_WITHDRAWAL, "withdrawal: " + withdrawal);
        assertTrue(SleepSystem.restless(body), "too restless to sleep");
        assertTrue(body.vitals.heartRate > 80f, "racing heart: " + body.vitals.heartRate);
        assertTrue(body.mind.mood < -20f, "anguish: " + body.mind.mood);
        assertTrue(body.needs.sickness > 5f, "nausea: " + body.needs.sickness);
        // A new shot helps less than the first one did
        Treatments.injectOpiate(body, 35f);
        SimTestUtil.run(body, SimTestUtil.env(), SimTestUtil.STEADY, 2 * 60f, null);
        assertTrue(ChemistrySystem.relief(body.chem) < firstRelief, "tolerance: " + ChemistrySystem.relief(body.chem) + " vs " + firstRelief);
        // Off the drug, it takes hours
        SimTestUtil.run(body, SimTestUtil.env(), SimTestUtil.STEADY, 5 * 3600f, null);
        assertTrue(ChemistrySystem.withdrawal(body.chem) < 12f, "fading after hours: " + ChemistrySystem.withdrawal(body.chem));
    }

    @Test
    void naloxoneThrowsADependentBodyIntoWithdrawal() {
        Body body = SimTestUtil.body();
        for (int dose = 0; dose < 3; dose++) {
            Treatments.injectOpiate(body, 35f);
            SimTestUtil.run(body, SimTestUtil.env(), SimTestUtil.STEADY, 10 * 60f, null);
        }
        Treatments.injectNaloxone(body);
        SimTestUtil.run(body, SimTestUtil.env(), SimTestUtil.STEADY, 10f, null);
        assertTrue(ChemistrySystem.withdrawal(body.chem) > 20f, "precipitated withdrawal: " + ChemistrySystem.withdrawal(body.chem));
    }

    @Test
    void painkillersTakeAFewMinutes() {
        Body body = SimTestUtil.body();
        body.part(BodyPart.FOREARM_L).fracture = 1f;
        body.derived.compute(body);
        float before = body.derived.totalPain;
        Treatments.takeAnalgesic(body, 45f);
        SimTestUtil.run(body, SimTestUtil.env(), SimTestUtil.STEADY, 20f, null);
        assertTrue(before - body.derived.totalPain < 10f, "barely yet");
        SimTestUtil.run(body, SimTestUtil.env(), SimTestUtil.STEADY, 280f, null);
        assertTrue(before - body.derived.totalPain > 15f, "working after five minutes: " + before + " -> " + body.derived.totalPain);
    }

    @Test
    void tooManyPainkillersCanMakeTheStomachBleed() {
        Body body = SimTestUtil.body();
        Treatments.takeAnalgesic(body, 160f);
        SimTestUtil.run(body, SimTestUtil.env(), SimTestUtil.STEADY, 15 * 60f, null);
        assertTrue(body.part(BodyPart.ABDOMEN).internalBleed > 0f || body.vitals.bloodVolume < 4.99f, "an ulcer bled");
    }

    @Test
    void caffeineHoldsTirednessBackThenItComesBack() {
        Body body = SimTestUtil.body();
        Treatments.drink(body, 10f, 0f, 0f, 0f, 40f, new Random(1));
        SimTestUtil.run(body, SimTestUtil.env(), SimTestUtil.STEADY, 30 * 60f, null);
        assertTrue(body.chem.caffeineDebt > 3f, "held back: " + body.chem.caffeineDebt);
        assertTrue(SleepSystem.restless(body) || body.chem.caffeine <= 30f);
        SimTestUtil.run(body, SimTestUtil.env(), SimTestUtil.STEADY, 4 * 3600f, null);
        assertTrue(body.chem.caffeine < 10f && body.chem.caffeineDebt < 0.5f, "the crash came: " + body.chem.caffeineDebt);
    }

    @Test
    void anaphylaxisCrashesThePressureUntilEpinephrine() {
        Body body = SimTestUtil.body();
        DrugEffects.allergicReaction(body);
        SimTestUtil.run(body, SimTestUtil.env(), SimTestUtil.STEADY, 60f, null);
        assertTrue(body.vitals.systolic < 105f, "vessels wide open: " + body.vitals.systolic);
        assertTrue(body.vitals.respRate < 22f, "throat swelling: " + body.vitals.respRate);
        Treatments.injectEpinephrine(body);
        SimTestUtil.run(body, SimTestUtil.env(), SimTestUtil.STEADY, 60f, null);
        assertTrue(body.chem.anaphylaxis < 1f, "epinephrine stops it: " + body.chem.anaphylaxis);
    }

    @Test
    void foodPoisoningBreaksOutSlowly() {
        Body body = SimTestUtil.body();
        Treatments.poison(body, 40f);
        SimTestUtil.run(body, SimTestUtil.env(), SimTestUtil.STEADY, 30f, null);
        assertTrue(body.needs.sickness < 5f, "fine at first: " + body.needs.sickness);
        SimTestUtil.run(body, SimTestUtil.env(), SimTestUtil.STEADY, 10 * 60f, null);
        assertTrue(body.needs.sickness > 15f, "sick ten minutes later: " + body.needs.sickness);
        assertTrue(body.vitals.bodyTemp > 37.2f, "feverish: " + body.vitals.bodyTemp);
        assertTrue(body.part(BodyPart.ABDOMEN).pain > 5f, "cramps: " + body.part(BodyPart.ABDOMEN).pain);
    }
}
