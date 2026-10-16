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
        /** A vomiting fit starts (the stomach empties when it ends). */
        VOMITED,
        VOMITED_BLOOD,
        AMPUTATED,
        STROKE,
        TRANSFUSION_REACTION,
        PASSED_OUT_TIRED,
        /** Ran out of stamina and collapsed. */
        COLLAPSED,
        STARVING,
        OVERATE,
        FELL_ASLEEP,
        /** Woken up by pain, cold, nausea... (as opposed to waking up rested). */
        WOKEN_UP,
        RESTED,
        LAST_STAND,
        EYE_LOST,
        EARS_RINGING,
        // Brain damage episodes (below 95 % brain health)
        BRAIN_HALLUCINATION,
        BRAIN_MIRRORED,
        BRAIN_DROP,
        BRAIN_SHAKE,
        BRAIN_FLASH,
        STRENGTH_UP,
        RESILIENCE_UP,
        INTELLIGENCE_UP
    }
}
