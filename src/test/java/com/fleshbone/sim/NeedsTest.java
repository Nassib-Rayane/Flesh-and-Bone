package com.fleshbone.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import com.fleshbone.sim.system.ChemistrySystem;
import com.fleshbone.sim.system.HungerSystem;
import com.fleshbone.sim.system.SicknessSystem;
import com.fleshbone.sim.system.SleepSystem;
import com.fleshbone.sim.treatment.Treatments;

/** Hunger, weight, thirst, temperature, sleep, nausea, mood and drugs. */
class NeedsTest {
    @Test
    void hungerDropsAtCasualtiesUnknownPace() {
        Body body = SimTestUtil.body();
        SimTestUtil.run(body, SimTestUtil.env(), 10 * 60f);
        assertEquals(74f, body.needs.hunger, 1f, "2.6 points a minute");
    }

    @Test
    void mealsAddHungerAndWeight() {
        Body body = SimTestUtil.body();
        body.needs.hunger = 50f;
        Treatments.MealResult result = Treatments.eat(body, 30f, 30f * HungerSystem.KG_PER_HUNGER, 0f, 0f, 0f, 2f, new Random(1));
        assertEquals(Treatments.MealResult.FINE, result);
        assertEquals(80f, body.needs.hunger, 1e-4);
        assertEquals(70.2f, body.needs.weight, 1e-4);
        assertTrue(body.mind.moodBase > 0f, "a good meal lifts the mood");
    }

    @Test
    void overeatingSickens() {
        Body body = SimTestUtil.body();
        body.needs.hunger = 120f;
        Treatments.MealResult result = Treatments.eat(body, 30f, 0.2f, 0f, 0f, 0f, 0f, new Random(1));
        assertTrue(result == Treatments.MealResult.OVERATE || result == Treatments.MealResult.THREW_UP, result.toString());
        assertEquals(125f, body.needs.hunger, 1e-4);
        assertTrue(body.needs.sickness >= 12f, "25 points too many: " + body.needs.sickness);
    }

    @Test
    void fastingBurnsWeightAndStarvingWastesMuscles() {
        Body body = SimTestUtil.body();
        SimTestUtil.run(body, SimTestUtil.env(), 30 * 60f);
        assertTrue(body.needs.weight < 69.5f, "30 minutes without food: " + body.needs.weight);
        body.needs.hunger = 0f;
        body.needs.thirst = 100f;
        SimTestUtil.run(body, SimTestUtil.env(), 5 * 60f);
        assertTrue(body.part(BodyPart.THIGH_L).muscle < 95f, "starving eats the muscles: " + body.part(BodyPart.THIGH_L).muscle);
        assertEquals(DeathCause.STARVATION, DeathCheck.classify(body));
    }

