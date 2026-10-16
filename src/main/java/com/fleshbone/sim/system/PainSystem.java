package com.fleshbone.sim.system;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyEvent;
import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.Chemistry;
import com.fleshbone.sim.PartState;
import com.fleshbone.sim.Vitals;

/**
 * Pain per part = acute pain (fades) + pain from lasting conditions. Total pain is the worst part plus a quarter of
 * the second worst, minus adrenaline and painkillers. Above 75 total pain, pain shock builds up.
 */
public final class PainSystem {
    public static final float DISCOMFORT = 10f;
    public static final float PAIN = 30f;
    public static final float SEVERE = 55f;
    public static final float AGONY = 80f;
    public static final float COLLAPSE = 100f;
    public static final float SHOCK_PAIN = 75f;
    public static final float SHOCK_UNCONSCIOUS = 66f;
    /** Above this total pain the character cannot fall asleep. */
    public static final float NO_SLEEP = 30f;

    private PainSystem() {}

    /** Pain from lasting conditions on a part: missing skin and muscle, broken bones, fragments, infection, tourniquet. */
    public static float conditionPain(Body body, BodyPart part) {
        PartState s = body.part(part);
        if (s.amputated) return 0f;
        float p = (100f - s.skin) * 0.15f + (100f - s.muscle) * 0.1f;
        if (s.isFractured()) p += s.splinted ? 15f : 35f;
        if (s.isDislocated()) p += s.splinted ? 15f : 30f;
        p += Math.min(30f, s.shrapnel * 8f);
        p += s.infection / 10f;
        if (s.infection > 60f) p += 4f;
        if (s.tourniquet) p += 40f;
        return p;
    }

    public static float partPain(Body body, BodyPart part) {
        PartState s = body.part(part);
        float p = s.pain + conditionPain(body, part);
        return s.chilled > 0f ? p * 0.6f : p;
    }

    /** Pain before adrenaline and drugs. */
    public static float rawTotal(Body body) {
        float max = 0f;
        float second = 0f;
        for (BodyPart part : BodyPart.VALUES) {
            float p = partPain(body, part);
            if (p > max) {
                second = max;
                max = p;
            } else if (p > second) {
                second = p;
            }
        }
        return max + second * 0.25f;
    }

    /** Pain removed by drugs: opioids are strong (tolerance weakens them), mild painkillers cap at 25. */
    public static float analgesia(Chemistry c) {
        float opioid = Math.max(0f, c.opiate - c.opiateTolerance * 0.5f) * 0.9f;
        return opioid + Math.min(25f, c.analgesic * 0.4f);
    }

    /** Extra pain from opioid withdrawal. */
    public static float withdrawalPain(Chemistry c) {
        return Math.max(0f, c.opiateDependence - c.opiate) * 0.4f;
    }

    public static float total(Body body, float raw) {
        Vitals v = body.vitals;
        return Math.max(0f, raw - (v.adrenaline + body.chem.epinephrine) * 0.5f - analgesia(body.chem) + withdrawalPain(body.chem));
    }

    /** Acute pain fades with a ~30 s time constant, faster under an ice pack. Adrenaline wears off (half-life 30 s). */
    public static void decay(Body body, float dt) {
        for (BodyPart part : BodyPart.VALUES) {
            PartState s = body.part(part);
            if (s.pain <= 0f) continue;
            float rate = (0.1f + 0.03f * s.pain) * (s.chilled > 0f ? 2.5f : 1f);
            s.pain = Math.max(0f, s.pain - rate * dt);
        }
        Vitals v = body.vitals;
        v.adrenaline = v.adrenaline <= 0.01f ? 0f : v.adrenaline * (float) Math.exp(-0.6931472 * dt / 30f);
    }

    public static void tickShock(Body body, float dt) {
        Vitals v = body.vitals;
        float pain = body.derived.totalPain;
        boolean wasShocked = v.shock >= SHOCK_UNCONSCIOUS;
        if (pain > SHOCK_PAIN) {
            v.shock = Math.min(100f, v.shock + 3.33f * (0.5f + (pain - SHOCK_PAIN) / 25f) * dt);
        } else {
            v.shock = Math.max(0f, v.shock - 1f * dt);
        }
        if (!wasShocked && v.shock >= SHOCK_UNCONSCIOUS) body.emit(BodyEvent.Type.PAIN_SHOCK, null);
    }
}
