package com.fleshbone.sim;

/** Mutable state of one body part. Values are 0–100 unless stated otherwise; times are in seconds. */
public final class PartState {
    /** Skin health: governs bleeding cap and infection risk. A part with full skin cannot bleed. */
    public float skin = 100f;
    /** Muscle health: governs the part's function. */
    public float muscle = 100f;
    /** External bleeding intensity (100 = 1.8 L/min before caps). */
    public float bleed;
    /** Internal bleeding intensity, head and torso only (100 = 0.45 L/min). */
    public float internalBleed;
    /** Acute pain from recent trauma; decays over time. Condition pain is added on top (see PainSystem). */
    public float pain;
    /** Infection progress. */
    public float infection;
    /** Wound contamination: dirt and germs that can seed an infection. */
    public float contamination;
    /** Seconds of disinfection left: no new infection and no progression while positive. */
    public float disinfected;
    /** Remaining fracture healing: 1 = fresh break, 0 = no fracture. */
    public float fracture;
    /** Remaining dislocation healing: 1 = fresh, 0 = none. */
    public float dislocation;
    /** Embedded fragments (arrowheads, shrapnel, glass). Block clotting and regeneration until removed. */
    public int shrapnel;

    public BandageType bandage = BandageType.NONE;
    /** 0–1, set by how well the dressing was wrapped. */
    public float bandageQuality;
    /** 0–1, blood saturation of the dressing. */
    public float bandageSoak;
    public boolean sutured;
    public boolean splinted;
    /** 1 for a proper splint, less for an improvised one (sticks). */
    public float splintQuality = 1f;
    public boolean tourniquet;
    /** Seconds since the tourniquet was tightened. */
    public float tourniquetTime;
    /** Seconds of ice-pack cooling left. */
    public float chilled;
    public boolean amputated;

    public boolean isFractured() { return fracture > 0f; }

    public boolean isDislocated() { return dislocation > 0f; }

    public boolean isBandaged() { return bandage != BandageType.NONE; }

    /** Fractures and dislocations halve the muscle health the part can regenerate to. */
    public float maxMuscle() {
        return (isFractured() || isDislocated()) ? 50f : 100f;
    }

    /** Broken skin that is not stitched shut. */
    public boolean hasOpenWound() {
        return !amputated && skin < 80f && !sutured;
    }

    public void removeBandage() {
        bandage = BandageType.NONE;
        bandageQuality = 0f;
        bandageSoak = 0f;
    }

    public void reset() {
        skin = 100f;
        muscle = 100f;
        bleed = internalBleed = pain = 0f;
        infection = contamination = disinfected = 0f;
        fracture = dislocation = 0f;
        shrapnel = 0;
        removeBandage();
        sutured = splinted = tourniquet = amputated = false;
        splintQuality = 1f;
        tourniquetTime = chilled = 0f;
    }

    public void visit(StateVisitor v) {
        skin = v.f("skin", skin);
        muscle = v.f("muscle", muscle);
        bleed = v.f("bleed", bleed);
        internalBleed = v.f("internal_bleed", internalBleed);
        pain = v.f("pain", pain);
        infection = v.f("infection", infection);
        contamination = v.f("contamination", contamination);
        disinfected = v.f("disinfected", disinfected);
        fracture = v.f("fracture", fracture);
        dislocation = v.f("dislocation", dislocation);
        shrapnel = v.i("shrapnel", shrapnel);
        bandage = v.e("bandage", bandage, BandageType.class);
        bandageQuality = v.f("bandage_quality", bandageQuality);
        bandageSoak = v.f("bandage_soak", bandageSoak);
        sutured = v.b("sutured", sutured);
        splinted = v.b("splinted", splinted);
        splintQuality = v.f("splint_quality", splintQuality);
        tourniquet = v.b("tourniquet", tourniquet);
        tourniquetTime = v.f("tourniquet_time", tourniquetTime);
        chilled = v.f("chilled", chilled);
        amputated = v.b("amputated", amputated);
    }
}
