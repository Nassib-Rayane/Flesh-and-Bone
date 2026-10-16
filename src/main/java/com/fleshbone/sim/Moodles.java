package com.fleshbone.sim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.fleshbone.sim.Moodle.Type;
import com.fleshbone.sim.injury.InjuryApplier;
import com.fleshbone.sim.system.BleedingSystem;
import com.fleshbone.sim.system.HealingSystem;

/** Derives the active moodles from the body state (thresholds follow Casualties: Unknown's). */
public final class Moodles {
    private static final Comparator<Moodle> ORDER = Comparator
            .comparing((Moodle m) -> m.type().positive)
            .thenComparing(Comparator.comparingInt(Moodle::level).reversed())
            .thenComparing(m -> m.type().ordinal());

    private Moodles() {}

    /** @param foodLevel vanilla food level (0–20), which the body does not store */
    public static List<Moodle> compute(Body body, int foodLevel) {
        List<Moodle> out = new ArrayList<>();
        Vitals v = body.vitals;
        Derived d = body.derived;
        Needs n = body.needs;
        Chemistry c = body.chem;

        if (v.cardiacArrest) add(out, Type.CARDIAC_ARREST, 4);
        add(out, Type.BLEEDING, level(d.externalBleed, BleedingSystem.MINOR_BLEEDING_LPM, BleedingSystem.BLEEDING_LPM,
                BleedingSystem.HEAVY_BLEEDING_LPM, BleedingSystem.CATASTROPHIC_BLEEDING_LPM));
        add(out, Type.INTERNAL_BLEEDING, level(d.internalBleed, BleedingSystem.INTERNAL_BLEEDING_LPM, 0.12f, 0.25f, 0.38f));
        add(out, Type.BLOOD_LOSS, below(v.bloodVolume, 4.5f, 4.0f, 3.5f, 3.0f));
        add(out, Type.PAIN, level(d.totalPain, 10.0001f, 30.0001f, 55.0001f, 80.0001f));
        add(out, Type.SHOCK, v.shock >= 66f ? 4 : v.shock > 50f ? 3 : v.shock > 30f ? 2 : 0);
        add(out, Type.CONSCIOUSNESS, below(v.consciousness, 90f, 72f, 55f, 30f));
        if (!v.cardiacArrest) {
            add(out, Type.HYPOTENSION, below(v.systolic, 110f, 96f, 83f, 60f));
            add(out, Type.HYPERTENSION, level(v.systolic, 130.0001f, 145.0001f, 162.0001f, 180.0001f));
            int tachy = v.heartRate > 200f ? 3 : v.heartRate > 160f ? 2 : v.heartRate > 110f ? 1 : 0;
            add(out, Type.TACHYCARDIA, tachy);
            if (tachy == 0) add(out, Type.BRADYCARDIA, v.heartRate < 40f ? 2 : v.heartRate < 50f ? 1 : 0);
            add(out, Type.ARRHYTHMIA, v.fibrillation > 75f ? 3 : v.fibrillation > 50f ? 2 : v.fibrillation > 15f ? 1 : 0);
        }
        add(out, Type.HYPOXEMIA, below(v.spo2, 90f, 75f, 60f, 45f));
        add(out, Type.BREATHING, v.respRate < 2.5f ? 4 : v.respRate < 6f ? 2 : v.respRate < 10f ? 1 : 0);
        add(out, Type.HEMOTHORAX, v.hemothorax > 70f ? 3 : v.hemothorax > 40f ? 2 : 0);
        if (v.embolism) add(out, Type.EMBOLISM, 3);
        add(out, Type.BRAIN_DAMAGE, below(v.brainHealth, 95f, 80f, 60f, 30f));
        if (body.part(BodyPart.HEAD).muscle < InjuryApplier.CONCUSSION_MUSCLE) add(out, Type.CONCUSSION, 3);

        int fractures = 0;
        int unsplinted = 0;
        int dislocations = 0;
        int dislocationsUnsplinted = 0;
        int shrapnel = 0;
        float tourniquetTime = -1f;
        boolean amputated = false;
        float infection = 0f;
        for (BodyPart part : BodyPart.VALUES) {
            PartState s = body.part(part);
            if (s.isFractured()) {
                fractures++;
                if (!s.splinted) unsplinted++;
            }
            if (s.isDislocated()) {
                dislocations++;
                if (!s.splinted) dislocationsUnsplinted++;
            }
            shrapnel += s.shrapnel;
            if (s.tourniquet) tourniquetTime = Math.max(tourniquetTime, s.tourniquetTime);
            amputated |= s.amputated;
            infection = Math.max(infection, s.infection);
        }
        if (fractures > 0) add(out, Type.FRACTURE, unsplinted >= 2 ? 3 : unsplinted == 1 ? 2 : 1);
        if (dislocations > 0) add(out, Type.DISLOCATION, dislocationsUnsplinted > 0 ? 2 : 1);
        if (shrapnel > 0) add(out, Type.SHRAPNEL, shrapnel > 2 ? 2 : 1);
        if (tourniquetTime >= 0f) {
            add(out, Type.TOURNIQUET, tourniquetTime > HealingSystem.TOURNIQUET_SAFE_TIME ? 3
                    : tourniquetTime > HealingSystem.TOURNIQUET_WARNING_TIME ? 2 : 1);
        }
        if (amputated) add(out, Type.AMPUTATED, 2);
        add(out, Type.INFECTION, level(infection, 25.0001f, 40.0001f, 60.0001f, 80.0001f));
        add(out, Type.SEPSIS, v.sepsis > 80f ? 4 : v.sepsis > 50f ? 3 : v.sepsis > 10f ? 2 : 0);
        add(out, Type.HYPERTHERMIA, level(v.bodyTemp, 38.0001f, 39.0001f, 40.2501f, 41.5001f));
        add(out, Type.HYPOTHERMIA, below(v.bodyTemp, 35.5f, 34f, 32.5f, 28f));

        add(out, Type.THIRST, below(n.thirst, 75f, 55f, 35f, 20.0001f));
        add(out, Type.OVERHYDRATED, n.thirst > 175f ? 3 : n.thirst > 125f ? 1 : 0);
        add(out, Type.HUNGER, foodLevel <= 3 ? 4 : foodLevel < 7 ? 3 : foodLevel < 10 ? 2 : foodLevel < 15 ? 1 : 0);
        add(out, Type.TIRED, below(n.energy, 35f, 25f, 15f, 7f));
        add(out, Type.EXERTED, below(n.stamina, 70f, 50f, 35f, 15f));
        add(out, Type.NAUSEA, level(n.sickness, 10.0001f, 30.0001f, 50.0001f, 75.0001f));
        add(out, Type.DIRTY, n.dirtiness > 80f ? 2 : n.dirtiness > 50f ? 1 : 0);
        add(out, Type.WET, level(n.wetness, 5.0001f, 20.0001f, 45.0001f, 70.0001f));

        Mind m = body.mind;
        add(out, Type.MOOD_LOW, below(m.mood, -10f, -30f, -50f, -75f));
        add(out, Type.MOOD_HIGH, level(m.mood, 10.0001f, 30.0001f, 50.0001f, 75.0001f));
        add(out, Type.TRAUMA, m.trauma > 80f ? 3 : m.trauma > 45f ? 2 : m.trauma > 25f ? 1 : 0);

        if (c.opiate > 80f) add(out, Type.OVERDOSE, 4);
        else add(out, Type.OPIATED, c.opiate > 50f ? 3 : c.opiate > 20f ? 2 : c.opiate > 5f ? 1 : 0);
        float withdrawal = c.opiateDependence - c.opiate;
        add(out, Type.WITHDRAWAL, withdrawal > 34f ? 4 : withdrawal > 25f ? 3 : withdrawal > 15f ? 2 : withdrawal > 5f ? 1 : 0);
        float adrenaline = v.adrenaline + c.epinephrine;
        add(out, Type.ADRENALINE, adrenaline > 65f ? 2 : adrenaline > 20f ? 1 : 0);
        if (c.caffeine > 10f) add(out, Type.STIMULATED, 1);

        out.sort(ORDER);
        return out;
    }

    private static void add(List<Moodle> out, Type type, int level) {
        if (level > 0) out.add(new Moodle(type, Math.min(4, level)));
    }

    /** Level from rising thresholds (value strictly above each). */
    private static int level(float value, float t1, float t2, float t3, float t4) {
        if (value >= t4) return 4;
        if (value >= t3) return 3;
        if (value >= t2) return 2;
        return value >= t1 ? 1 : 0;
    }

    /** Level from falling thresholds (value strictly below each). */
    private static int below(float value, float t1, float t2, float t3, float t4) {
        if (value < t4) return 4;
        if (value < t3) return 3;
        if (value < t2) return 2;
        return value < t1 ? 1 : 0;
    }
}
