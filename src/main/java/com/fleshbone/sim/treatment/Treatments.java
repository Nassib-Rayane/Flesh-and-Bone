package com.fleshbone.sim.treatment;

import java.util.random.RandomGenerator;

import com.fleshbone.sim.BandageType;
import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyEvent;
import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.PartState;
import com.fleshbone.sim.BloodType;
import com.fleshbone.sim.Needs;
import com.fleshbone.sim.Vitals;
import com.fleshbone.sim.system.CirculationSystem;
import com.fleshbone.sim.system.HungerSystem;
import com.fleshbone.sim.system.InfectionSystem;
import com.fleshbone.sim.system.PainSystem;
import com.fleshbone.sim.system.SicknessSystem;

/** What each medical action does to the body. Pure functions; {@code quality} (0–1) comes from minigames. */
public final class Treatments {
    /** Above this total pain a dislocation cannot be put back (take painkillers first). */
    public static final float RESET_PAIN_LIMIT = 75f;
    public static final float ANTISEPTIC_SECONDS = 300f;
    public static final float ICE_PACK_SECONDS = 180f;

    private Treatments() {}

    public enum ResetResult { SUCCESS, FAILED, TOO_PAINFUL, BROKE_BONE }

    // ------------------------------------------------------------------------------------------------ wounds

    public static boolean canBandage(PartState s) {
        return !s.amputated && (s.skin < 100f || s.bleed > 0f) && (!s.isBandaged() || s.bandageSoak > 0.3f || s.bandageQuality < 0.95f);
    }

    /** Wraps a dressing; direct pressure also slows the bleeding right away. */
    public static void bandage(Body body, BodyPart part, BandageType type, float quality) {
        PartState s = body.part(part);
        s.bandage = type;
        s.bandageQuality = clamp01(quality);
        s.bandageSoak = 0f;
        s.bleed *= 1f - 0.3f * s.bandageQuality;
        if (type.disinfects) {
            s.disinfected = Math.max(s.disinfected, ANTISEPTIC_SECONDS);
            s.contamination *= 0.5f;
        }
        if (type == BandageType.RAG) s.contamination = Math.min(100f, s.contamination + 5f);
        body.derived.compute(body);
    }

    public static boolean canDisinfect(PartState s) {
        return !s.amputated && (s.hasOpenWound() || s.infection > 0f || s.contamination > 5f);
    }

    /** Antiseptic: kills germs in the wound, protects it for a while, and stings. */
    public static void disinfect(Body body, BodyPart part, float seconds) {
        PartState s = body.part(part);
        s.disinfected = Math.max(s.disinfected, seconds);
        s.contamination = 0f;
        if (s.infection > 0f && s.infection < 40f) s.infection = Math.max(0f, s.infection - 10f);
        s.pain = Math.min(150f, s.pain + 15f);
        body.derived.compute(body);
    }

    public static boolean canSuture(PartState s) {
        return !s.amputated && !s.sutured && s.skin < 90f;
    }

    /** Stitches close the wound: skin regrows faster and it barely bleeds. Stitching a dirty wound seals the germs in. */
    public static void suture(Body body, BodyPart part, float quality) {
        PartState s = body.part(part);
        float q = clamp01(quality);
        s.sutured = true;
        s.skin = Math.min(100f, s.skin + 25f * q);
        s.bleed *= 1f - 0.7f * q;
        s.pain = Math.min(150f, s.pain + 6f + 6f * (1f - q));
        body.derived.compute(body);
    }

    public static boolean canExtract(PartState s) {
        return !s.amputated && s.shrapnel > 0;
    }

    /**
     * Pulls one fragment out. Tweezers make it easier. A failed pull hurts, tears skin and reopens the wound;
     * fumbling inside a head wound can bruise the brain.
     *
     * @return whether the fragment came out
     */
    public static boolean extractShrapnel(Body body, BodyPart part, float quality, boolean tweezers, RandomGenerator rng) {
        PartState s = body.part(part);
        if (s.shrapnel <= 0) return false;
        float chance = tweezers ? 0.15f + 0.85f * clamp01(quality) : 0.05f + 0.65f * clamp01(quality);
        boolean success = rng.nextFloat() < chance;
        if (success) {
            s.shrapnel--;
            s.bleed = Math.min(100f, s.bleed + 3f);
            s.pain = Math.min(150f, s.pain + 4f);
        } else {
            s.pain = Math.min(150f, s.pain + 9f + rng.nextFloat() * 7f);
            s.skin = Math.max(0f, s.skin - 5f);
            s.bleed = Math.min(100f, s.bleed + 2f);
            if (part == BodyPart.HEAD && rng.nextFloat() < 0.8f) {
                body.vitals.brainHealth = Math.max(0f, body.vitals.brainHealth - rng.nextFloat());
            }
        }
        body.derived.compute(body);
        return success;
    }

