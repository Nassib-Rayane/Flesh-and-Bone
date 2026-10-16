package com.fleshbone.sim;

/**
 * Something noteworthy that happened to the body. Drained each tick by the game glue to show thoughts, play sounds
 * and grant advancements.
 *
 * @param part the part concerned, or {@code null} for whole-body events
 */
public record BodyEvent(Type type, BodyPart part) {
    public enum Type {
        FRACTURE,
        DISLOCATION,
        SHRAPNEL,
        HEAVY_BLEEDING,
        INTERNAL_BLEEDING,
        CONCUSSION,
        FAINTED,
        WOKE_UP,
        PAIN_SHOCK,
        CARDIAC_ARREST,
        HEART_RESTARTED,
        RESPIRATORY_ARREST,
        FRACTURE_HEALED,
        DISLOCATION_HEALED,
        LIMB_NUMB,
        INFECTION_NOTICED,
        SEPSIS,
        VOMITED,
        AMPUTATED,
        STROKE,
        TRANSFUSION_REACTION,
        PASSED_OUT_TIRED
    }
}
