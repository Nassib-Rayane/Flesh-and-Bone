package com.fleshbone.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.fleshbone.sim.injury.Injury;
import com.fleshbone.sim.injury.InjuryKind;
import com.fleshbone.sim.system.BleedingSystem;

class BleedingTest {
    @Test
    void healthyBodyDoesNotBleed() {
        Body body = SimTestUtil.body();
        SimTestUtil.run(body, SimTestUtil.env(), 60f);
        assertEquals(0f, body.derived.totalBleed(), 1e-6);
        assertEquals(Vitals.NORMAL_BLOOD_VOLUME, body.vitals.bloodVolume, 1e-4);
    }

    @Test
    void fullSkinCannotBleed() {
        Body body = SimTestUtil.body();
        body.part(BodyPart.FOREARM_L).bleed = 50f;
        assertEquals(0f, BleedingSystem.externalRate(body, BodyPart.FOREARM_L), 1e-6);
    }

    @Test
    void zombieCutClotsOnItsOwn() {
        Body body = SimTestUtil.body();
        body.injure(Injury.of(BodyPart.FOREARM_R, InjuryKind.CUT, 15f), new Random(3));
        float rate = BleedingSystem.externalRate(body, BodyPart.FOREARM_R);
        assertTrue(rate > 0.15f && rate < 0.35f, "a 15-severity cut bleeds about 0.27 L/min, got " + rate);
        SimTestUtil.run(body, SimTestUtil.env(), 15 * 60f);
        assertEquals(0f, body.part(BodyPart.FOREARM_R).bleed, 1e-3, "clotted after 15 minutes");
        float lost = Vitals.NORMAL_BLOOD_VOLUME - body.vitals.bloodVolume;
        assertTrue(lost < 0.8f, "a single cut is survivable, lost " + lost + " L");
        assertNull(body.deathCause());
    }

    @Test
    void untreatedHeavyBleedingKills() {
        Body body = SimTestUtil.body();
        PartState thigh = body.part(BodyPart.THIGH_L);
        thigh.skin = 10f;
        thigh.bleed = 80f;
        float t = SimTestUtil.run(body, SimTestUtil.env(), 30 * 60f, b -> b.deathCause() != null);
        assertNotNull(body.deathCause(), "a severed artery kills without treatment");
        assertEquals(DeathCause.HEMORRHAGE, body.deathCause());
        assertTrue(t < 15 * 60f, "death within 15 minutes, took " + t + " s");
    }

    @Test
    void tourniquetStopsTheWholeLimbBelowIt() {
        Body body = SimTestUtil.body();
        for (BodyPart part : List.of(BodyPart.FOREARM_L, BodyPart.HAND_L)) {
            body.part(part).skin = 30f;
            body.part(part).bleed = 60f;
        }
        body.part(BodyPart.UPPER_ARM_L).tourniquet = true;
        assertEquals(0f, BleedingSystem.externalRate(body, BodyPart.FOREARM_L), 1e-6);
        assertEquals(0f, BleedingSystem.externalRate(body, BodyPart.HAND_L), 1e-6);
        body.part(BodyPart.THIGH_L).skin = 30f;
        body.part(BodyPart.THIGH_L).bleed = 60f;
        assertTrue(BleedingSystem.externalRate(body, BodyPart.THIGH_L) > 0f, "other limbs are unaffected");
    }

    @Test
    void goodBandageCutsMostBleeding() {
        Body body = SimTestUtil.body();
        PartState shin = body.part(BodyPart.SHIN_R);
        shin.skin = 40f;
        shin.bleed = 40f;
        float before = BleedingSystem.externalRate(body, BodyPart.SHIN_R);
        shin.bandage = BandageType.BANDAGE;
        shin.bandageQuality = 1f;
        float after = BleedingSystem.externalRate(body, BodyPart.SHIN_R);
        assertTrue(after < before * 0.2f, "bandage at full quality stops more than 80 %: " + before + " -> " + after);
    }

    @Test
    void lostBloodRegeneratesWithFood() {
        Body body = SimTestUtil.body();
        body.vitals.bloodVolume = 4.5f;
        SimTestUtil.run(body, SimTestUtil.env(), 5 * 60f);
        assertTrue(body.vitals.bloodVolume > 4.7f, "about 0.26 L in 5 minutes, got " + body.vitals.bloodVolume);
        Body intact = SimTestUtil.body();
        SimTestUtil.run(intact, SimTestUtil.env(), 5 * 60f);
        assertTrue(body.needs.hunger < intact.needs.hunger - 5f, "new blood costs food: " + body.needs.hunger + " vs " + intact.needs.hunger);

        Body starving = SimTestUtil.body();
        starving.vitals.bloodVolume = 4.5f;
        starving.needs.hunger = 0f;
        Environment env = SimTestUtil.env();
        SimTestUtil.run(starving, env, 5 * 60f);
        assertEquals(4.5f, starving.vitals.bloodVolume, 1e-3, "no regeneration while starving");
    }
}
