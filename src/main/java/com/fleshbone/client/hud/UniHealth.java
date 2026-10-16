package com.fleshbone.client.hud;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.Derived;
import com.fleshbone.sim.Needs;
import com.fleshbone.sim.PartState;
import com.fleshbone.sim.Vitals;
import com.fleshbone.sim.WeightClass;
import com.fleshbone.sim.system.InfectionSystem;
import com.fleshbone.sim.system.ThirstSystem;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The UNI-HEALTH readout, after Casualties: Unknown's health chip: every number the body runs on, with C:U's warning
 * thresholds (yellow when worrying, flashing red when critical), the ECG, and the details of one limb. Shared by the
 * HUD and the health screen. Without a chip (hardcore servers) and without a vitals monitor, what only instruments
 * could measure is replaced by what can be felt or seen.
 */
public final class UniHealth {
    public static final int WIDTH = 164;
    private static final int LINE = 10;
    private static final int PAD = 4;
    private static final int COL2 = 84;
    private static final int BG = 0xD80C1216;
    private static final int BORDER = 0xFF2E4656;
    private static final int TITLE = 0xFF8FD4FF;
    private static final int LABEL = 0xFF9EAAB2;
    private static final int VALUE = 0xFFF2F2F2;
    private static final int WARN = 0xFFFFC23A;
    private static final int BAD = 0xFFFF4636;
    private static final int BAD_DIM = 0xFF7A1A12;
    private static final int TRACK = 0xFF1E262C;
    private static final int ECG_H = 16;

    public enum Detail { FULL, COMPACT }

    private enum Level { OK, WARN, CRITICAL }

    private UniHealth() {}

    public static int height(Detail detail, boolean limb) {
        int rows = detail == Detail.FULL ? 10 : 2;
        return PAD + LINE + rows * LINE + ECG_H + 4 + (limb ? 4 * LINE + 4 : 0) + PAD;
    }

