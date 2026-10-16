package com.fleshbone.sim;

/**
 * Dressings wrapped around a wound.
 *
 * @param stopFactor    fraction of external bleeding stopped at full quality
 * @param soakPerLitre  how fast blood saturates the dressing (a soaked dressing loses most of its effect)
 * @param contamination infection pressure added per second while worn (rags are dirty)
 * @param disinfects    whether applying it disinfects the wound
 */
public enum BandageType {
    NONE(0f, 0f, 0f, false),
    RAG(0.55f, 9f, 0.04f, false),
    BANDAGE(0.85f, 5f, 0.01f, false),
    STERILE(0.9f, 5f, 0f, true);

    public final float stopFactor;
    public final float soakPerLitre;
    public final float contamination;
    public final boolean disinfects;

    BandageType(float stopFactor, float soakPerLitre, float contamination, boolean disinfects) {
        this.stopFactor = stopFactor;
        this.soakPerLitre = soakPerLitre;
        this.contamination = contamination;
        this.disinfects = disinfects;
    }
}
