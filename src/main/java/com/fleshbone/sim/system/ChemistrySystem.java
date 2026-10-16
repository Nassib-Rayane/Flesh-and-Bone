package com.fleshbone.sim.system;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.Chemistry;
import com.fleshbone.sim.SimSettings;

/**
 * Absorption, elimination, tolerance and dependence.
 * <ul>
 *   <li>Doses leave their depot over a minute (injection) or a few (pills, drinks), then decay with each drug's
 *       half-life: a shot of morphine relieves for half an hour, fading out within the hour.</li>
 *   <li>Opioid tolerance builds slowly with exposure (half-life an hour) and divides the felt effect:
 *       effect = level / (1 + tolerance / 50).</li>
 *   <li>Dependence builds with every bit of effect felt (three or four shots within an hour are enough to be hooked)
 *       and fades very slowly (half-life 90 minutes). Whenever the felt effect drops below it, the gap is withdrawal.</li>
 *   <li>Caffeine builds a lighter dependence, and holds back tiredness that comes back when it wears off.</li>
 *   <li>Naloxone flushes opioids out (throwing a dependent body straight into withdrawal); antivenom neutralizes venom.</li>
 * </ul>
 */
public final class ChemistrySystem {
    /** Tolerance gained per second per point of opioid level. */
    public static final float TOLERANCE_GAIN = 0.0004f;
    public static final float TOLERANCE_HALF_LIFE = 3600f;
    /** Dependence gained per second per point of felt opioid effect. */
    public static final float DEPENDENCE_GAIN = 0.00045f;
    public static final float DEPENDENCE_HALF_LIFE = 5400f;
    public static final float CAFFEINE_DEPENDENCE_GAIN = 0.00008f;
    public static final float CAFFEINE_DEPENDENCE_HALF_LIFE = 14400f;
    /** Opioid removed per second while naloxone is active. */
    public static final float NALOXONE_CLEARANCE = 8f;
    public static final float ANTIVENOM_CLEARANCE = 1.5f;
    /** Felt effect above which breathing collapses. */
    public static final float OVERDOSE = 80f;
    /** Withdrawal above which nausea builds up. */
    public static final float NAUSEOUS_WITHDRAWAL = 16f;
    /** Withdrawal above which sleep becomes impossible. */
    public static final float SLEEPLESS_WITHDRAWAL = 20f;
    /** Withdrawal above which the heart can fibrillate. */
    public static final float SEVERE_WITHDRAWAL = 60f;

    private ChemistrySystem() {}

    public static void tick(Body body, float dt, boolean tolerance) {
        tick(body, dt, tolerance, 1f);
    }

    public static void tick(Body body, SimSettings settings, float dt) {
        tick(body, dt, settings.drugs(), settings.dependenceSpeed());
    }

