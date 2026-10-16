package com.fleshbone.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class HealingTest {
    @Test
    void fractureHealsInConfiguredTime() {
        Body body = SimTestUtil.body();
        body.part(BodyPart.SHIN_L).fracture = 1f;
        float t = SimTestUtil.run(body, SimTestUtil.env(), SimTestUtil.STEADY, 60 * 60f, b -> !b.part(BodyPart.SHIN_L).isFractured());
        assertEquals(SimSettings.DEFAULT.fractureHealSeconds(), t, 5f);
    }

    @Test
    void splintHealsTwoAndAHalfTimesFaster() {
        Body body = SimTestUtil.body();
        body.part(BodyPart.FOREARM_R).fracture = 1f;
        body.part(BodyPart.FOREARM_R).splinted = true;
        float t = SimTestUtil.run(body, SimTestUtil.env(), SimTestUtil.STEADY, 60 * 60f, b -> !b.part(BodyPart.FOREARM_R).isFractured());
        assertEquals(SimSettings.DEFAULT.fractureHealSeconds() / 2.5f, t, 5f);
        assertFalse(body.part(BodyPart.FOREARM_R).splinted, "splint comes off once healed");
        assertTrue(body.drainEvents().stream().anyMatch(e -> e.type() == BodyEvent.Type.FRACTURE_HEALED));
    }

    @Test
    void starvingBodyDoesNotHeal() {
        Body body = SimTestUtil.body();
        body.part(BodyPart.THIGH_L).skin = 50f;
        body.part(BodyPart.THIGH_L).fracture = 1f;
        body.needs.hunger = 0f;
        Environment env = SimTestUtil.env();
        SimTestUtil.run(body, env, 10 * 60f);
        assertEquals(50f, body.part(BodyPart.THIGH_L).skin, 1e-3);
        assertEquals(1f, body.part(BodyPart.THIGH_L).fracture, 1e-6);
    }

    @Test
    void skinRegrowsInAboutTwentyFiveMinutes() {
        Body body = SimTestUtil.body();
        body.part(BodyPart.HAND_L).skin = 20f;
        float t = SimTestUtil.run(body, SimTestUtil.env(), SimTestUtil.STEADY, 60 * 60f, b -> b.part(BodyPart.HAND_L).skin >= 100f);
        assertTrue(t > 15 * 60f && t < 25 * 60f, "80 points at 0.069/s take about 19 minutes, took " + t);
    }

    @Test
    void shrapnelBlocksRegeneration() {
        Body body = SimTestUtil.body();
        body.part(BodyPart.THORAX).muscle = 60f;
        body.part(BodyPart.THORAX).shrapnel = 1;
        SimTestUtil.run(body, SimTestUtil.env(), 120f);
        assertEquals(60f, body.part(BodyPart.THORAX).muscle, 1e-3);
    }

    @Test
    void fractureCapsMuscleAtHalf() {
        Body body = SimTestUtil.body();
        body.part(BodyPart.UPPER_ARM_L).muscle = 30f;
        body.part(BodyPart.UPPER_ARM_L).fracture = 1f;
        SimTestUtil.run(body, SimTestUtil.env(), 10 * 60f);
        assertEquals(50f, body.part(BodyPart.UPPER_ARM_L).muscle, 1e-3);
    }

    @Test
    void forgottenTourniquetKillsTheLimb() {
        Body body = SimTestUtil.body();
        body.part(BodyPart.THIGH_R).tourniquet = true;
        SimTestUtil.run(body, SimTestUtil.env(), 10 * 60f);
        for (BodyPart part : List.of(BodyPart.THIGH_R, BodyPart.SHIN_R)) {
            assertTrue(body.part(part).muscle < 50f, part + " starved of blood");
        }
        assertEquals(100f, body.part(BodyPart.THIGH_L).muscle, 1e-3);
    }

    @Test
    void saveFormatRoundTrips() {
        Body body = SimTestUtil.body();
        body.part(BodyPart.SHIN_L).fracture = 0.4f;
        body.part(BodyPart.SHIN_L).bandage = BandageType.STERILE;
        body.part(BodyPart.HEAD).shrapnel = 2;
        body.vitals.bloodVolume = 3.9f;
        body.vitals.cardiacArrest = true;
        body.chem.opiate = 33f;
        body.needs.thirst = 41f;
        body.needs.hunger = -12f;
        body.needs.weight = 64.5f;
        body.skills.addXp(Skills.Stat.RESILIENCE, 130f);
        body.senses.leftEyeLost = true;
        body.vitals.baroreflex = 33f;
        Body copy = SimTestUtil.copy(body);
        assertEquals(0.4f, copy.part(BodyPart.SHIN_L).fracture, 1e-6);
        assertEquals(BandageType.STERILE, copy.part(BodyPart.SHIN_L).bandage);
        assertEquals(2, copy.part(BodyPart.HEAD).shrapnel);
        assertEquals(3.9f, copy.vitals.bloodVolume, 1e-6);
        assertTrue(copy.vitals.cardiacArrest);
        assertEquals(33f, copy.chem.opiate, 1e-6);
        assertEquals(41f, copy.needs.thirst, 1e-6);
        assertEquals(-12f, copy.needs.hunger, 1e-6);
        assertEquals(64.5f, copy.needs.weight, 1e-6);
        assertEquals(11, copy.skills.resilience());
        assertEquals(30f, copy.skills.xp(Skills.Stat.RESILIENCE), 1e-4);
        assertTrue(copy.senses.leftEyeLost);
        assertEquals(33f, copy.vitals.baroreflex, 1e-6);
        assertEquals(body.vitals.bloodType, copy.vitals.bloodType);
    }

    @Test
    void bodyPartTreeIsConsistent() {
        assertEquals(List.of(BodyPart.UPPER_ARM_L, BodyPart.FOREARM_L, BodyPart.HAND_L), BodyPart.UPPER_ARM_L.distal());
        assertTrue(BodyPart.THORAX.adjacent().containsAll(List.of(BodyPart.HEAD, BodyPart.ABDOMEN, BodyPart.UPPER_ARM_R)));
        for (BodyPart part : BodyPart.VALUES) assertEquals(part, BodyPart.byName(part.serializedName()));
        assertEquals(BodyPart.SHIN_R, BodyPart.SHIN_L.mirror());
    }
}