    // ------------------------------------------------------------------------------------------------ bones

    public static boolean canSplint(PartState s) {
        return !s.amputated && (s.isFractured() || s.isDislocated()) && !s.splinted;
    }

    public static void splint(Body body, BodyPart part) {
        body.part(part).splinted = true;
        body.derived.compute(body);
    }

    public static boolean canReset(PartState s) {
        return !s.amputated && s.isDislocated();
    }

    /** Putting a joint back: each attempt hurts, a failure can break the bone. Impossible while in too much pain. */
    public static ResetResult resetDislocation(Body body, BodyPart part, float quality, RandomGenerator rng) {
        PartState s = body.part(part);
        if (!s.isDislocated()) return ResetResult.FAILED;
        if (body.derived.totalPain > RESET_PAIN_LIMIT) return ResetResult.TOO_PAINFUL;
        s.pain = Math.min(150f, s.pain + 15f + rng.nextFloat() * 9f);
        ResetResult result;
        if (rng.nextFloat() < clamp01(quality)) {
            s.dislocation = 0f;
            body.emit(BodyEvent.Type.DISLOCATION_HEALED, part);
            result = ResetResult.SUCCESS;
        } else if (rng.nextFloat() < 0.05f) {
            s.fracture = 1f;
            s.pain = Math.min(150f, s.pain + 40f);
            body.emit(BodyEvent.Type.FRACTURE, part);
            result = ResetResult.BROKE_BONE;
        } else {
            result = ResetResult.FAILED;
        }
        body.derived.compute(body);
        return result;
    }

    // ------------------------------------------------------------------------------------------------ devices

    public static boolean canTourniquet(BodyPart part, PartState s) {
        return part.canHoldTourniquet() && !s.amputated && !s.tourniquet;
    }

    public static void tourniquet(Body body, BodyPart part) {
        PartState s = body.part(part);
        s.tourniquet = true;
        s.tourniquetTime = 0f;
        body.derived.compute(body);
    }

    public static void removeTourniquet(Body body, BodyPart part) {
        PartState s = body.part(part);
        s.tourniquet = false;
        s.tourniquetTime = 0f;
        body.derived.compute(body);
    }

    public static boolean canChill(PartState s) {
        return !s.amputated;
    }

    /** Ice pack: numbs the part and speeds muscle recovery; cools the body a little. */
    public static void chill(Body body, BodyPart part, float seconds) {
        body.part(part).chilled = Math.max(body.part(part).chilled, seconds);
        body.vitals.bodyTemp -= 0.2f;
        body.derived.compute(body);
    }

    // ------------------------------------------------------------------------------------------------ drugs

    public static void takeAnalgesic(Body body, float dose) {
        body.chem.analgesic = Math.min(100f, body.chem.analgesic + dose);
        body.derived.compute(body);
    }

    /** Opioid dose: strong pain relief while the tolerance catches up; too much stops breathing. */
    public static void injectOpiate(Body body, float dose) {
        body.chem.opiate = Math.min(150f, body.chem.opiate + dose);
        body.derived.compute(body);
    }

    /** Neutralizes venom over the next minutes. */
    public static void injectAntivenom(Body body) {
        body.chem.antivenom = 100f;
    }

    public static void takeAntibiotics(Body body, float dose) {
        body.chem.antibiotic = Math.min(100f, body.chem.antibiotic + dose);
    }

    // ------------------------------------------------------------------------------------------------ heart and blood

    public enum ShockResult { RESTARTED, RHYTHM_RESTORED, NO_EFFECT, INDUCED_FIBRILLATION }

