package com.fleshbone.sim;

import java.util.Locale;

/** Why a body died; each cause maps to a {@code fleshbone:<id>} damage type with its own death message. */
public enum DeathCause {
    HEMORRHAGE,
    CARDIAC_ARREST,
    ASPHYXIA,
    BRAIN_TRAUMA,
    SEPSIS,
    HYPOTHERMIA,
    HEATSTROKE,
    DEHYDRATION,
    STARVATION,
    OVERDOSE,
    WATER_INTOXICATION,
    ENVENOMATION;

    private final String id = name().toLowerCase(Locale.ROOT);

    public String id() { return id; }
}