    @Test
    void thirstEmptiesInAboutHalfAnHour() {
        Body body = SimTestUtil.body();
        float t = SimTestUtil.run(body, SimTestUtil.env(), 3600f, b -> b.needs.thirst <= 0f);
        assertTrue(t > 25 * 60f && t < 32 * 60f, "1/17 point a second at rest, took " + t / 60f + " min");
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
    void waterIntoxicationHurtsTheHeadAndTheHeart() {
        Body body = SimTestUtil.body();
        body.needs.thirst = 230f;
        SimTestUtil.run(body, SimTestUtil.env(), 20f);
        assertTrue(body.part(BodyPart.HEAD).pain > 20f, "headache: " + body.part(BodyPart.HEAD).pain);
        assertTrue(body.vitals.fibrillation > 20f, "1.66 %/s fibrillation: " + body.vitals.fibrillation);
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
        warm.heatTarget = 40f;
        warm.heatStrength = 0.5f;
        SimTestUtil.run(dressed, warm, 15 * 60f);
        assertTrue(dressed.vitals.bodyTemp > 36f, "leather and a campfire keep you warm: " + dressed.vitals.bodyTemp);
    }

    @Test
    void shiveringBurnsHunger() {
        Body cold = SimTestUtil.body();
        Environment env = SimTestUtil.env();
        env.ambientTemp = 5f;
        SimTestUtil.run(cold, env, 10 * 60f);
        Body mild = SimTestUtil.body();
        SimTestUtil.run(mild, SimTestUtil.env(), 10 * 60f);
        assertTrue(cold.needs.hunger < mild.needs.hunger - 3f, "shivering: " + cold.needs.hunger + " vs " + mild.needs.hunger);
        assertTrue(cold.vitals.bodyTemp > 35.5f, "and holds the temperature for a while: " + cold.vitals.bodyTemp);
    }

    @Test
    void heatMakesYouSweatAndThirsty() {
        Body hot = SimTestUtil.body();
        Environment env = SimTestUtil.env();
        env.ambientTemp = 42f;
        SimTestUtil.run(hot, env, 10 * 60f);
        Body mild = SimTestUtil.body();
        SimTestUtil.run(mild, SimTestUtil.env(), 10 * 60f);
        assertTrue(hot.needs.thirst < mild.needs.thirst - 5f, "sweating: " + hot.needs.thirst + " vs " + mild.needs.thirst);
        assertTrue(hot.vitals.bodyTemp < 38.5f, "but keeps the body cool: " + hot.vitals.bodyTemp);
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
    void sleepNeedsTirednessAndWakesUpRested() {
        Body body = SimTestUtil.body();
        assertEquals(SleepSystem.Refusal.NOT_TIRED, SleepSystem.refusal(body, SleepSystem.OKAY));
        body.needs.energy = 30f;
        assertNull(SleepSystem.refusal(body, SleepSystem.OKAY));
        body.part(BodyPart.SHIN_L).fracture = 1f;
        body.derived.compute(body);
        assertEquals(SleepSystem.Refusal.IN_PAIN, SleepSystem.refusal(body, SleepSystem.OKAY));
        body.part(BodyPart.SHIN_L).fracture = 0f;
        body.derived.compute(body);

        SleepSystem.fallAsleep(body, SleepSystem.OKAY);
        SimTestUtil.run(body, SimTestUtil.env(), 60f);
        assertTrue(body.isUnconscious(), "asleep");
        assertEquals(54f, body.needs.energy, 2f, "0.4 %/s on an okay spot");
        SimTestUtil.run(body, SimTestUtil.env(), 300f, b -> !b.needs.asleep);
        assertFalse(body.needs.asleep, "wakes up rested");
        assertTrue(body.needs.energy > 99f);
        assertEquals(0f, body.needs.groggy, 1e-6, "a decent night leaves no grogginess");
    }

    @Test
    void badSleepLeavesYouGroggyAndPainWakesYou() {
        Body body = SimTestUtil.body();
        body.needs.energy = 30f;
        SleepSystem.fallAsleep(body, SleepSystem.BAD);
        SimTestUtil.run(body, SimTestUtil.env(), 20f);
        body.part(BodyPart.FOREARM_L).pain = 60f;
        body.derived.compute(body);
        SimTestUtil.run(body, SimTestUtil.env(), 1f);
        assertFalse(body.needs.asleep, "pain wakes the body");
        assertTrue(body.needs.groggy > 100f, "after a bad sleep: " + body.needs.groggy);
    }

    @Test
    void sprintingDrainsStaminaAndRestRestoresIt() {
        Body body = SimTestUtil.body();
        Environment env = SimTestUtil.env();
        env.sprinting = true;
        env.moveSpeed = 5.6f;
        SimTestUtil.run(body, env, 40f);
        assertTrue(body.needs.stamina < 70f, "40 s of sprinting: " + body.needs.stamina);
        env.sprinting = false;
        env.moveSpeed = 0f;
        SimTestUtil.run(body, env, 30f);
        assertTrue(body.needs.stamina > 90f, "rested: " + body.needs.stamina);
    }

    @Test
    void severeNauseaEndsInVomiting() {
        Body body = SimTestUtil.body();
        body.needs.sickness = 95f;
        Random rng = new Random(2);
        Environment env = SimTestUtil.env();
        boolean vomited = false;
        for (int tick = 0; tick < 20 * 300 && !vomited; tick++) {
            body.tick(env, SimTestUtil.SETTINGS, rng);
            vomited = body.drainEvents().stream().anyMatch(e -> e.type() == BodyEvent.Type.VOMITED);
        }
        assertTrue(vomited, "the urge builds up to 50");
        float hunger = body.needs.hunger;
        SimTestUtil.run(body, env, 6f);
        assertTrue(body.needs.hunger < hunger - 15f, "the stomach empties: " + hunger + " -> " + body.needs.hunger);
    }

    @Test
    void vomitingFitLeavesTheBodyHelpless() {
        Body body = SimTestUtil.body();
        SicknessSystem.startVomiting(body);
        SimTestUtil.run(body, SimTestUtil.env(), 3f);
        assertTrue(body.vitals.consciousness <= SicknessSystem.FIT_CONSCIOUSNESS + 0.01f);
        assertTrue(Capabilities.compute(body, BodyPart.Side.RIGHT).move() < 0.1f);
    }

    @Test
    void strongPainLeavesTraumaAndRuinsMood() {
        Body body = SimTestUtil.body();
        body.part(BodyPart.SHIN_L).fracture = 1f;
        body.part(BodyPart.SHIN_L).pain = 60f;
        body.derived.compute(body);
        Environment env = SimTestUtil.env();
        for (int i = 0; i < 6; i++) {
            body.part(BodyPart.SHIN_L).pain = 60f;
            SimTestUtil.run(body, env, 30f);
        }
        assertTrue(body.mind.trauma > 5f, "trauma: " + body.mind.trauma);
        assertTrue(body.mind.mood < -10f, "mood: " + body.mind.mood);
    }

    @Test
    void lastingMoodFadesBackSlowly() {
        Body body = SimTestUtil.body();
        body.mind.moodBase = 20f;
        SimTestUtil.run(body, SimTestUtil.env(), 10 * 60f);
        assertEquals(15.5f, body.mind.moodBase, 0.1f, "0.45 points a minute");
    }

    @Test
    void misery_makesTheBodyRefuse() {
        assertEquals(0f, com.fleshbone.sim.system.MindSystem.refusalChance(0f), 1e-6);
        assertEquals(0.05f, com.fleshbone.sim.system.MindSystem.refusalChance(-10f), 1e-4);
        assertEquals(0.15f, com.fleshbone.sim.system.MindSystem.refusalChance(-30f), 1e-4);
        assertEquals(0.9f, com.fleshbone.sim.system.MindSystem.refusalChance(-100f), 1e-4);
    }

    @Test
    void opioidToleranceTurnsIntoWithdrawal() {
        Body body = SimTestUtil.body();
        Treatments.injectOpiate(body, 35f);
        assertEquals(35f, body.chem.opiateReception(), 1e-4, "full effect at first");
        SimTestUtil.run(body, SimTestUtil.env(), 120f);
        assertTrue(body.chem.opiateReception() < 25f, "the tolerance catches up: " + body.chem.opiateReception());
        for (int dose = 0; dose < 3; dose++) {
            Treatments.injectOpiate(body, 30f);
            SimTestUtil.run(body, SimTestUtil.env(), 300f);
        }
        SimTestUtil.run(body, SimTestUtil.env(), 30 * 60f);
        float withdrawal = ChemistrySystem.withdrawal(body.chem);
        assertTrue(withdrawal > 5f, "withdrawal after the opioids wore off: " + withdrawal);
        assertTrue(body.mind.mood < -5f, "withdrawal is miserable: " + body.mind.mood);
    }

    @Test
    void naloxoneThrowsATolerantBodyIntoWithdrawal() {
        Body body = SimTestUtil.body();
        Treatments.injectOpiate(body, 60f);
        SimTestUtil.run(body, SimTestUtil.env(), 600f);
        Treatments.injectNaloxone(body);
        SimTestUtil.run(body, SimTestUtil.env(), 5f);
        assertTrue(ChemistrySystem.withdrawal(body.chem) > 20f, "precipitated withdrawal: " + ChemistrySystem.withdrawal(body.chem));
    }
}