    /**
     * Defibrillator shock. Restores a fibrillating heart most of the time. A stopped heart restarts one time in five
     * (Casualties: Unknown), less as the arrest drags on; epinephrine doubles the odds, CPR helps. Shocking a healthy
     * heart can throw it into fibrillation.
     */
    public static ShockResult defibrillate(Body body, float quality, RandomGenerator rng) {
        Vitals v = body.vitals;
        PartState thorax = body.part(BodyPart.THORAX);
        thorax.pain = Math.min(150f, thorax.pain + 30f);
        thorax.skin = Math.max(0f, thorax.skin - 4f);
        float q = clamp01(quality);
        ShockResult result;
        if (v.cardiacArrest) {
            float chance = 0.2f * Math.max(0.1f, 1f - v.arrestTime / 300f) * (0.5f + 0.5f * q)
                    * (1f + Math.min(1f, body.chem.epinephrine / 60f)) * (v.cprPerfusion > 0.3f ? 1.25f : 1f);
            if (rng.nextFloat() < Math.min(0.6f, chance)) {
                CirculationSystem.restartHeart(body);
                result = ShockResult.RESTARTED;
            } else {
                result = ShockResult.NO_EFFECT;
            }
        } else if (v.fibrillation > 15f) {
            if (rng.nextFloat() < 0.6f + 0.3f * q) {
                v.fibrillation = 0f;
                result = ShockResult.RHYTHM_RESTORED;
            } else {
                result = ShockResult.NO_EFFECT;
            }
        } else {
            v.fibrillation = Math.min(100f, v.fibrillation + 40f + rng.nextFloat() * 40f);
            result = ShockResult.INDUCED_FIBRILLATION;
        }
        body.derived.compute(body);
        return result;
    }

    /** Chest compressions with rescue breaths: keeps blood moving; a lucky compression can restart the heart. */
    public static boolean cpr(Body body, float perfusion, RandomGenerator rng) {
        Vitals v = body.vitals;
        v.cprPerfusion = Math.max(v.cprPerfusion, clamp01(perfusion));
        if (v.cardiacArrest && perfusion > 0.6f && rng.nextFloat() < 0.015f * (1f + Math.min(1f, body.chem.epinephrine / 60f))) {
            CirculationSystem.restartHeart(body);
            return true;
        }
        return false;
    }

    public static void injectEpinephrine(Body body) {
        body.chem.epinephrine = Math.min(100f, body.chem.epinephrine + 60f);
        body.derived.compute(body);
    }

    public static void injectAntiarrhythmic(Body body) {
        body.chem.antiarrhythmic = Math.min(100f, body.chem.antiarrhythmic + 60f);
        body.vitals.fibrillation = Math.max(0f, body.vitals.fibrillation - 25f);
    }

    /** Reverses opioids at once, which throws a dependent body into withdrawal. */
    public static void injectNaloxone(Body body) {
        body.chem.naloxone = 100f;
        body.chem.opiate *= 0.1f;
        body.derived.compute(body);
    }

    public static void injectProcoagulant(Body body) {
        body.chem.procoagulant = Math.min(150f, body.chem.procoagulant + 50f);
    }

    public static void injectThrombolytic(Body body) {
        body.chem.thrombolytic = Math.min(100f, body.chem.thrombolytic + 60f);
        body.vitals.embolism = false;
        body.vitals.viscosity = Math.max(-100f, body.vitals.viscosity - 40f);
    }

    /** @return false if the donor blood was incompatible (transfusion reaction instead of volume) */
    public static boolean transfuse(Body body, BloodType donor, float litres) {
        Vitals v = body.vitals;
        if (donor != null && !v.bloodType.canReceive(donor)) {
            v.sepsis = Math.min(100f, v.sepsis + 25f);
            v.bodyTemp += 1.2f;
            PartState thorax = body.part(BodyPart.THORAX);
            thorax.pain = Math.min(150f, thorax.pain + 25f);
            body.needs.sickness = Math.min(100f, body.needs.sickness + 30f);
            body.emit(BodyEvent.Type.TRANSFUSION_REACTION, null);
            body.derived.compute(body);
            return false;
        }
        v.bloodVolume = Math.min(Vitals.MAX_BLOOD_VOLUME, v.bloodVolume + litres);
        body.derived.compute(body);
        return true;
    }

    /** Saline restores volume but thins the blood. */
    public static void infuseSaline(Body body, float litres) {
        body.vitals.bloodVolume = Math.min(Vitals.MAX_BLOOD_VOLUME, body.vitals.bloodVolume + litres);
        body.vitals.viscosity = Math.max(-100f, body.vitals.viscosity - 10f);
        body.derived.compute(body);
    }

