package com.fleshbone.sim;

import java.util.Locale;

/**
 * A status indicator shown on the HUD. Level 1 (mild) to 4 (critical).
 */
public record Moodle(Moodle.Type type, int level) {
    public enum Type {
        CARDIAC_ARREST("☠"),
        LAST_STAND("✪", true),
        BLEEDING("♦"),
        INTERNAL_BLEEDING("◆"),
        BLOOD_LOSS("▼"),
        PAIN("✶"),
        SHOCK("⚡"),
        CONSCIOUSNESS("◎"),
        COLLAPSED("⤓"),
        HYPOTENSION("↓"),
        HYPERTENSION("↑"),
        TACHYCARDIA("♥"),
        BRADYCARDIA("♡"),
        ARRHYTHMIA("∿"),
        HYPOXEMIA("○"),
        BREATHING("≈"),
        HEMOTHORAX("⊖"),
        EMBOLISM("✕"),
        BRAIN_DAMAGE("Ψ"),
        CONCUSSION("✦"),
        FRACTURE("╳"),
        DISLOCATION("↯"),
        SHRAPNEL("✧"),
        TOURNIQUET("⊗"),
        AMPUTATED("✂"),
        BLIND("◐"),
        DEAF("♮"),
        INFECTION("☣"),
        SEPSIS("✚"),
        ENVENOMED("☤"),
        HYPERTHERMIA("♨"),
        HYPOTHERMIA("❄"),
        THIRST("◊"),
        OVERHYDRATED("≋"),
        HUNGER("∅"),
        FULL("●"),
        UNDERWEIGHT("‹"),
        OVERWEIGHT("›"),
        OVERLOADED("▣"),
        TIRED("☾"),
        GROGGY("☁"),
        EXERTED("»"),
        NAUSEA("~"),
        VOMITING("≀"),
        MOOD_LOW("☹"),
        TRAUMA("‼"),
        DIRTY("▒"),
        WET("☂"),
        OVERDOSE("☢"),
        WITHDRAWAL("✾"),
        CAFFEINE_WITHDRAWAL("☕"),
        ANAPHYLAXIS("⚠"),
        FOOD_POISONING("☠"),
        OPIATED("✿", true),
        ADRENALINE("▲", true),
        STIMULATED("☕", true),
        SATIATED("◉", true),
        MOOD_HIGH("☺", true);

        public final String symbol;
        public final boolean positive;
        private final String key;

        Type(String symbol) {
            this(symbol, false);
        }

        Type(String symbol, boolean positive) {
            this.symbol = symbol;
            this.positive = positive;
            this.key = name().toLowerCase(Locale.ROOT);
        }

        /** Translation key stem: {@code fleshbone.moodle.<key>.<level>} for the name. */
        public String key() {
            return key;
        }
    }

    public String translationKey() {
        return "fleshbone.moodle." + type.key() + "." + level;
    }
}
