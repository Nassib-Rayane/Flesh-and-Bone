package com.fleshbone.sim;

import java.util.Locale;

/**
 * Body weight classes. Casualties: Unknown's thresholds for its 50 kg character, scaled ×1.4 for a 70 kg adult.
 * Each class away from healthy shifts blood pressure by 12/8 mmHg.
 */
public enum WeightClass {
    EMACIATED(-4, 0f),
    MALNOURISHED(-3, 46.2f),
    SKINNY(-2, 50.96f),
    UNDERWEIGHT(-1, 55.72f),
    HEALTHY(0, 62.86f),
    CHUBBY(1, 77f),
    OVERWEIGHT(2, 84.28f),
    FAT(3, 89.04f),
    OBESE(4, 93.8f);

    public static final WeightClass[] VALUES = values();
    /** The heart starts to fibrillate outside these weights. */
    public static final float FIBRILLATION_LOW = 41.44f;
    public static final float FIBRILLATION_HIGH = 98.56f;
    /** Warning thresholds of the health panel. */
    public static final float CRITICAL_LOW = 43.8f;
    public static final float CRITICAL_HIGH = 96.2f;

    /** Steps from healthy: negative = too thin, positive = too heavy. */
    public final int level;
    /** Lowest weight of the class in kg. */
    public final float from;
    private final String key = name().toLowerCase(Locale.ROOT);

    WeightClass(int level, float from) {
        this.level = level;
        this.from = from;
    }

    public String key() { return key; }

    public static WeightClass of(float weight) {
        WeightClass result = EMACIATED;
        for (WeightClass c : VALUES) {
            if (weight >= c.from) result = c;
        }
        return result;
    }

    /** Systolic mmHg added by the weight (diastolic is two thirds of it). */
    public float pressureEffect() {
        return level * 12f;
    }

    public static boolean fibrillates(float weight) {
        return weight < FIBRILLATION_LOW || weight > FIBRILLATION_HIGH;
    }
}
