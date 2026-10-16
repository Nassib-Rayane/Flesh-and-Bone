package com.fleshbone.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import com.fleshbone.sim.system.ChemistrySystem;
import com.fleshbone.sim.system.MetabolismSystem;
import com.fleshbone.sim.treatment.Treatments;

class NeedsTest {
    @Test
    void thirstEmptiesInAboutFortyMinutes() {
        Body body = SimTestUtil.body();
        float t = SimTestUtil.run(body, SimTestUtil.env(), 3600f, b -> b.needs.thirst <= 0f);
        assertTrue(t > 35 * 60f && t < 45 * 60f, "thirst empties in ~40 min at rest, took " + t / 60f + " min");
    }

    @Test
    void dehydrationEventuallyKills() {
        Body body = SimTestUtil.body();
        body.needs.thirst = 0f;
        SimTestUtil.run(body, SimTestUtil.env(), 2 * 3600f, b -> b.deathCause() != null);
        assertEquals(DeathCause.DEHYDRATION, body.deathCause());
    }

    @Test
    void drinkingRestoresThirstAndDirtyWaterCanSicken() {
        Body body = SimTestUtil.body();
        body.needs.thirst = 40f;
        Treatments.drink(body, 30f, 12f, 1f, 0f, 0f, new Random(1));
        assertEquals(70f, body.needs.thirst, 1e-4);
        assertEquals(12f, body.needs.sickness, 1e-4);
    }

    @Test
    void exhaustionMakesYouPassOutUntilRested() {
        Body body = SimTestUtil.body();
        body.needs.energy = 0.5f;
        SimTestUtil.run(body, SimTestUtil.env(), 60f, b -> b.needs.passedOut);
        assertTrue(body.needs.passedOut, "energy hit zero: passed out");
        SimTestUtil.run(body, SimTestUtil.env(), 5f);
        assertTrue(body.isUnconscious(), "collapsed");
        SimTestUtil.run(body, SimTestUtil.env(), 120f, b -> !b.needs.passedOut);
        assertFalse(body.needs.passedOut, "comes round once 20 energy is back");
        assertTrue(body.needs.energy >= 20f);
    }

    @Test
    void sprintingDrainsStaminaAndRestRestoresIt() {
        Body body = SimTestUtil.body();
        Environment env = SimTestUtil.env();
        env.sprinting = true;
        env.moveSpeed = 5.6f;
        SimTestUtil.run(body, env, 20f);
        assertTrue(body.needs.stamina < 70f, "20 s of sprinting: " + body.needs.stamina);
        env.sprinting = false;
        env.moveSpeed = 0f;
        SimTestUtil.run(body, env, 10f);
        assertTrue(body.needs.stamina > 90f, "rested");
    }

    @Test
    void severeNauseaEndsInVomiting() {
        Body body = SimTestUtil.body();
        body.needs.sickness = 95f;
        Random rng = new Random(2);
        Environment env = SimTestUtil.env();
        boolean vomited = false;
        for (int tick = 0; tick < 20 * 120 && !vomited; tick++) {
            body.tick(env, SimSettings.DEFAULT, rng);
            vomited = body.drainEvents().stream().anyMatch(e -> e.type() == BodyEvent.Type.VOMITED);
        }
        assertTrue(vomited);
        assertTrue(body.needs.sickness < 95f);
    }

    @Test
    void strongPainLeavesTraumaAndRuinsMood() {
        Body body = SimTestUtil.body();
        body.part(BodyPart.SHIN_L).fracture = 1f;
        body.part(BodyPart.SHIN_L).pain = 60f;
        body.derived.compute(body);
        Environment env = SimTestUtil.env();
        env.foodLevel = 20;
        for (int i = 0; i < 6; i++) {
            body.part(BodyPart.SHIN_L).pain = 60f;
            SimTestUtil.run(body, env, 30f);
        }
        assertTrue(body.mind.trauma > 5f, "trauma: " + body.mind.trauma);
        assertTrue(body.mind.mood < -10f, "mood: " + body.mind.mood);
    }

    @Test
    void opioidDependenceOutlastsTheDrug() {
        Body body = SimTestUtil.body();
        for (int dose = 0; dose < 4; dose++) {
            Treatments.injectOpiate(body, 30f);
            SimTestUtil.run(body, SimTestUtil.env(), 300f);
        }
        SimTestUtil.run(body, SimTestUtil.env(), 30 * 60f);
        assertTrue(ChemistrySystem.withdrawal(body.chem) > 5f, "withdrawal after the opioids wore off: " + ChemistrySystem.withdrawal(body.chem));
        body.derived.compute(body);
        assertTrue(body.derived.totalPain > 0f, "withdrawal hurts");
    }

    @Test
    void coldAirChillsAnUndressedBody() {
        Body body = SimTestUtil.body();
        Environment env = SimTestUtil.env();
        env.ambientTemp = -10f;
        SimTestUtil.run(body, env, 15 * 60f);
        assertTrue(body.vitals.bodyTemp < 35.5f, "15 minutes at -10 °C: " + body.vitals.bodyTemp);
        Body dressed = SimTestUtil.body();
        Environment warm = SimTestUtil.env();
        warm.ambientTemp = -10f;
        warm.insulation = 0.8f;
        warm.heatSource = 12f;
        SimTestUtil.run(dressed, warm, 15 * 60f);
        assertTrue(dressed.vitals.bodyTemp > 36f, "leather and a campfire keep you warm: " + dressed.vitals.bodyTemp);
        assertTrue(MetabolismSystem.THIRST_RATE > 0f);
    }
}
