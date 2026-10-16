package com.fleshbone.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import com.fleshbone.sim.system.BleedingSystem;
import com.fleshbone.sim.treatment.Treatments;

class TreatmentTest {
    private static Body bleedingThigh() {
        Body body = SimTestUtil.body();
        body.part(BodyPart.THIGH_L).skin = 30f;
        body.part(BodyPart.THIGH_L).bleed = 40f;
        body.derived.compute(body);
        return body;
    }

    @Test
    void bandagingTheBleedingPartSavesTheBody() {
        Body body = bleedingThigh();
        assertEquals(BodyPart.THIGH_L, Treatments.bandageTarget(body));
        Treatments.bandage(body, BodyPart.THIGH_L, BandageType.BANDAGE, 1f);
        SimTestUtil.run(body, SimTestUtil.env(), 10 * 60f);
        assertNull(body.deathCause());
        assertTrue(body.vitals.bloodVolume > 4.0f, "a good bandage keeps most of the blood in: " + body.vitals.bloodVolume);
    }

    @Test
    void tourniquetGoesAboveABleedingHand() {
        Body body = SimTestUtil.body();
        body.part(BodyPart.HAND_R).skin = 20f;
        body.part(BodyPart.HAND_R).bleed = 30f;
        body.derived.compute(body);
        assertEquals(BodyPart.FOREARM_R, Treatments.tourniquetTarget(body));
        Treatments.tourniquet(body, BodyPart.FOREARM_R);
        assertEquals(0f, BleedingSystem.externalRate(body, BodyPart.HAND_R), 1e-6);
    }

    @Test
    void antisepticStingsButCleans() {
        Body body = SimTestUtil.body();
        PartState s = body.part(BodyPart.FOREARM_L);
        s.skin = 50f;
        s.contamination = 40f;
        Treatments.disinfect(body, BodyPart.FOREARM_L, Treatments.ANTISEPTIC_SECONDS);
        assertEquals(0f, s.contamination, 1e-6);
        assertTrue(s.disinfected > 0f);
        assertTrue(s.pain >= 15f);
    }

    @Test
    void splintOnlyForBrokenOrDislocated() {
        Body body = SimTestUtil.body();
        assertNull(Treatments.splintTarget(body));
        body.part(BodyPart.SHIN_R).fracture = 1f;
        assertEquals(BodyPart.SHIN_R, Treatments.splintTarget(body));
        Treatments.splint(body, BodyPart.SHIN_R);
        assertFalse(Treatments.canSplint(body.part(BodyPart.SHIN_R)));
    }

    @Test
    void dislocationCannotBeResetInAgony() {
        Body body = SimTestUtil.body();
        PartState s = body.part(BodyPart.UPPER_ARM_L);
        s.dislocation = 1f;
        s.pain = 120f;
        body.derived.compute(body);
        assertEquals(Treatments.ResetResult.TOO_PAINFUL, Treatments.resetDislocation(body, BodyPart.UPPER_ARM_L, 1f, new Random(1)));
        s.pain = 0f;
        body.derived.compute(body);
        assertEquals(Treatments.ResetResult.SUCCESS, Treatments.resetDislocation(body, BodyPart.UPPER_ARM_L, 1f, new Random(1)));
        assertFalse(s.isDislocated());
    }

    @Test
    void tweezersPullFragmentsMoreReliablyThanFingers() {
        int tweezers = 0;
        int fingers = 0;
        Random rng = new Random(11);
        for (int n = 0; n < 1000; n++) {
            Body a = SimTestUtil.body();
            a.part(BodyPart.SHIN_L).shrapnel = 1;
            if (Treatments.extractShrapnel(a, BodyPart.SHIN_L, 0.5f, true, rng)) tweezers++;
            Body b = SimTestUtil.body();
            b.part(BodyPart.SHIN_L).shrapnel = 1;
            if (Treatments.extractShrapnel(b, BodyPart.SHIN_L, 0.5f, false, rng)) fingers++;
        }
        assertTrue(tweezers > fingers + 200, "tweezers " + tweezers + " vs fingers " + fingers);
    }

    @Test
    void morphineDullsPainAndStacksTowardOverdose() {
        Body body = SimTestUtil.body();
        body.part(BodyPart.SHIN_L).fracture = 1f;
        body.derived.compute(body);
        float before = body.derived.totalPain;
        Treatments.injectOpiate(body, 35f);
        SimTestUtil.run(body, SimTestUtil.env(), 120f);
        body.derived.compute(body);
        assertTrue(body.derived.totalPain < before - 20f, "works within a couple of minutes: " + before + " -> " + body.derived.totalPain);
        Treatments.injectOpiate(body, 35f);
        Treatments.injectOpiate(body, 35f);
        SimTestUtil.run(body, SimTestUtil.env(), 120f);
        assertTrue(body.chem.opiate > 80f, "three doses at once is an overdose: " + body.chem.opiate);
    }
}
