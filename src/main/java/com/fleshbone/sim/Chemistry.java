package com.fleshbone.sim;

/** Drug concentrations (0–100). Each one decays exponentially with its own half-life. */
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

    /** Opioids (morphine): strong analgesia, respiratory depression. Overdose above 80. */
    public float opiate;
    /** Built up by repeated opioid use; raises the dose needed for the same effect. */
    public float opiateTolerance;
    /** Physical dependence: withdrawal when opiates drop while this is high. */
    public float opiateDependence;
    /** Mild painkillers. */
    public float analgesic;
    public float epinephrine;
    public float antiarrhythmic;
    public float procoagulant;
    public float thrombolytic;
    public float antibiotic;
    public float caffeine;
    public float naloxone;

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
    }

    static float decay(float value, float halfLife, float dt) {
        if (value <= 0.001f) return 0f;
        return value * (float) Math.exp(-0.6931472 * dt / halfLife);
    }

    public void reset() {
        opiate = opiateTolerance = opiateDependence = analgesic = 0f;
        epinephrine = antiarrhythmic = procoagulant = thrombolytic = 0f;
        antibiotic = caffeine = naloxone = 0f;
    }

    public void visit(StateVisitor v) {
        opiate = v.f("opiate", opiate);
        opiateTolerance = v.f("opiate_tolerance", opiateTolerance);
        opiateDependence = v.f("opiate_dependence", opiateDependence);
        analgesic = v.f("analgesic", analgesic);
        epinephrine = v.f("epinephrine", epinephrine);
        antiarrhythmic = v.f("antiarrhythmic", antiarrhythmic);
        procoagulant = v.f("procoagulant", procoagulant);
        thrombolytic = v.f("thrombolytic", thrombolytic);
        antibiotic = v.f("antibiotic", antibiotic);
        caffeine = v.f("caffeine", caffeine);
        naloxone = v.f("naloxone", naloxone);
    }
}