    /** Takes blood out (filling a blood bag). */
    public static void drawBlood(Body body, float litres) {
        body.vitals.bloodVolume = Math.max(0f, body.vitals.bloodVolume - litres);
        body.derived.compute(body);
    }

    public static boolean canDrain(Body body) {
        return body.vitals.hemothorax > 5f;
    }

    /** Chest drain: lets the blood around the lungs out. Painful and a little dirty. */
    public static void chestDrain(Body body) {
        body.vitals.hemothorax = Math.max(0f, body.vitals.hemothorax - 35f);
        PartState thorax = body.part(BodyPart.THORAX);
        thorax.pain = Math.min(150f, thorax.pain + 25f);
        thorax.contamination = Math.min(100f, thorax.contamination + 8f);
        body.derived.compute(body);
    }

    // ------------------------------------------------------------------------------------------------ food and drink

    /**
     * A drink: water, with a chance of nausea for untreated water, a warmth effect for hot drinks and caffeine.
     *
     * @return whether it made the body sick
     */
    public static boolean drink(Body body, float thirst, float sickness, float sicknessChance, float warmth, float caffeine, RandomGenerator rng) {
        Needs n = body.needs;
        n.thirst = Math.min(Needs.MAX_THIRST, Math.max(Needs.MIN_THIRST, n.thirst + thirst));
        body.vitals.bodyTemp += warmth;
        body.chem.caffeine = Math.min(100f, body.chem.caffeine + caffeine);
        boolean sick = sickness != 0f && rng.nextFloat() < sicknessChance;
        if (sick) n.sickness = Math.max(0f, Math.min(100f, n.sickness + sickness));
        body.derived.compute(body);
        return sick;
    }

    public enum MealResult { FINE, SICK, OVERATE, THREW_UP }

    /**
     * A meal: hunger and body weight, thirst (dry food is negative), a lasting mood change, and side effects. Spoiled
     * food can sicken, so can eating with filthy hands (dirt above 75: 10 %). Eating past 125 hunger sickens in
     * proportion and throws up one time in five. A dislocated jaw makes every bite hurt.
     */
    public static MealResult eat(Body body, float hunger, float weight, float thirst, float sickness, float sicknessChance,
                                 float mood, RandomGenerator rng) {
        Needs n = body.needs;
        float excess = Math.max(0f, n.hunger + hunger - Needs.MAX_HUNGER);
        n.hunger = Math.min(Needs.MAX_HUNGER, n.hunger + hunger);
        n.weight += weight;
        n.thirst = Math.min(Needs.MAX_THIRST, Math.max(Needs.MIN_THIRST, n.thirst + thirst));
        body.mind.addMood(mood);
        MealResult result = MealResult.FINE;
        if (sickness != 0f && rng.nextFloat() < sicknessChance) {
            n.sickness = Math.max(0f, Math.min(100f, n.sickness + sickness));
            if (sickness > 0f) result = MealResult.SICK;
        }
        if (n.dirtiness > 75f && rng.nextFloat() < 0.1f) {
            n.sickness = Math.min(100f, n.sickness + 8f);
            result = MealResult.SICK;
        }
        if (excess > 0f) {
            n.sickness = Math.min(100f, n.sickness + excess * 0.5f);
            result = MealResult.OVERATE;
            body.emit(BodyEvent.Type.OVERATE);
            if (rng.nextFloat() < HungerSystem.OVEREATING_VOMIT_CHANCE) {
                SicknessSystem.startVomiting(body);
                result = MealResult.THREW_UP;
            }
        }
        PartState head = body.part(BodyPart.HEAD);
        if (head.isDislocated()) head.pain = Math.min(150f, head.pain + 25f);
        body.derived.compute(body);
        return result;
    }

    // ------------------------------------------------------------------------------------------------ amputation

    /** A limb can be cut off once its infection is severe, or when it is already destroyed. */
    public static boolean canAmputate(BodyPart part, PartState s) {
        return part.canAmputate() && !s.amputated && (s.infection > InfectionSystem.AMPUTATION_THRESHOLD || s.muscle <= 5f);
    }

