package com.fleshbone.sim.system;

import java.util.random.RandomGenerator;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyEvent;
import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.Chemistry;
import com.fleshbone.sim.Environment;
import com.fleshbone.sim.Needs;
import com.fleshbone.sim.PartState;

/**
 * Side effects that do not belong to one organ system (the heart, lungs, pain, mood and sleep effects live in their
 * own systems):
 * <ul>
 *   <li>Opioid withdrawal: sweats, diarrhoea, cramps in the limbs.</li>
 *   <li>Caffeine: the tiredness it held back comes back when it wears off; withdrawal headaches.</li>
 *   <li>Too many painkillers: a stomach ulcer can bleed. Thrombolytics: spontaneous bleeding.</li>
 *   <li>Antibiotics upset the stomach. Food poisoning breaks out slowly: nausea, cramps, diarrhoea, fever.</li>
 *   <li>Anaphylaxis fades slowly on its own, quickly with epinephrine.</li>
 * </ul>
 */
public final class DrugEffects {
    /** Anaphylaxis left by an allergic reaction. */
    public static final float ANAPHYLAXIS = 60f;

    private DrugEffects() {}

    public static void tick(Body body, Environment env, float dt, RandomGenerator rng) {
        Chemistry c = body.chem;
        Needs n = body.needs;
        float withdrawal = ChemistrySystem.withdrawal(c);
        if (withdrawal > 5f) {
            n.wetness = Math.min(100f, n.wetness + 0.05f * withdrawal / 50f * dt);
            if (withdrawal > 30f) diarrhoea(n, 0.03f, dt);
            if (withdrawal > 25f && rng != null && rng.nextFloat() < 0.05f * dt) cramp(body, rng, 6f + withdrawal * 0.15f);
        }

        if (c.caffeine > 10f) {
            c.caffeineDebt = Math.min(30f, c.caffeineDebt + 0.0002f * c.caffeine * dt);
        } else if (c.caffeineDebt > 0f) {
            float paid = Math.min(c.caffeineDebt, 0.1f * dt);
            c.caffeineDebt -= paid;
            n.energy = Math.max(0f, n.energy - paid);
        }
        float caffeineWithdrawal = ChemistrySystem.caffeineWithdrawal(c);
        if (caffeineWithdrawal > 5f) floorPain(body.part(BodyPart.HEAD), Math.min(30f, caffeineWithdrawal * 0.8f), 0.5f, dt);

        if (rng != null && c.analgesic > 70f && rng.nextFloat() < 0.01f * (c.analgesic - 70f) / 30f * dt) {
            PartState abdomen = body.part(BodyPart.ABDOMEN);
            abdomen.internalBleed = Math.min(100f, abdomen.internalBleed + 8f);
            abdomen.pain = Math.min(150f, abdomen.pain + 10f);
            body.emit(BodyEvent.Type.INTERNAL_BLEEDING, BodyPart.ABDOMEN);
        }
        if (rng != null && c.thrombolytic > 50f && rng.nextFloat() < 0.002f * (c.thrombolytic - 50f) / 50f * dt) {
            if (rng.nextFloat() < 0.1f) {
                body.vitals.brainHealth = Math.max(0f, body.vitals.brainHealth - 5f);
                body.part(BodyPart.HEAD).internalBleed = Math.min(100f, body.part(BodyPart.HEAD).internalBleed + 6f);
                body.emit(BodyEvent.Type.STROKE);
            } else {
                BodyPart part = rng.nextBoolean() ? BodyPart.ABDOMEN : BodyPart.THORAX;
                body.part(part).internalBleed = Math.min(100f, body.part(part).internalBleed + 6f);
                body.emit(BodyEvent.Type.INTERNAL_BLEEDING, part);
            }
        }
        if (c.antibiotic > 30f) {
            n.sickness = Math.min(100f, n.sickness + 0.01f * dt);
            diarrhoea(n, 0.01f, dt);
        }
        if (c.foodPoisoning > 3f) {
            n.sickness = Math.min(100f, n.sickness + 0.006f * c.foodPoisoning * dt);
            floorPain(body.part(BodyPart.ABDOMEN), Math.min(40f, c.foodPoisoning * 0.4f), 1f, dt);
            if (c.foodPoisoning > 15f) diarrhoea(n, 0.0006f * c.foodPoisoning, dt);
        }
        if (c.anaphylaxis > 0f) {
            float fade = c.epinephrine > 20f ? 3f : 0.08f;
            c.anaphylaxis = Math.max(0f, c.anaphylaxis - fade * dt);
        }
    }

    /** A severe allergic reaction (antibiotics, antivenom). */
    public static void allergicReaction(Body body) {
        body.chem.anaphylaxis = Math.max(body.chem.anaphylaxis, ANAPHYLAXIS);
        body.emit(BodyEvent.Type.ALLERGIC_REACTION);
    }

    private static void diarrhoea(Needs n, float rate, float dt) {
        n.thirst = Math.max(Needs.MIN_THIRST, n.thirst - rate * dt);
        n.hunger = Math.max(Needs.MIN_HUNGER, n.hunger - rate * 0.6f * dt);
    }

    private static void cramp(Body body, RandomGenerator rng, float pain) {
        BodyPart part = BodyPart.VALUES[3 + rng.nextInt(BodyPart.COUNT - 3)];
        PartState s = body.part(part);
        if (!s.amputated) s.pain = Math.min(150f, s.pain + pain);
    }

    /** Raises acute pain toward {@code floor} at {@code rate} per second (a lasting ache). */
    private static void floorPain(PartState s, float floor, float rate, float dt) {
        if (s.pain < floor) s.pain = Math.min(floor, s.pain + rate * dt);
    }
}
