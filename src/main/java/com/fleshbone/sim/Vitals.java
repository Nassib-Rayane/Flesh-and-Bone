package com.fleshbone.sim;

/** Whole-body physiology: blood, heart, lungs, brain, immune system and core temperature. */
public final class Vitals {
    public static final float NORMAL_BLOOD_VOLUME = 5.0f;
    public static final float MAX_BLOOD_VOLUME = 7.5f;

    /** Litres of blood. */
    public float bloodVolume = NORMAL_BLOOD_VOLUME;
    public float heartRate = 70f;
    public float systolic = 120f;
    public float diastolic = 80f;
    /** 0–100: arrhythmia (>15), ventricular tachycardia (>50), ventricular fibrillation (>75), arrest at 100. */
    public float fibrillation;
    public boolean cardiacArrest;
    /** Seconds since the heart stopped. */
    public float arrestTime;
    /** Breaths per minute. */
    public float respRate = 16f;
    /** Blood oxygen saturation, percent. */
    public float spo2 = 98f;
    /** Blood around the lungs, 0–100+. */
    public float hemothorax;
    /** −100 (thin, bleeds more) to 100 (thick, clots, embolism risk). */
    public float viscosity;
    public boolean embolism;
    public float brainHealth = 100f;
    public float consciousness = 100f;
    /** Pain shock, 0–100. Unconscious at 66. */
    public float shock;
    /** Natural adrenaline from injuries, 0–100. Halves into pain relief. */
    public float adrenaline;
    public float sepsis;
    /** 100 = normal, 0–200. */
    public float immunity = 100f;
    /** Core temperature in °C. */
    public float bodyTemp = 37f;
    public BloodType bloodType = BloodType.O_POS;
    /** 0–1 circulation provided by someone doing CPR; decays quickly when compressions stop. */
    public float cprPerfusion;
    /** Set by a catastrophic head injury: death on the next check. */
    public boolean headDestroyed;
    /** Most likely cause if the body dies now; updated whenever a lethal process starts. */
    public DeathCause pendingCause = DeathCause.CARDIAC_ARREST;

    public void reset() {
        BloodType keep = bloodType;
        bloodVolume = NORMAL_BLOOD_VOLUME;
        heartRate = 70f;
        systolic = 120f;
        diastolic = 80f;
        fibrillation = 0f;
        cardiacArrest = false;
        arrestTime = 0f;
        respRate = 16f;
        spo2 = 98f;
        hemothorax = 0f;
        viscosity = 0f;
        embolism = false;
        brainHealth = 100f;
        consciousness = 100f;
        shock = 0f;
        adrenaline = 0f;
        sepsis = 0f;
        immunity = 100f;
        bodyTemp = 37f;
        bloodType = keep;
        cprPerfusion = 0f;
        headDestroyed = false;
        pendingCause = DeathCause.CARDIAC_ARREST;
    }

    public void visit(StateVisitor v) {
        bloodVolume = v.f("blood_volume", bloodVolume);
        heartRate = v.f("heart_rate", heartRate);
        systolic = v.f("systolic", systolic);
        diastolic = v.f("diastolic", diastolic);
        fibrillation = v.f("fibrillation", fibrillation);
        cardiacArrest = v.b("cardiac_arrest", cardiacArrest);
        arrestTime = v.f("arrest_time", arrestTime);
        respRate = v.f("resp_rate", respRate);
        spo2 = v.f("spo2", spo2);
        hemothorax = v.f("hemothorax", hemothorax);
        viscosity = v.f("viscosity", viscosity);
        embolism = v.b("embolism", embolism);
        brainHealth = v.f("brain_health", brainHealth);
        consciousness = v.f("consciousness", consciousness);
        shock = v.f("shock", shock);
        adrenaline = v.f("adrenaline", adrenaline);
        sepsis = v.f("sepsis", sepsis);
        immunity = v.f("immunity", immunity);
        bodyTemp = v.f("body_temp", bodyTemp);
        bloodType = v.e("blood_type", bloodType, BloodType.class);
        cprPerfusion = v.f("cpr_perfusion", cprPerfusion);
        headDestroyed = v.b("head_destroyed", headDestroyed);
        pendingCause = v.e("pending_cause", pendingCause, DeathCause.class);
    }
}
