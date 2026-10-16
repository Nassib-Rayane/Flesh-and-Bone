package com.fleshbone.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import com.fleshbone.sim.BodyPart.Side;
import com.fleshbone.sim.injury.HitDistribution;
import com.fleshbone.sim.injury.Injury;
import com.fleshbone.sim.injury.InjuryKind;

class InjuryTest {
    @Test
    void cutDamagesSkinMoreThanMuscle() {
        Body body = SimTestUtil.body();
        body.injure(Injury.of(BodyPart.FOREARM_L, InjuryKind.CUT, 15f), new Random(1));
        PartState s = body.part(BodyPart.FOREARM_L);
        assertEquals(82f, s.skin, 1e-3);
        assertEquals(94f, s.muscle, 1e-3);
        assertEquals(15f, s.bleed, 1e-3);
        assertTrue(s.contamination > 0f);
        assertTrue(body.vitals.adrenaline > 0f);
    }

    @Test
    void bruiseDoesNotBleed() {
        Body body = SimTestUtil.body();
        body.injure(Injury.of(BodyPart.THIGH_R, InjuryKind.BLUNT, 8f), new Random(1));
        assertEquals(0f, body.part(BodyPart.THIGH_R).bleed, 1e-6);
    }

    @Test
    void tenBlockFallBreaksALegAboutHalfTheTime() {
        int broken = 0;
        int trials = 2000;
        Random rng = new Random(42);
        for (int n = 0; n < trials; n++) {
            Body body = new Body();
            body.injureAll(HitDistribution.fall(35f, rng), rng); // 7 damage × 5
            if (body.part(BodyPart.SHIN_L).isFractured() || body.part(BodyPart.SHIN_R).isFractured()) broken++;
        }
        float ratio = broken / (float) trials;
        assertTrue(ratio > 0.3f && ratio < 0.65f, "about half of 10-block falls break a shin, got " + ratio);
    }

    @Test
    void shortFallIsHarmless() {
        Random rng = new Random(42);
        for (int n = 0; n < 200; n++) {
            Body body = new Body();
            body.injureAll(HitDistribution.fall(10f, rng), rng); // 2 damage × 5
            assertFalse(body.part(BodyPart.SHIN_L).isFractured() || body.part(BodyPart.SHIN_R).isFractured());
        }
    }

    @Test
    void pointBlankCreeperKnocksOut() {
        Body body = SimTestUtil.body();
        Random rng = new Random(9);
        body.injureAll(HitDistribution.explosion(200f, Side.LEFT, rng), rng);
        SimTestUtil.run(body, SimTestUtil.env(), 30f, Body::isIncapacitated);
        assertTrue(body.isIncapacitated(), "a point-blank blast leaves you on the ground");
    }

    @Test
    void crushedHeadKills() {
        Body body = SimTestUtil.body();
        body.injure(Injury.of(BodyPart.HEAD, InjuryKind.CRUSH, 120f), new Random(1));
        assertEquals(DeathCause.BRAIN_TRAUMA, body.deathCause());
    }

    @Test
    void hitsOnAMissingLimbLandOnTheStump() {
        Body body = SimTestUtil.body();
        body.part(BodyPart.HAND_R).amputated = true;
        body.injure(Injury.of(BodyPart.HAND_R, InjuryKind.CUT, 20f), new Random(1));
        assertTrue(body.part(BodyPart.FOREARM_R).skin < 100f);
    }

    @Test
    void heightMapsToTheRightParts() {
        Random rng = new Random(3);
        assertEquals(BodyPart.HEAD, HitDistribution.partAtHeight(0.95f, Side.CENTER, rng));
        assertEquals(BodyPart.SHIN_L, HitDistribution.partAtHeight(0.1f, Side.LEFT, rng));
        assertEquals(BodyPart.THIGH_R, HitDistribution.partAtHeight(0.35f, Side.RIGHT, rng));
    }
}
