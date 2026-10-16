package com.fleshbone.client.hud;

import java.util.Locale;

import com.fleshbone.sim.Vitals;
import com.fleshbone.sim.system.ConsciousnessSystem;

import net.minecraft.network.chat.Component;

/** What can be told about a body without instruments: words instead of numbers (no health chip, no monitor). */
public final class Words {
    private Words() {}

    public static Component word(String group, String word) {
        return Component.translatable("fleshbone.panel.word." + group + "." + word);
    }

    public static Component blood(float litres) {
        String w;
        if (litres >= 4.6f) w = "none";
        else if (litres >= 4.0f) w = "light";
        else if (litres >= 3.3f) w = "heavy";
        else w = "critical";
        return word("blood", w);
    }

    public static Component pulse(Vitals v) {
        String w;
        if (v.cardiacArrest || v.heartRate <= 0f) w = "absent";
        else if (v.fibrillation > 50f) w = "chaotic";
        else if (v.heartRate > 120f) w = v.systolic < 90f ? "weak_fast" : "fast";
        else if (v.heartRate < 50f) w = "slow";
        else w = "normal";
        return word("pulse", w);
    }

    public static Component breathing(Vitals v) {
        String w;
        if (v.respRate < 2.5f) w = "absent";
        else if (v.respRate < 12.5f) w = "shallow";
        else if (v.respRate > 32f) w = "fast";
        else w = "normal";
        return word("breathing", w);
    }

    public static Component temperature(float temp) {
        String w;
        if (temp < 32.5f) w = "hypothermia";
        else if (temp < 35.5f) w = "cold";
        else if (temp > 40f) w = "high_fever";
        else if (temp > 38f) w = "fever";
        else w = "normal";
        return word("temperature", w);
    }

    public static Component consciousness(float c) {
        String w;
        if (c < ConsciousnessSystem.UNCONSCIOUS) w = "unconscious";
        else if (c < ConsciousnessSystem.INCAPACITATED) w = "incapacitated";
        else if (c < ConsciousnessSystem.FAINTING) w = "fainting";
        else if (c < ConsciousnessSystem.VERY_CONFUSED) w = "very_confused";
        else if (c < ConsciousnessSystem.CONFUSED) w = "confused";
        else w = "alert";
        return word("consciousness", w);
    }

    public static Component mood(float mood) {
        String w;
        if (mood < -50f) w = "depressed";
        else if (mood < -10f) w = "low";
        else if (mood > 30f) w = "happy";
        else w = "neutral";
        return word("mood", w);
    }

    public static String format(float value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    public static String duration(float seconds) {
        int total = Math.max(0, Math.round(seconds));
        return String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60);
    }
}
