package com.fleshbone.sim;

/** Drug and toxin concentrations (0–100). Each one decays exponentially with its own half-life. */
public final class Chemistry {
    public static final float OPIATE_HALF_LIFE = 480f;
    public static final float ANALGESIC_HALF_LIFE = 300f;
    public static final float EPINEPHRINE_HALF_LIFE = 90f;
    public static final float ANTIARRHYTHMIC_HALF_LIFE = 600f;
    public static final float PROCOAGULANT_HALF_LIFE = 240f;
    public static final float THROMBOLYTIC_HALF_LIFE = 120f;
    public static final float ANTIBIOTIC_HALF_LIFE = 900f;
    public static final float CAFFEINE_HALF_LIFE = 900f;
    public static final float NALOXONE_HALF_LIFE = 240f;
    public static final float VENOM_HALF_LIFE = 360f;
    public static final float ANTIVENOM_HALF_LIFE = 600f;

    /** Opioids (morphine) in the blood. What the body feels is the reception: this minus the tolerance. */
    public float opiate;
    /** Follows the opioid level up quickly and down slowly; above the level, the gap is withdrawal. */
    public float opiateTolerance;
    /** Mild painkillers. */
    public float analgesic;
    public float epinephrine;
    public float antiarrhythmic;
    public float procoagulant;
    public float thrombolytic;
    public float antibiotic;
    public float caffeine;
    public float naloxone;
    /** Venom from spiders and stings: thins the blood, starves it of oxygen, sickens. */
    public float venom;
    public float antivenom;

    /** Opioid reception: positive = relief and sedation, negative = withdrawal. */
    public float opiateReception() {
        return opiate - opiateTolerance;
    }

    public void decay(float dt) {
        opiate = decay(opiate, OPIATE_HALF_LIFE, dt);
        analgesic = decay(analgesic, ANALGESIC_HALF_LIFE, dt);
        epinephrine = decay(epinephrine, EPINEPHRINE_HALF_LIFE, dt);
        antiarrhythmic = decay(antiarrhythmic, ANTIARRHYTHMIC_HALF_LIFE, dt);
        procoagulant = decay(procoagulant, PROCOAGULANT_HALF_LIFE, dt);
        thrombolytic = decay(thrombolytic, THROMBOLYTIC_HALF_LIFE, dt);
        antibiotic = decay(antibiotic, ANTIBIOTIC_HALF_LIFE, dt);
        caffeine = decay(caffeine, CAFFEINE_HALF_LIFE, dt);
        naloxone = decay(naloxone, NALOXONE_HALF_LIFE, dt);
        venom = decay(venom, VENOM_HALF_LIFE, dt);
        antivenom = decay(antivenom, ANTIVENOM_HALF_LIFE, dt);
    }

    static float decay(float value, float halfLife, float dt) {
        if (value <= 0.001f) return 0f;
        return value * (float) Math.exp(-0.6931472 * dt / halfLife);
    }

    public void reset() {
        opiate = opiateTolerance = analgesic = 0f;
        epinephrine = antiarrhythmic = procoagulant = thrombolytic = 0f;
        antibiotic = caffeine = naloxone = venom = antivenom = 0f;
    }

    public void visit(StateVisitor v) {
        opiate = v.f("opiate", opiate);
        opiateTolerance = v.f("opiate_tolerance", opiateTolerance);
        analgesic = v.f("analgesic", analgesic);
        epinephrine = v.f("epinephrine", epinephrine);
        antiarrhythmic = v.f("antiarrhythmic", antiarrhythmic);
        procoagulant = v.f("procoagulant", procoagulant);
        thrombolytic = v.f("thrombolytic", thrombolytic);
        antibiotic = v.f("antibiotic", antibiotic);
        caffeine = v.f("caffeine", caffeine);
        naloxone = v.f("naloxone", naloxone);
        venom = v.f("venom", venom);
        antivenom = v.f("antivenom", antivenom);
    }
}
