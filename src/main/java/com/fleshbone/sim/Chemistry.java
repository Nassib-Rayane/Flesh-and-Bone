package com.fleshbone.sim;

/**
 * Drugs and toxins. A dose first goes into a depot (the muscle for an injection, the stomach for a pill or a drink)
 * that releases it into the blood over a minute or a few; the blood level then decays with the drug's half-life.
 * Opioids and caffeine also leave tolerance and dependence behind.
 */
public final class Chemistry {
    // Elimination half-lives, seconds
    public static final float OPIATE_HALF_LIFE = 1500f;
    public static final float ANALGESIC_HALF_LIFE = 900f;
    public static final float EPINEPHRINE_HALF_LIFE = 120f;
    public static final float ANTIARRHYTHMIC_HALF_LIFE = 900f;
    public static final float PROCOAGULANT_HALF_LIFE = 600f;
    public static final float THROMBOLYTIC_HALF_LIFE = 300f;
    public static final float ANTIBIOTIC_HALF_LIFE = 1200f;
    public static final float CAFFEINE_HALF_LIFE = 2400f;
    public static final float NALOXONE_HALF_LIFE = 300f;
    public static final float VENOM_HALF_LIFE = 360f;
    public static final float ANTIVENOM_HALF_LIFE = 600f;
    public static final float FOOD_POISONING_HALF_LIFE = 600f;
    // Absorption half-lives from the depot, seconds
    public static final float INJECTION_ABSORPTION = 30f;
    public static final float PILL_ABSORPTION = 150f;
    public static final float DRINK_ABSORPTION = 200f;
    /** Food poisoning takes a while to break out. */
    public static final float GUT_ABSORPTION = 240f;

    /** Opioids (morphine) in the blood; what the body feels depends on the tolerance. */
    public float opiate;
    public float opiateDepot;
    /** Slow, lasting tolerance: halves the felt effect at 50. */
    public float opiateTolerance;
    /** Physical dependence: withdrawal when the felt effect falls below it. */
    public float opiateDependence;
    /** Mild painkillers. */
    public float analgesic;
    public float analgesicDepot;
    public float epinephrine;
    public float epinephrineDepot;
    public float antiarrhythmic;
    public float procoagulant;
    public float thrombolytic;
    public float antibiotic;
    public float antibioticDepot;
    public float caffeine;
    public float caffeineDepot;
    public float caffeineDependence;
    /** Tiredness held back by caffeine, paid back when it wears off. */
    public float caffeineDebt;
    public float naloxone;
    /** Venom from spiders and stings: thins the blood, starves it of oxygen, sickens. */
    public float venom;
    public float antivenom;
    /** Food poisoning (raw meat, rotten food): breaks out slowly. */
    public float foodPoisoning;
    public float foodPoisoningDepot;
    /** Severe allergic reaction, 0–100: vessels open, the throat swells. Epinephrine stops it. */
    public float anaphylaxis;

    public void reset() {
        opiate = opiateDepot = opiateTolerance = opiateDependence = 0f;
        analgesic = analgesicDepot = epinephrine = epinephrineDepot = 0f;
        antiarrhythmic = procoagulant = thrombolytic = 0f;
        antibiotic = antibioticDepot = caffeine = caffeineDepot = caffeineDependence = caffeineDebt = 0f;
        naloxone = venom = antivenom = foodPoisoning = foodPoisoningDepot = anaphylaxis = 0f;
    }

    public void visit(StateVisitor v) {
        opiate = v.f("opiate", opiate);
        opiateDepot = v.f("opiate_depot", opiateDepot);
        opiateTolerance = v.f("opiate_tolerance", opiateTolerance);
        opiateDependence = v.f("opiate_dependence", opiateDependence);
        analgesic = v.f("analgesic", analgesic);
        analgesicDepot = v.f("analgesic_depot", analgesicDepot);
        epinephrine = v.f("epinephrine", epinephrine);
        epinephrineDepot = v.f("epinephrine_depot", epinephrineDepot);
        antiarrhythmic = v.f("antiarrhythmic", antiarrhythmic);
        procoagulant = v.f("procoagulant", procoagulant);
        thrombolytic = v.f("thrombolytic", thrombolytic);
        antibiotic = v.f("antibiotic", antibiotic);
        antibioticDepot = v.f("antibiotic_depot", antibioticDepot);
        caffeine = v.f("caffeine", caffeine);
        caffeineDepot = v.f("caffeine_depot", caffeineDepot);
        caffeineDependence = v.f("caffeine_dependence", caffeineDependence);
        caffeineDebt = v.f("caffeine_debt", caffeineDebt);
        naloxone = v.f("naloxone", naloxone);
        venom = v.f("venom", venom);
        antivenom = v.f("antivenom", antivenom);
        foodPoisoning = v.f("food_poisoning", foodPoisoning);
        foodPoisoningDepot = v.f("food_poisoning_depot", foodPoisoningDepot);
        anaphylaxis = v.f("anaphylaxis", anaphylaxis);
    }

    /** Exponential decay over {@code dt} seconds. */
    public static float decay(float value, float halfLife, float dt) {
        if (value <= 1e-5f) return 0f;
        return value * (float) Math.exp(-0.6931472 * dt / halfLife);
    }
}
