package com.fleshbone.sim;

import java.util.random.RandomGenerator;

/** ABO/Rh blood groups, drawn with real-world frequencies when a new body is created. */
public enum BloodType {
    O_NEG(0.07f), O_POS(0.38f), A_NEG(0.06f), A_POS(0.34f), B_NEG(0.02f), B_POS(0.09f), AB_NEG(0.01f), AB_POS(0.03f);

    private final float frequency;

    BloodType(float frequency) {
        this.frequency = frequency;
    }

    public boolean hasA() { return this == A_NEG || this == A_POS || this == AB_NEG || this == AB_POS; }

    public boolean hasB() { return this == B_NEG || this == B_POS || this == AB_NEG || this == AB_POS; }

    public boolean rhPositive() { return ordinal() % 2 == 1; }

    /** Whether this recipient can safely receive red cells from {@code donor}. */
    public boolean canReceive(BloodType donor) {
        if (donor.hasA() && !hasA()) return false;
        if (donor.hasB() && !hasB()) return false;
        return !donor.rhPositive() || rhPositive();
    }

    public String display() {
        String abo = switch (this) {
            case O_NEG, O_POS -> "O";
            case A_NEG, A_POS -> "A";
            case B_NEG, B_POS -> "B";
            case AB_NEG, AB_POS -> "AB";
        };
        return abo + (rhPositive() ? "+" : "-");
    }

    public static BloodType random(RandomGenerator rng) {
        float roll = rng.nextFloat();
        for (BloodType type : values()) {
            roll -= type.frequency;
            if (roll <= 0f) return type;
        }
        return O_POS;
    }
}
