package com.fleshbone.sim;

import java.util.Locale;

/**
 * Strength, resilience and intelligence, 10 being average. Experience comes from what the body goes through (fights,
 * pain, workouts, crafting, trading...). Levels follow Casualties: Unknown's curve: 60 XP per level up to 10, 100 XP
 * from 10 to 11, then 18 % more for each level.
 */
public final class Skills {
    public enum Stat {
        STRENGTH,
        RESILIENCE,
        INTELLIGENCE;

        public static final Stat[] VALUES = values();
        private final String key = name().toLowerCase(Locale.ROOT);

        public String key() { return key; }
    }

    public static final int START_LEVEL = 10;
    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 20;

    private final int[] levels = {START_LEVEL, START_LEVEL, START_LEVEL};
    private final float[] xp = new float[Stat.VALUES.length];

    public int level(Stat stat) {
        return levels[stat.ordinal()];
    }

    public float xp(Stat stat) {
        return xp[stat.ordinal()];
    }

    public int strength() { return levels[Stat.STRENGTH.ordinal()]; }

    public int resilience() { return levels[Stat.RESILIENCE.ordinal()]; }

    public int intelligence() { return levels[Stat.INTELLIGENCE.ordinal()]; }

    /** Experience needed to go from {@code level} to the next one. */
    public static float xpToNext(int level) {
        if (level < 10) return 60f;
        if (level == 10) return 100f;
        return 100f * (float) Math.pow(1.18, level - 10);
    }

    /** Progress toward the next level, 0–1. */
    public float progress(Stat stat) {
        int level = level(stat);
        return level >= MAX_LEVEL ? 1f : Math.min(1f, xp(stat) / xpToNext(level));
    }

    /** @return the number of levels gained */
    public int addXp(Stat stat, float amount) {
        int i = stat.ordinal();
        if (amount <= 0f || levels[i] >= MAX_LEVEL) return 0;
        xp[i] += amount;
        int gained = 0;
        while (levels[i] < MAX_LEVEL && xp[i] >= xpToNext(levels[i])) {
            xp[i] -= xpToNext(levels[i]);
            levels[i]++;
            gained++;
        }
        if (levels[i] >= MAX_LEVEL) xp[i] = 0f;
        return gained;
    }

    public void setLevel(Stat stat, int level) {
        levels[stat.ordinal()] = Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, level));
        xp[stat.ordinal()] = 0f;
    }

    public void copyFrom(Skills other) {
        System.arraycopy(other.levels, 0, levels, 0, levels.length);
        System.arraycopy(other.xp, 0, xp, 0, xp.length);
    }

    public void reset() {
        for (Stat stat : Stat.VALUES) setLevel(stat, START_LEVEL);
    }

    public void visit(StateVisitor v) {
        for (Stat stat : Stat.VALUES) {
            int i = stat.ordinal();
            levels[i] = v.i(stat.key(), levels[i]);
            xp[i] = v.f(stat.key() + "_xp", xp[i]);
        }
    }
}