    /**
     * @param exact numbers (chip or monitor) rather than words
     * @param limb  part to detail at the bottom, or {@code null}
     * @return the height drawn
     */
    public static int draw(GuiGraphicsExtractor g, Font font, Body body, int x, int y, Detail detail, boolean exact, BodyPart limb, float time) {
        Vitals v = body.vitals;
        Needs n = body.needs;
        Derived d = body.derived;
        int h = height(detail, limb != null);
        g.fill(x, y, x + WIDTH, y + h, BG);
        g.outline(x, y, WIDTH, h, BORDER);
        int cy = y + PAD;
        g.text(font, Component.translatable(exact ? "fleshbone.uni.title" : "fleshbone.uni.title_unchipped"), x + PAD, cy, TITLE, false);
        right(g, font, Component.literal(v.bloodType.display()), x + WIDTH - PAD, cy, LABEL);
        cy += LINE;

        if (detail == Detail.FULL) {
            Level brain = level(v.brainHealth < 50f, v.brainHealth < 95f);
            label(g, font, "brain", x + PAD, cy);
            if (exact) {
                bar(g, x + 44, cy + 2, WIDTH - 44 - 32 - PAD, v.brainHealth / 100f, 0xFFD070D0);
                right(g, font, pct(v.brainHealth), x + WIDTH - PAD, cy, color(brain, time));
            } else {
                right(g, font, Component.literal("?"), x + WIDTH - PAD, cy, LABEL);
            }
            cy += LINE;

            pair(g, font, x, cy, "consciousness", exact ? pct(v.consciousness) : Words.consciousness(v.consciousness),
                    level(v.consciousness < 40f, v.consciousness < 90f),
                    "pain", pct(d.totalPain), level(d.totalPain > 75f, d.totalPain > 30f), time);
            cy += LINE;
            pair(g, font, x, cy, "blood", exact ? lit(Words.format(v.bloodVolume, 3) + " L") : Words.blood(v.bloodVolume),
                    level(v.bloodVolume < 3f, v.bloodVolume < 4.5f),
                    "immunity", exact ? pct(d.immunity) : lit("?"), level(d.immunity < 50f, d.immunity < 80f), time);
            cy += LINE;
            barPair(g, font, x, cy, "hunger", n.hunger / Needs.MAX_HUNGER, 0xFFE09040, lit(Math.round(n.hunger) + ""),
                    level(n.hunger < 10f, n.hunger < 40f || n.hunger > 120f),
                    "sickness", pct(n.sickness), level(n.sickness > 70f, n.sickness > 30f), time);
            cy += LINE;
            barPair(g, font, x, cy, "thirst", Math.min(1f, n.thirst / 150f), 0xFF40A0FF, lit(Math.round(n.thirst) + ""),
                    level(n.thirst < 10f || n.thirst > ThirstSystem.WATER_INTOXICATION, n.thirst < ThirstSystem.DEHYDRATED || n.thirst > ThirstSystem.OVERHYDRATED),
                    "temperature", exact ? lit(Words.format(v.bodyTemp, 1) + "°C") : Words.temperature(v.bodyTemp),
                    level(v.bodyTemp < 30f || v.bodyTemp > 41f, v.bodyTemp < 35.5f || v.bodyTemp > 38.5f), time);
            cy += LINE;
            pair(g, font, x, cy, "stamina", pct(n.stamina), level(n.stamina < 10f, n.stamina < 35f),
                    "mood", exact ? lit(Words.format(body.mind.mood, 1)) : Words.mood(body.mind.mood),
                    level(body.mind.mood < -75f, body.mind.mood < -30f), time);
            cy += LINE;
            WeightClass weightClass = d.weightClass;
            pair(g, font, x, cy, "weight", exact ? lit(Words.format(n.weight, 1) + " kg") : Component.translatable("fleshbone.weight." + weightClass.key()),
                    level(n.weight < WeightClass.CRITICAL_LOW || n.weight > WeightClass.CRITICAL_HIGH, weightClass != WeightClass.HEALTHY),
                    "energy", pct(n.energy), level(n.energy < 5f, n.energy < 25f), time);
            cy += LINE;
            pair(g, font, x, cy, "breathing", exact ? lit(Math.round(v.respRate) + "/min") : Words.breathing(v),
                    level(v.respRate < 2.5f, v.respRate < 12.5f || v.respRate > 35f),
                    "stand", lastStandText(v), v.lastStand > 0f ? Level.WARN : Level.OK, time);
            cy += LINE;
            label(g, font, "load", x + PAD, cy);
            bar(g, x + 44, cy + 2, WIDTH - 44 - 62 - PAD, Math.min(1f, n.carried / Math.max(1f, d.capacity) / 2f), d.overload > 0f ? 0xFFE07030 : 0xFF70B070);
            right(g, font, lit(Words.format(n.carried, 1) + "/" + Words.format(d.capacity, 0) + " kg"), x + WIDTH - PAD, cy,
                    color(level(d.overload >= 1f, d.overload > 0f), time));
            cy += LINE;
        }

        // Heart, pressure, oxygen
        Level hr = level(v.cardiacArrest || v.heartRate > 200f, v.heartRate > 120f || v.heartRate < 50f);
        Level bp = level(!v.cardiacArrest && (v.systolic < 88f || v.systolic > 160f), v.systolic < 100f || v.systolic > 140f);
        Level o2 = level(v.spo2 < 70f, v.spo2 < 92f);
        if (exact) {
            String rate = v.cardiacArrest ? "0" : String.valueOf(Math.round(v.heartRate));
            g.text(font, "♥ " + rate, x + PAD, cy, color(hr, time), false);
            g.centeredText(font, Math.round(v.systolic) + "/" + Math.round(v.diastolic), x + WIDTH / 2 + 4, cy, color(bp, time));
            right(g, font, lit("O₂ " + Math.round(v.spo2) + "%"), x + WIDTH - PAD, cy, color(o2, time));
        } else {
            g.text(font, Component.literal("♥ ").append(Words.pulse(v)), x + PAD, cy, color(hr, time), false);
        }
        cy += LINE;
        if (detail == Detail.COMPACT) {
            pair(g, font, x, cy, "blood", exact ? lit(Words.format(v.bloodVolume, 2) + " L") : Words.blood(v.bloodVolume),
                    level(v.bloodVolume < 3f, v.bloodVolume < 4.5f),
                    "temperature", exact ? lit(Words.format(v.bodyTemp, 1) + "°C") : Words.temperature(v.bodyTemp),
                    level(v.bodyTemp < 30f || v.bodyTemp > 41f, v.bodyTemp < 35.5f || v.bodyTemp > 38.5f), time);
            cy += LINE;
        }
        ecg(g, body, x + PAD, cy, WIDTH - 2 * PAD, exact, time);
        cy += ECG_H + 4;

        if (limb != null) limb(g, font, body, limb, x, cy, exact, time);
        return h;
    }

