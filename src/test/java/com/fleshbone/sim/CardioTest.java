package com.fleshbone.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import com.fleshbone.sim.minigame.ChargeMeter;
import com.fleshbone.sim.minigame.RhythmTracker;
import com.fleshbone.sim.system.CirculationSystem;
import com.fleshbone.sim.treatment.Treatments;

class CardioTest {
    @Test
    void earlyShockWithEpinephrineRestartsTheHeartOftenEnough() {
        int restarted = 0;
        Random rng = new Random(3);
        for (int n = 0; n < 500; n++) {
            Body body = SimTestUtil.body();
            CirculationSystem.stopHeart(body);
            body.chem.epinephrine = 60f;
            if (Treatments.defibrillate(body, 1f, rng) == Treatments.ShockResult.RESTARTED) restarted++;
        }
        assertTrue(restarted > 150 && restarted < 250, "one in five, doubled by epinephrine: " + restarted);
    }

    @Test
    void lateShockRarelyWorks() {
        int restarted = 0;
        Random rng = new Random(4);
        for (int n = 0; n < 500; n++) {
            Body body = SimTestUtil.body();
            CirculationSystem.stopHeart(body);
            body.vitals.arrestTime = 230f;
            if (Treatments.defibrillate(body, 1f, rng) == Treatments.ShockResult.RESTARTED) restarted++;
        }
        assertTrue(restarted < 50, "after four minutes of arrest it is almost hopeless: " + restarted);
    }

    @Test
    void epinephrineRestartsAPerfusedHeart() {
        Body body = SimTestUtil.body();
        CirculationSystem.stopHeart(body);
        Treatments.injectEpinephrine(body);
        Environment env = SimTestUtil.env();
        Random rng = new Random(12);
        int tick = 0;
        for (; tick < 20 * 180 && body.vitals.cardiacArrest; tick++) {
            body.vitals.cprPerfusion = 1f;
            body.tick(env, SimTestUtil.SETTINGS, rng);
        }
        assertFalse(body.vitals.cardiacArrest, "3 % a second with CPR");
    }

    @Test
    void shockingAHealthyHeartCausesFibrillation() {
        Body body = SimTestUtil.body();
        assertEquals(Treatments.ShockResult.INDUCED_FIBRILLATION, Treatments.defibrillate(body, 1f, new Random(1)));
        assertTrue(body.vitals.fibrillation >= 40f);
    }

    @Test
    void wrongBloodGroupCausesAReaction() {
        Body body = SimTestUtil.body();
        body.vitals.bloodType = BloodType.O_NEG;
        body.vitals.bloodVolume = 4f;
        assertFalse(Treatments.transfuse(body, BloodType.A_POS, 0.45f));
        assertEquals(4f, body.vitals.bloodVolume, 1e-6, "no volume from incompatible blood");
        assertTrue(body.vitals.sepsis >= 25f);
        assertTrue(Treatments.transfuse(body, BloodType.O_NEG, 0.45f));
        assertEquals(4.45f, body.vitals.bloodVolume, 1e-5);
    }

    @Test
    void procoagulantOverdoseThrowsAClotThatThrombolyticsDissolve() {
        Body body = SimTestUtil.body();
        Treatments.injectProcoagulant(body);
        Treatments.injectProcoagulant(body);
        Environment env = SimTestUtil.env();
        Random rng = new Random(9);
        for (int tick = 0; tick < 20 * 1200 && !body.vitals.embolism; tick++) {
            if (tick % (20 * 60) == 0) Treatments.injectProcoagulant(body);
            body.tick(env, SimTestUtil.SETTINGS, rng);
        }
        assertTrue(body.vitals.embolism, "thick blood eventually clots in the lungs (viscosity " + body.vitals.viscosity + ")");
        float thorax = body.part(BodyPart.THORAX).muscle;
        SimTestUtil.run(body, env, 10f);
        assertTrue(body.part(BodyPart.THORAX).muscle < thorax, "the embolism strangles the chest");
        Treatments.injectThrombolytic(body);
        assertFalse(body.vitals.embolism);
    }

    @Test
    void chestDrainEmptiesHemothorax() {
        Body body = SimTestUtil.body();
        body.vitals.hemothorax = 50f;
        assertTrue(Treatments.canDrain(body));
        Treatments.chestDrain(body);
        assertEquals(15f, body.vitals.hemothorax, 1e-6);
    }

    @Test
    void naloxoneReversesAnOverdose() {
        Body body = SimTestUtil.body();
        Treatments.injectOpiate(body, 100f);
        Treatments.injectNaloxone(body);
        assertTrue(body.chem.opiate < 15f);
    }

    @Test
    void chargeMeterRewardsTheTopBand() {
        ChargeMeter meter = new ChargeMeter(1f);
        meter.press(0f);
        assertEquals(1f, meter.release(0.9f), 1e-6);
        meter.press(0f);
        assertTrue(meter.release(0.3f) < 0.3f, "undercharged");
        meter.press(0f);
        assertTrue(meter.release(1.25f) < 0.3f, "overcharged");
    }

    @Test
    void steadyRhythmGivesFullPerfusion() {
        RhythmTracker rhythm = new RhythmTracker();
        float beat = 60f / RhythmTracker.TARGET_BPM;
        for (int i = 0; i < 20; i++) rhythm.compress(i * beat);
        assertTrue(rhythm.perfusion(19 * beat) > 0.9f, "on-beat compressions: " + rhythm.perfusion(19 * beat));
        RhythmTracker slow = new RhythmTracker();
        for (int i = 0; i < 10; i++) slow.compress(i * 1.2f);
        assertTrue(slow.perfusion(9 * 1.2f) < 0.3f, "one compression a second is too slow");
    }
}
