package com.fleshbone.sim;

/** Whole-body physiology: blood, heart, vessels, lungs, brain and core temperature. */
public final class Vitals {
    public static final float NORMAL_BLOOD_VOLUME = 5.0f;
    public static final float MAX_BLOOD_VOLUME = 7.5f;
    /** Breaths per minute of a healthy body at rest (Casualties: Unknown's value). */
    public static final float NORMAL_RESP_RATE = 25f;

    /** Litres of blood. */
    public float bloodVolume = NORMAL_BLOOD_VOLUME;
    public float heartRate = 70f;
    /** Baroreflex: bpm the heart adds (or removes) to hold 120 mmHg, −30 to +80, reached gradually. */
    public float baroreflex;
    /** Vessel tone, −1 (dilated) to +1 (constricted). Exertion, adrenaline and cold tighten; heat and opiates relax. */
    public float vesselTone;
    public float systolic = 120f;
    public float diastolic = 80f;
    /** 0–100: arrhythmia (>15), ventricular tachycardia (>50), ventricular fibrillation (>75), arrest at 100. */
    public float fibrillation;
    public boolean cardiacArrest;
    /** Seconds since the heart stopped. */
    public float arrestTime;
    /** Breaths per minute. */
    public float respRate = NORMAL_RESP_RATE;
    /** Blood oxygen saturation, percent. */
    public float spo2 = 100f;
    /** Blood around the lungs, 0–100+. */
    public float hemothorax;
    /** −100 (thin, bleeds more) to 100 (thick, clots, embolism risk). */
    public float viscosity;
    public boolean embolism;
    public float brainHealth = 100f;
    /** Seconds since the last roll for a brain-damage episode. */
    public float brainEventTimer;
    public float consciousness = 100f;
    /** Pain shock, 0–100. Unconscious at 66. */
    public float shock;
    /** Natural adrenaline from injuries, 0–100. Halves into pain relief. */
    public float adrenaline;
    public float sepsis;
    /** Core temperature in °C. */
    public float bodyTemp = 37f;
    /** How fast the core temperature moves, °C per minute (smoothed). */
    public float tempTrend;
    public BloodType bloodType = BloodType.O_POS;
    /** 0–1 circulation provided by someone doing CPR; decays quickly when compressions stop. */
    public float cprPerfusion;
    /** Set by a catastrophic head injury: death on the next check. */
    public boolean headDestroyed;
    /** Most likely cause if the body dies now; updated whenever a lethal process starts. */
    public DeathCause pendingCause = DeathCause.CARDIAC_ARREST;
    /** The one roll per life for a last stand has been made. */
    public boolean lastStandUsed;
    /** Seconds of last stand left: pain ignored, the body forced to hold on. */
    public float lastStand;

    public void reset() {
        BloodType keep = bloodType;
        bloodVolume = NORMAL_BLOOD_VOLUME;
        heartRate = 70f;
        baroreflex = 0f;
        vesselTone = 0f;
        systolic = 120f;
        diastolic = 80f;
        fibrillation = 0f;
        cardiacArrest = false;
        arrestTime = 0f;
        respRate = NORMAL_RESP_RATE;
        spo2 = 100f;
        hemothorax = 0f;
        viscosity = 0f;
        embolism = false;
        brainHealth = 100f;
        brainEventTimer = 0f;
        consciousness = 100f;
        shock = 0f;
        adrenaline = 0f;
        sepsis = 0f;
        bodyTemp = 37f;
        tempTrend = 0f;
        bloodType = keep;
        cprPerfusion = 0f;
        headDestroyed = false;
        pendingCause = DeathCause.CARDIAC_ARREST;
        lastStandUsed = false;
        lastStand = 0f;
    }

    public void visit(StateVisitor v) {
        bloodVolume = v.f("blood_volume", bloodVolume);
        heartRate = v.f("heart_rate", heartRate);
        baroreflex = v.f("baroreflex", baroreflex);
        vesselTone = v.f("vessel_tone", vesselTone);
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
        brainEventTimer = v.f("brain_event_timer", brainEventTimer);
        consciousness = v.f("consciousness", consciousness);
        shock = v.f("shock", shock);
        adrenaline = v.f("adrenaline", adrenaline);
        sepsis = v.f("sepsis", sepsis);
        bodyTemp = v.f("body_temp", bodyTemp);
        tempTrend = v.f("temp_trend", tempTrend);
        bloodType = v.e("blood_type", bloodType, BloodType.class);
        cprPerfusion = v.f("cpr_perfusion", cprPerfusion);
        headDestroyed = v.b("head_destroyed", headDestroyed);
        pendingCause = v.e("pending_cause", pendingCause, DeathCause.class);
        lastStandUsed = v.b("last_stand_used", lastStandUsed);
        lastStand = v.f("last_stand", lastStand);
    }
}