    /** Worst part right now (for the HUD): bleeding, pain, infection and damage. */
    public static BodyPart worstPart(Body body) {
        BodyPart worst = BodyPart.THORAX;
        float score = -1f;
        for (BodyPart part : BodyPart.VALUES) {
            PartState s = body.part(part);
            int i = part.ordinal();
            float value = (body.derived.partBleed[i] + body.derived.partInternal[i]) * 400f + body.derived.partPain[i]
                    + s.infection + (100f - s.muscle) * 0.5f + (100f - s.skin) * 0.3f + (s.amputated ? -1000f : 0f);
            if (value > score) {
                score = value;
                worst = part;
            }
        }
        return worst;
    }

    private static void limb(GuiGraphicsExtractor g, Font font, Body body, BodyPart part, int x, int y, boolean exact, float time) {
        PartState s = body.part(part);
        int i = part.ordinal();
        g.fill(x + PAD, y, x + WIDTH - PAD, y + 1, BORDER);
        y += 3;
        g.text(font, Component.translatable("fleshbone.part." + part.serializedName()), x + PAD, y, TITLE, false);
        right(g, font, lit(statusGlyphs(s)), x + WIDTH - PAD, y, WARN);
        y += LINE;
        if (s.amputated) {
            g.text(font, Component.translatable("fleshbone.panel.amputated"), x + PAD, y, BAD, false);
            return;
        }
        Level skin = level(s.skin < 10f, s.skin < 50f);
        Level muscle = level(s.muscle < 10f, s.muscle < 50f);
        barPair(g, font, x, y, "skin", s.skin / 100f, BodyDoll.tissueColor(s.skin), pct(s.skin), skin,
                "muscle", pct(s.muscle), muscle, time);
        y += LINE;
        float bleed = body.derived.partBleed[i] + body.derived.partInternal[i];
        float pain = body.derived.partPain[i];
        pair(g, font, x, y, "bleed", lit(Words.format(bleed, 3)), level(bleed > 0.12f, bleed > 0.001f),
                "part_pain", lit(Math.round(pain) + ""), level(pain > 50f, pain > 20f), time);
        y += LINE;
        pair(g, font, x, y, "infection", s.infection > 0f || exact ? pct(s.infection) : lit("-"),
                level(s.infection > InfectionSystem.NOTICEABLE, s.infection > 0f),
                "fracture", s.isFractured() ? pct((1f - s.fracture) * 100f) : lit("-"), s.isFractured() ? Level.WARN : Level.OK, time);
    }

    /** Fracture, dislocation, fragments, dressing, stitches, splint, tourniquet, disinfection, ice. */
    static String statusGlyphs(PartState s) {
        StringBuilder out = new StringBuilder();
        if (s.isFractured()) out.append("╳");
        if (s.isDislocated()) out.append("↯");
        if (s.shrapnel > 0) out.append("✧").append(s.shrapnel);
        if (s.isBandaged()) out.append("▤");
        if (s.sutured) out.append("≡");
        if (s.splinted) out.append("‖");
        if (s.tourniquet) out.append("⊗");
        if (s.disinfected > 0f) out.append("✚");
        if (s.chilled > 0f) out.append("❄");
        return out.toString();
    }

