package com.fleshbone.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import com.fleshbone.sim.Skills.Stat;
import com.fleshbone.sim.system.CirculationSystem;
import com.fleshbone.sim.system.Encumbrance;
import com.fleshbone.sim.system.ImmunitySystem;
import com.fleshbone.sim.system.LastStand;
import com.fleshbone.sim.system.SkillSystem;

/** Stats and experience, carrying capacity, immunity and the last stand. */
class SkillsTest {
    @Test
    void levelsFollowCasualtiesUnknownCurve() {
        assertEquals(60f, Skills.xpToNext(5), 1e-4);
        assertEquals(100f, Skills.xpToNext(10), 1e-4);
        assertEquals(118f, Skills.xpToNext(11), 1e-3);
        Skills skills = new Skills();
        assertEquals(1, skills.addXp(Stat.STRENGTH, 110f));
        assertEquals(11, skills.strength());
        assertEquals(10f, skills.xp(Stat.STRENGTH), 1e-4);
    }

    @Test
    void pushUpsBuildStrengthAndCostStamina() {
        Body body = SimTestUtil.body();
        Environment env = SimTestUtil.env();
        env.exercise = Environment.Exercise.PUSHUPS;
        SimTestUtil.run(body, env, 60f);
        assertEquals(30f, body.skills.xp(Stat.STRENGTH), 0.5f, "0.5 XP a second");
        assertEquals(28f, body.needs.stamina, 2f, "1.2 %/s");
        assertTrue(body.vitals.heartRate > 100f, "a workout raises the heart rate: " + body.vitals.heartRate);
    }

    @Test
    void enduringPainToughens() {
        Body body = SimTestUtil.body();
        body.part(BodyPart.THIGH_L).fracture = 1f;
        SimTestUtil.run(body, SimTestUtil.env(), 60f);
        assertTrue(body.skills.xp(Stat.RESILIENCE) > 1f, "resilience XP: " + body.skills.xp(Stat.RESILIENCE));
    }

    @Test
    void strengthHitsHarder() {
        Skills skills = new Skills();
        skills.setLevel(Stat.STRENGTH, 15);
        assertEquals(1.167f, SkillSystem.attackMultiplier(skills), 1e-3);
    }

    @Test
    void capacityDependsOnStatsFoodAndWeight() {
        Body body = SimTestUtil.body();
        assertEquals(Encumbrance.BASE_CAPACITY, Encumbrance.capacity(body), 1e-4);
        body.needs.hunger = 110f;
        assertEquals(Encumbrance.BASE_CAPACITY + 3.4f, Encumbrance.capacity(body), 1e-4, "satiated");
        body.needs.hunger = 100f;
        body.skills.setLevel(Stat.STRENGTH, 14);
        body.skills.setLevel(Stat.RESILIENCE, 12);
        assertEquals(Encumbrance.BASE_CAPACITY + 2.5f, Encumbrance.capacity(body), 1e-4, "the weaker of strength and resilience");
        body.skills.reset();
        body.needs.weight = 100f;
        assertTrue(Encumbrance.capacity(body) < Encumbrance.BASE_CAPACITY - 10f, "a heavy body carries itself");
    }

    @Test
    void overloadSlowsAndTires() {
        Body light = SimTestUtil.body();
        Body heavy = SimTestUtil.body();
        Environment env = SimTestUtil.env();
        env.moveSpeed = 4f;
        Environment loaded = SimTestUtil.env();
        loaded.moveSpeed = 4f;
        loaded.load = 37.5f;
        light.needs.stamina = 50f;
        heavy.needs.stamina = 50f;
        SimTestUtil.run(light, env, 10f);
        SimTestUtil.run(heavy, loaded, 10f);
        assertEquals(0.5f, heavy.derived.overload, 1e-3);
        assertTrue(heavy.needs.stamina < light.needs.stamina, "slower recovery under load");
        Capabilities caps = Capabilities.compute(heavy, BodyPart.Side.RIGHT);
        Capabilities free = Capabilities.compute(light, BodyPart.Side.RIGHT);
        assertTrue(caps.move() < free.move() * 0.9f, "−12.5 % speed at half overload");
        assertTrue(caps.attackSpeed() < free.attackSpeed());
    }

    @Test
    void immunityMatchesTheReferenceScreenshot() {
        // UNI-HEALTH capture: hunger 90, thirst 84, energy 85, 37.8 °C, 5.001 L → 133 %
        Body body = SimTestUtil.body();
        body.needs.hunger = 90f;
        body.needs.thirst = 84f;
        body.needs.energy = 85f;
        body.vitals.bodyTemp = 37.8f;
        body.vitals.bloodVolume = 5.001f;
        assertEquals(133.6f, ImmunitySystem.compute(body), 0.5f);
        body.chem.antibiotic = 100f;
        assertEquals(200f, ImmunitySystem.compute(body), 1e-3, "antibiotics +70 %, capped at 200");
    }

    @Test
    void infectionTableMatches() {
        assertEquals(18f, ImmunitySystem.infectionRate(0f), 1e-4);
        assertEquals(7.2f, ImmunitySystem.infectionRate(100f), 1e-4);
        assertEquals(-0.288f, ImmunitySystem.infectionRate(196f), 1e-4);
        assertEquals(-5.76f, ImmunitySystem.infectionRate(200f), 1e-4);
    }

    @Test
    void lastStandOddsDependOnMood() {
        assertEquals(0f, LastStand.chance(-30f), 1e-6);
        assertEquals(0.5f, LastStand.chance(20f), 1e-6);
        assertEquals(1f, LastStand.chance(70f), 1e-6);
    }

    @Test
    void aHappyDyingBodyGetsALastStand() {
        Body body = SimTestUtil.body();
        CirculationSystem.stopHeart(body);
        body.vitals.brainHealth = 16f;
        body.vitals.spo2 = 10f;
        body.mind.moodBase = 40f;
        body.mind.mood = 90f;
        Environment env = SimTestUtil.env();
        Random rng = new Random(3);
        boolean stood = false;
        for (int tick = 0; tick < 20 * 10 && !stood; tick++) {
            body.tick(env, SimSettings.DEFAULT, rng);
            stood = body.drainEvents().stream().anyMatch(e -> e.type() == BodyEvent.Type.LAST_STAND);
        }
        assertTrue(stood, "pulls through once");
        assertFalse(body.vitals.cardiacArrest, "the heart restarts");
        assertTrue(body.vitals.lastStand > 50f);
        assertEquals(0f, body.derived.totalPain, 1e-6, "pain held off");
        assertTrue(body.vitals.lastStandUsed);
    }

    @Test
    void aMiserableDyingBodyDoesNot() {
        Body body = SimTestUtil.body();
        CirculationSystem.stopHeart(body);
        body.vitals.brainHealth = 16f;
        body.vitals.spo2 = 10f;
        body.mind.moodBase = -40f;
        body.mind.mood = -60f;
        SimTestUtil.run(body, SimTestUtil.env(), SimSettings.DEFAULT, 10f, null);
        assertTrue(body.vitals.lastStandUsed);
        assertTrue(body.vitals.cardiacArrest, "no miracle");
    }
}
