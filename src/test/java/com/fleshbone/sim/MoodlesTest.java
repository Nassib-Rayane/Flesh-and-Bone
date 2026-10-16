package com.fleshbone.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class MoodlesTest {
    @Test
    void healthyBodyHasNoMoodles() {
        assertTrue(Moodles.compute(SimTestUtil.body()).isEmpty());
    }

    @Test
    void heavyBleedingAndFractureShow() {
        Body body = SimTestUtil.body();
        body.part(BodyPart.THIGH_L).skin = 20f;
        body.part(BodyPart.THIGH_L).bleed = 20f; // 0.36 L/min
        body.part(BodyPart.SHIN_R).fracture = 1f;
        body.derived.compute(body);
        List<Moodle> moodles = Moodles.compute(body);
        assertTrue(moodles.contains(new Moodle(Moodle.Type.BLEEDING, 3)), moodles.toString());
        assertTrue(moodles.contains(new Moodle(Moodle.Type.FRACTURE, 2)), moodles.toString());
    }

    @Test
    void worstMoodlesComeFirstAndPositivesLast() {
        Body body = SimTestUtil.body();
        body.vitals.cardiacArrest = true;
        body.vitals.adrenaline = 80f;
        body.part(BodyPart.HAND_L).shrapnel = 1;
        body.derived.compute(body);
        List<Moodle> moodles = Moodles.compute(body);
        assertEquals(Moodle.Type.CARDIAC_ARREST, moodles.get(0).type());
        assertTrue(moodles.get(moodles.size() - 1).type().positive);
    }

    @Test
    void hungerWeightAndLoadShow() {
        Body body = SimTestUtil.body();
        body.needs.hunger = 0f;
        assertTrue(Moodles.compute(body).contains(new Moodle(Moodle.Type.HUNGER, 4)));
        body.needs.hunger = 35f;
        assertTrue(Moodles.compute(body).contains(new Moodle(Moodle.Type.HUNGER, 1)));
        body.needs.hunger = 110f;
        assertTrue(Moodles.compute(body).contains(new Moodle(Moodle.Type.SATIATED, 1)));
        body.needs.weight = 80f;
        body.needs.carried = 40f;
        body.derived.compute(body);
        List<Moodle> moodles = Moodles.compute(body);
        assertTrue(moodles.contains(new Moodle(Moodle.Type.OVERWEIGHT, 1)), moodles.toString());
        assertTrue(moodles.stream().anyMatch(m -> m.type() == Moodle.Type.OVERLOADED), moodles.toString());
    }
}
