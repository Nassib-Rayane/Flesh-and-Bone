package com.fleshbone.sim.system;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyEvent;
import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.Chemistry;
import com.fleshbone.sim.PartState;
import com.fleshbone.sim.Skills;
import com.fleshbone.sim.Vitals;

/**
 * Pain per part = acute pain (fades) + pain from lasting conditions. Felt pain is the worst part scaled by resilience
 * (×1.25 at level 0, ×0.75 at level 20), minus half the adrenaline and the painkillers. Above 75, pain shock builds up
 * (unless resilience reached 20). Deep cold numbs the limbs.
 */
public final class PainSystem {
    public static final float DISCOMFORT = 10f;
    public static final float PAIN = 30f;
    public static final float SEVERE = 55f;
    public static final float AGONY = 80f;
    public static final float COLLAPSE = 100f;
    public static final float SHOCK_PAIN = 75f;
    public static final float SHOCK_UNCONSCIOUS = 66f;
    /** Above this felt pain the character cannot fall asleep (and wakes up). */
    public static final float NO_SLEEP = 31f;
    /** Resilience level that makes pain shock impossible. */
    public static final int SHOCK_IMMUNE_RESILIENCE = 20;

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
        if (s.chilled > 0f) p *= 0.6f;
        if (part.isLimb() && numb(body)) p *= 0.5f;
        return p;
    }

    /** Below 32 °C the limbs go numb. */
    static boolean numb(Body body) {
        return body.vitals.bodyTemp < TemperatureSystem.NUMB;
    }

    /** Worst part pain, before resilience, adrenaline and drugs. */
    public static float rawTotal(Body body) {
        float max = 0f;
        for (BodyPart part : BodyPart.VALUES) max = Math.max(max, partPain(body, part));
        return max;
    }

    /** Casualties: Unknown's resilience factor: 1.25 − 0.025 per level. */
    public static float resilienceFactor(Skills skills) {
        return SimMath.clamp(1.25f - 0.025f * skills.resilience(), 0.5f, 1.5f);
    }

    /** Pain removed by drugs: opioids by what the body receives, mild painkillers up to 25. */
    public static float analgesia(Chemistry c) {
        return ChemistrySystem.relief(c) * 0.9f + Math.min(25f, c.analgesic * 0.4f);
    }

    public static float total(Body body, float raw) {
        Vitals v = body.vitals;
        if (v.lastStand > 0f) return 0f;
        float felt = raw * resilienceFactor(body.skills) - (v.adrenaline + body.chem.epinephrine) * 0.5f - analgesia(body.chem);
        return Math.max(0f, felt);
    }

    /**
     * Acute pain fades with a ~30 s time constant, faster under an ice pack and much faster in numb limbs.
     * Adrenaline wears off (half-life 30 s).
     */
    public static void decay(Body body, float dt) {
        boolean numb = numb(body);
        for (BodyPart part : BodyPart.VALUES) {
            PartState s = body.part(part);
            if (s.pain <= 0f) continue;
            float rate = (0.1f + 0.03f * s.pain) * (s.chilled > 0f ? 2.5f : 1f);
            if (numb && part.isLimb()) rate += 5f;
            s.pain = Math.max(0f, s.pain - rate * dt);
        }
        Vitals v = body.vitals;
        v.adrenaline = v.adrenaline <= 0.01f ? 0f : v.adrenaline * (float) Math.exp(-0.6931472 * dt / 30f);
    }

    public static void tickShock(Body body, float dt) {
        Vitals v = body.vitals;
        float pain = body.derived.totalPain;
        boolean wasShocked = v.shock >= SHOCK_UNCONSCIOUS;
        boolean immune = body.skills.resilience() >= SHOCK_IMMUNE_RESILIENCE;
        if (pain > SHOCK_PAIN && !immune) {
            v.shock = Math.min(100f, v.shock + 3.33f * (0.5f + (pain - SHOCK_PAIN) / 25f) * dt);
        } else {
            v.shock = Math.max(0f, v.shock - 1f * dt);
        }
        if (!wasShocked && v.shock >= SHOCK_UNCONSCIOUS) body.emit(BodyEvent.Type.PAIN_SHOCK, null);
    }
}