    /**
     * Removes the part and everything below it. The stump (the part above) bleeds fully and loses tissue; a clean
     * cut ({@code quality}) limits the damage. Leaves lasting trauma.
     */
    public static void amputate(Body body, BodyPart part, float quality) {
        float q = clamp01(quality);
        for (BodyPart lost : part.distal()) {
            PartState s = body.part(lost);
            s.reset();
            s.amputated = true;
            s.skin = 0f;
            s.muscle = 0f;
        }
        BodyPart stump = part.parent();
        if (stump != null) {
            PartState s = body.part(stump);
            s.skin = Math.max(0f, s.skin - 30f * (1.5f - q));
            s.muscle = Math.max(0f, s.muscle - 50f);
            s.bleed = 100f;
            s.pain = Math.min(150f, s.pain + 60f);
            s.contamination = Math.min(100f, s.contamination + 10f);
            s.sutured = false;
        }
        body.mind.trauma = Math.min(100f, body.mind.trauma + 30f);
        body.mind.addMood(-20f);
        body.emit(BodyEvent.Type.AMPUTATED, part);
        body.derived.compute(body);
    }

    // ------------------------------------------------------------------------------------------------ targeting

    /** Part bleeding the most externally, or {@code null}. */
    public static BodyPart mostBleeding(Body body) {
        BodyPart best = null;
        float rate = 0.0005f;
        for (BodyPart part : BodyPart.VALUES) {
            float r = body.derived.partBleed[part.ordinal()];
            if (r > rate) {
                rate = r;
                best = part;
            }
        }
        return best;
    }

    /** Most bleeding part, else the most damaged skin that would take a dressing. */
    public static BodyPart bandageTarget(Body body) {
        BodyPart bleeding = mostBleeding(body);
        if (bleeding != null && canBandage(body.part(bleeding))) return bleeding;
        BodyPart best = null;
        float skin = 100f;
        for (BodyPart part : BodyPart.VALUES) {
            PartState s = body.part(part);
            if (canBandage(s) && !s.isBandaged() && s.skin < skin) {
                skin = s.skin;
                best = part;
            }
        }
        return best;
    }

    public static BodyPart disinfectTarget(Body body) {
        BodyPart best = null;
        float worst = -1f;
        for (BodyPart part : BodyPart.VALUES) {
            PartState s = body.part(part);
            if (!canDisinfect(s) || s.disinfected > 30f) continue;
            float score = s.infection * 2f + s.contamination + (100f - s.skin) * 0.2f;
            if (score > worst) {
                worst = score;
                best = part;
            }
        }
        return best;
    }

    public static BodyPart sutureTarget(Body body) {
        BodyPart best = null;
        float score = 0f;
        for (BodyPart part : BodyPart.VALUES) {
            PartState s = body.part(part);
            if (!canSuture(s)) continue;
            float value = body.derived.partBleed[part.ordinal()] * 100f + (100f - s.skin);
            if (value > score) {
                score = value;
                best = part;
            }
        }
        return best;
    }

    public static BodyPart shrapnelTarget(Body body) {
        BodyPart best = null;
        int most = 0;
        for (BodyPart part : BodyPart.VALUES) {
            PartState s = body.part(part);
            if (canExtract(s) && s.shrapnel > most) {
                most = s.shrapnel;
                best = part;
            }
        }
        return best;
    }

    /** Where to tighten a tourniquet for the worst limb bleed: on the bleeding limb part, or above a bleeding hand. */
    public static BodyPart tourniquetTarget(Body body) {
        BodyPart best = null;
        float rate = 0.0005f;
        for (BodyPart part : BodyPart.VALUES) {
            if (!part.isLimb()) continue;
            float r = body.derived.partBleed[part.ordinal()];
            if (r <= rate) continue;
            BodyPart site = part.canHoldTourniquet() ? part : part.parent();
            if (site != null && canTourniquet(site, body.part(site))) {
                rate = r;
                best = site;
            }
        }
        return best;
    }

    public static BodyPart splintTarget(Body body) {
        for (BodyPart part : BodyPart.VALUES) {
            if (part.isLimb() && canSplint(body.part(part))) return part;
        }
        for (BodyPart part : BodyPart.VALUES) {
            if (canSplint(body.part(part))) return part;
        }
        return null;
    }

    public static BodyPart mostPainful(Body body) {
        BodyPart best = null;
        float pain = PainSystem.DISCOMFORT * 0.5f;
        for (BodyPart part : BodyPart.VALUES) {
            float p = body.derived.partPain[part.ordinal()];
            if (p > pain && canChill(body.part(part))) {
                pain = p;
                best = part;
            }
        }
        return best;
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : Math.min(1f, value);
    }
}