    private static Component lastStandText(Vitals v) {
        if (v.lastStand > 0f) return lit(Words.duration(v.lastStand));
        return Component.translatable(v.lastStandUsed ? "fleshbone.uni.stand_used" : "fleshbone.uni.stand_ready");
    }

    private static void ecg(GuiGraphicsExtractor g, Body body, int x, int y, int width, boolean exact, float time) {
        g.fill(x, y, x + width, y + ECG_H, 0xFF06100A);
        if (!exact) return;
        Vitals v = body.vitals;
        int trace = v.cardiacArrest || v.fibrillation > 50f ? 0xFFFF5040 : 0xFF50FF70;
        int mid = y + ECG_H / 2;
        int previous = mid;
        for (int i = 0; i < width; i++) {
            float t = time - (width - i) * 0.02f;
            int py = mid - Math.round(VitalsMonitor.wave(v, t) * (ECG_H / 2f - 1));
            g.fill(x + i, Math.min(previous, py), x + i + 1, Math.max(previous, py) + 1, trace);
            previous = py;
        }
    }

    // ------------------------------------------------------------------------------------------------ drawing helpers

    private static void pair(GuiGraphicsExtractor g, Font font, int x, int y, String label1, Component value1, Level level1,
                             String label2, Component value2, Level level2, float time) {
        label(g, font, label1, x + PAD, y);
        right(g, font, value1, x + COL2 - 6, y, color(level1, time));
        label(g, font, label2, x + COL2, y);
        right(g, font, value2, x + WIDTH - PAD, y, color(level2, time));
    }

    private static void barPair(GuiGraphicsExtractor g, Font font, int x, int y, String label1, float fill, int barColor, Component value1,
                                Level level1, String label2, Component value2, Level level2, float time) {
        label(g, font, label1, x + PAD, y);
        int valueW = font.width(value1);
        int barX = x + PAD + font.width(Component.translatable("fleshbone.uni." + label1)) + 3;
        int barEnd = x + COL2 - 6 - valueW - 3;
        if (barEnd - barX >= 6) bar(g, barX, y + 2, barEnd - barX, fill, barColor);
        right(g, font, value1, x + COL2 - 6, y, color(level1, time));
        label(g, font, label2, x + COL2, y);
        right(g, font, value2, x + WIDTH - PAD, y, color(level2, time));
    }

    private static void bar(GuiGraphicsExtractor g, int x, int y, int width, float fill, int color) {
        if (width <= 0) return;
        g.fill(x, y, x + width, y + 5, TRACK);
        g.fill(x, y, x + Math.round(width * Math.max(0f, Math.min(1f, fill))), y + 5, color);
    }

    private static void label(GuiGraphicsExtractor g, Font font, String key, int x, int y) {
        g.text(font, Component.translatable("fleshbone.uni." + key), x, y, LABEL, false);
    }

    private static void right(GuiGraphicsExtractor g, Font font, Component text, int rightX, int y, int color) {
        g.text(font, text, rightX - font.width(text), y, color, false);
    }

    private static Component pct(float value) {
        return Component.literal(Math.round(value) + "%");
    }

    private static Component lit(String text) {
        return Component.literal(text);
    }

    private static Level level(boolean critical, boolean warning) {
        return critical ? Level.CRITICAL : warning ? Level.WARN : Level.OK;
    }

    /** Critical values flash red, as on Casualties: Unknown's health panel. */
    private static int color(Level level, float time) {
        return switch (level) {
            case OK -> VALUE;
            case WARN -> WARN;
            case CRITICAL -> Math.sin(time * 9.0) > 0 ? BAD : BAD_DIM;
        };
    }
}