    static void tick(Body body, float dt, boolean dependence, float dependenceSpeed) {
        Chemistry c = body.chem;
        // Absorption from the depots
        float absorbed = absorb(c.opiateDepot, Chemistry.INJECTION_ABSORPTION, dt);
        c.opiateDepot -= absorbed;
        c.opiate = Math.min(150f, c.opiate + absorbed);
        absorbed = absorb(c.analgesicDepot, Chemistry.PILL_ABSORPTION, dt);
        c.analgesicDepot -= absorbed;
        c.analgesic = Math.min(150f, c.analgesic + absorbed);
        absorbed = absorb(c.epinephrineDepot, Chemistry.INJECTION_ABSORPTION * 0.5f, dt);
        c.epinephrineDepot -= absorbed;
        c.epinephrine = Math.min(150f, c.epinephrine + absorbed);
        absorbed = absorb(c.antibioticDepot, Chemistry.PILL_ABSORPTION, dt);
        c.antibioticDepot -= absorbed;
        c.antibiotic = Math.min(150f, c.antibiotic + absorbed);
        absorbed = absorb(c.caffeineDepot, Chemistry.DRINK_ABSORPTION, dt);
        c.caffeineDepot -= absorbed;
        c.caffeine = Math.min(150f, c.caffeine + absorbed);
        absorbed = absorb(c.foodPoisoningDepot, Chemistry.GUT_ABSORPTION, dt);
        c.foodPoisoningDepot -= absorbed;
        c.foodPoisoning = Math.min(100f, c.foodPoisoning + absorbed);

        // Elimination
        c.opiate = Chemistry.decay(c.opiate, Chemistry.OPIATE_HALF_LIFE, dt);
        c.analgesic = Chemistry.decay(c.analgesic, Chemistry.ANALGESIC_HALF_LIFE, dt);
        c.epinephrine = Chemistry.decay(c.epinephrine, Chemistry.EPINEPHRINE_HALF_LIFE, dt);
        c.antiarrhythmic = Chemistry.decay(c.antiarrhythmic, Chemistry.ANTIARRHYTHMIC_HALF_LIFE, dt);
        c.procoagulant = Chemistry.decay(c.procoagulant, Chemistry.PROCOAGULANT_HALF_LIFE, dt);
        c.thrombolytic = Chemistry.decay(c.thrombolytic, Chemistry.THROMBOLYTIC_HALF_LIFE, dt);
        c.antibiotic = Chemistry.decay(c.antibiotic, Chemistry.ANTIBIOTIC_HALF_LIFE, dt);
        c.caffeine = Chemistry.decay(c.caffeine, Chemistry.CAFFEINE_HALF_LIFE, dt);
        c.naloxone = Chemistry.decay(c.naloxone, Chemistry.NALOXONE_HALF_LIFE, dt);
        c.venom = Chemistry.decay(c.venom, Chemistry.VENOM_HALF_LIFE, dt);
        c.antivenom = Chemistry.decay(c.antivenom, Chemistry.ANTIVENOM_HALF_LIFE, dt);
        c.foodPoisoning = Chemistry.decay(c.foodPoisoning, Chemistry.FOOD_POISONING_HALF_LIFE, dt);
        if (c.naloxone > 10f) {
            c.opiate = Math.max(0f, c.opiate - NALOXONE_CLEARANCE * dt);
            c.opiateDepot = Math.max(0f, c.opiateDepot - NALOXONE_CLEARANCE * dt);
        }
        if (c.antivenom > 5f) c.venom = Math.max(0f, c.venom - ANTIVENOM_CLEARANCE * dt);

        if (!dependence) {
            c.opiateTolerance = 0f;
            c.opiateDependence = 0f;
            c.caffeineDependence = 0f;
            return;
        }
        c.opiateTolerance += TOLERANCE_GAIN * dependenceSpeed * c.opiate * (1f - c.opiateTolerance / 100f) * dt;
        c.opiateTolerance = Chemistry.decay(c.opiateTolerance, TOLERANCE_HALF_LIFE, dt);
        float relief = relief(c);
        if (relief > 5f) c.opiateDependence += DEPENDENCE_GAIN * dependenceSpeed * relief * (1f - c.opiateDependence / 100f) * dt;
        c.opiateDependence = Chemistry.decay(c.opiateDependence, DEPENDENCE_HALF_LIFE, dt);
        if (c.caffeine > 10f) {
            c.caffeineDependence += CAFFEINE_DEPENDENCE_GAIN * dependenceSpeed * c.caffeine * (1f - c.caffeineDependence / 100f) * dt;
        }
        c.caffeineDependence = Chemistry.decay(c.caffeineDependence, CAFFEINE_DEPENDENCE_HALF_LIFE, dt);
    }

    private static float absorb(float depot, float halfLife, float dt) {
        if (depot <= 0.001f) return depot;
        return depot * (1f - (float) Math.exp(-0.6931472 * dt / halfLife));
    }

    /** Opioid sensitivity left by tolerance: 1 sober, 0.5 at tolerance 50. */
    public static float sensitivity(Chemistry c) {
        return 1f / (1f + c.opiateTolerance / 50f);
    }

    /** Opioid effect actually felt. */
    public static float relief(Chemistry c) {
        return c.opiate * sensitivity(c);
    }

    /** Opioid withdrawal: how far the felt effect falls short of what the body now depends on. */
    public static float withdrawal(Chemistry c) {
        return Math.max(0f, c.opiateDependence - relief(c));
    }

    /** Caffeine withdrawal (headache, fatigue). */
    public static float caffeineWithdrawal(Chemistry c) {
        return Math.max(0f, c.caffeineDependence - c.caffeine * 0.5f);
    }
}
