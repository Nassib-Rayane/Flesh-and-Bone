package com.fleshbone.client.hud;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.PartState;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Paper-doll view of the body, Casualties: Unknown style: each part is coloured by its pain (pale → red), darkened by
 * lost muscle, rimmed raw red by lost skin; blood drops mark bleeding parts. Drawn on a 20×44 grid; the player's left
 * side is on the left of the screen.
 */
public final class BodyDoll {
    public static final int GRID_W = 20;
    public static final int GRID_H = 44;
    /** x0, y0, x1, y1 in grid units, indexed by part ordinal. */
    private static final int[][] RECTS = new int[BodyPart.COUNT][];

    static {
        set(BodyPart.HEAD, 7, 0, 13, 6);
        set(BodyPart.THORAX, 6, 7, 14, 15);
        set(BodyPart.ABDOMEN, 6, 15, 14, 21);
        set(BodyPart.UPPER_ARM_L, 3, 7, 6, 15);
        set(BodyPart.FOREARM_L, 2, 15, 5, 22);
        set(BodyPart.HAND_L, 1, 22, 5, 26);
        set(BodyPart.UPPER_ARM_R, 14, 7, 17, 15);
        set(BodyPart.FOREARM_R, 15, 15, 18, 22);
        set(BodyPart.HAND_R, 15, 22, 19, 26);
        set(BodyPart.THIGH_L, 6, 21, 10, 32);
        set(BodyPart.SHIN_L, 6, 32, 10, 44);
        set(BodyPart.THIGH_R, 10, 21, 14, 32);
        set(BodyPart.SHIN_R, 10, 32, 14, 44);
    }

    private BodyDoll() {}

    private static void set(BodyPart part, int x0, int y0, int x1, int y1) {
        RECTS[part.ordinal()] = new int[] {x0, y0, x1, y1};
    }

    public static int[] rect(BodyPart part) {
        return RECTS[part.ordinal()];
    }

    /**
     * @param highlighted part outlined in yellow, or {@code null}
     * @param time        seconds, animates bleeding
     */
    public static void draw(GuiGraphicsExtractor g, Body body, int x, int y, float scale, BodyPart highlighted, float time) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        for (BodyPart part : BodyPart.VALUES) drawPart(g, body, part, part == highlighted, time);
        g.pose().pushMatrix();
        g.pose().scale(0.5f, 0.5f);
        drawBlood(g, body, time);
        g.pose().popMatrix();
        g.pose().popMatrix();
    }

    /** The part under a screen position, or {@code null}. */
    public static BodyPart partAt(double mouseX, double mouseY, int x, int y, float scale) {
        double gx = (mouseX - x) / scale;
        double gy = (mouseY - y) / scale;
        for (BodyPart part : BodyPart.VALUES) {
            int[] r = RECTS[part.ordinal()];
            if (gx >= r[0] && gx < r[2] && gy >= r[1] && gy < r[3]) return part;
        }
        return null;
    }

    private static void drawPart(GuiGraphicsExtractor g, Body body, BodyPart part, boolean highlighted, float time) {
        PartState s = body.part(part);
        int[] r = RECTS[part.ordinal()];
        int x0 = r[0];
        int y0 = r[1];
        int x1 = r[2];
        int y1 = r[3];
        if (s.amputated) {
            g.outline(x0, y0, x1 - x0, y1 - y0, 0x80303030);
            if (highlighted) g.outline(x0, y0, x1 - x0, y1 - y0, 0xFFFFE040);
            return;
        }
        // Pain colours the limb (pale → yellow → orange → red), as in Casualties: Unknown
        g.fill(x0, y0, x1, y1, painColor(body.derived.partPain[part.ordinal()]));
        // Lost muscle darkens the inside, lost skin turns the rim raw red
        int inset = x1 - x0 >= 3 && y1 - y0 >= 3 ? 1 : 0;
        int dark = Math.round(Math.max(0f, 100f - s.muscle) / 100f * 0.7f * 255f);
        if (dark > 4) g.fill(x0 + inset, y0 + inset, x1 - inset, y1 - inset, dark << 24);
        int raw = Math.round(Math.max(0f, 100f - s.skin) / 100f * 255f);
        if (raw > 8 && inset > 0) {
            int ring = (raw << 24) | 0x7A0C08;
            g.fill(x0, y0, x1, y0 + 1, ring);
            g.fill(x0, y1 - 1, x1, y1, ring);
            g.fill(x0, y0 + 1, x0 + 1, y1 - 1, ring);
            g.fill(x1 - 1, y0 + 1, x1, y1 - 1, ring);
        }
        if (s.infection > 10f) {
            int alpha = Math.round(Math.min(1f, s.infection / 60f) * 230f);
            for (int yy = y0; yy < y1; yy++) {
                for (int xx = x0; xx < x1; xx++) {
                    if ((xx * 7 + yy * 13 + part.ordinal() * 3) % 5 == 0) g.fill(xx, yy, xx + 1, yy + 1, (alpha << 24) | 0x58B030);
                }
            }
        }
        if (s.isBandaged()) {
            int color = s.bandageSoak > 0.6f ? 0xE0B04040 : s.bandageSoak > 0.3f ? 0xE0E0B0B0 : 0xE0F0F0F0;
            for (int yy = y0 + 1; yy < y1 - 1; yy += 2) g.fill(x0, yy, x1, yy + 1, color);
        }
        if (s.splinted) {
            g.fill(x0, y0, x0 + 1, y1, 0xFF8B5A2B);
            g.fill(x1 - 1, y0, x1, y1, 0xFF8B5A2B);
        }
        if (s.tourniquet) g.fill(x0, y0, x1, y0 + 1, 0xFF101010);
        if (s.isFractured()) {
            int mid = (y0 + y1) / 2;
            int cx = (x0 + x1) / 2;
            g.fill(x0, mid, cx, mid + 1, 0xFFFFFFFF);
            g.fill(cx, mid - 1, x1, mid, 0xFFFFFFFF);
        }
        if (s.isDislocated()) g.fill(x0, y0, x0 + 1, y0 + 1, 0xFFFFD020);
        for (int n = 0; n < Math.min(3, s.shrapnel); n++) {
            int px = x0 + 1 + n;
            if (px < x1) g.fill(px, y1 - 2, px + 1, y1 - 1, 0xFF505050);
        }
        if (highlighted) g.outline(x0, y0, x1 - x0, y1 - y0, 0xFFFFE040);
    }

    /** Blood drops on bleeding parts (1 to 3 by flow, one dripping), a dark drop for bleeding inside. Half-grid units. */
    private static void drawBlood(GuiGraphicsExtractor g, Body body, float time) {
        for (BodyPart part : BodyPart.VALUES) {
            int i = part.ordinal();
            float external = body.derived.partBleed[i];
            float internal = body.derived.partInternal[i];
            if (external <= 0.001f && internal <= 0.001f) continue;
            int[] r = RECTS[i];
            int cx = r[0] + r[2];
            int cy = r[1] + r[3];
            if (internal > 0.001f) drop(g, cx - 2, cy - 4, 0xFF3A0010, 0xFF8A2040);
            if (external > 0.001f) {
                int drops = external >= 0.225f ? 3 : external >= 0.09f ? 2 : 1;
                int width = (r[2] - r[0]) * 2;
                for (int n = 0; n < drops; n++) {
                    int dx = drops == 1 ? 0 : Math.round((n - (drops - 1) / 2f) * Math.min(6f, width / (float) drops));
                    drop(g, cx - 2 + dx, cy - 4 - (width < 7 ? n * 3 : 0), 0xFFC80A0A, 0xFFFF6A5A);
                }
                // One drop falls from the bottom of the part, faster the heavier the flow
                float speed = 0.7f + Math.min(2f, external * 6f);
                float fall = (time * speed + i * 0.37f) % 1f;
                int alpha = Math.round((1f - fall) * 255f);
                int y = r[3] * 2 + Math.round(fall * 6f);
                g.fill(cx - 1, y, cx + 1, y + 2, (alpha << 24) | 0xC80A0A);
            }
        }
    }

    /** 5×7 drop (half-grid units) with a light top-left glint. */
    private static void drop(GuiGraphicsExtractor g, int x, int y, int color, int glint) {
        g.fill(x + 2, y, x + 3, y + 2, color);
        g.fill(x + 1, y + 2, x + 4, y + 3, color);
        g.fill(x, y + 3, x + 5, y + 6, color);
        g.fill(x + 1, y + 6, x + 4, y + 7, color);
        g.fill(x + 1, y + 3, x + 2, y + 4, glint);
    }

    /** Pale without pain, then yellow, orange and red as it hurts more. */
    public static int painColor(float pain) {
        float p = Math.max(0f, pain);
        int rgb;
        if (p < 20f) rgb = lerp(0xE6E0D6, 0xEED79A, p / 20f);
        else if (p < 45f) rgb = lerp(0xEED79A, 0xE8963C, (p - 20f) / 25f);
        else rgb = lerp(0xE8963C, 0xD02A1E, Math.min(1f, (p - 45f) / 30f));
        return 0xFF000000 | rgb;
    }

    /** Pale when healthy, red when hurt, nearly black when destroyed. */
    public static int tissueColor(float health) {
        float h = Math.max(0f, Math.min(100f, health)) / 100f;
        int healthy = 0xD8CFC4;
        int hurt = 0xC0442E;
        int dead = 0x3A0606;
        int rgb = h >= 0.5f ? lerp(hurt, healthy, (h - 0.5f) * 2f) : lerp(dead, hurt, h * 2f);
        return 0xFF000000 | rgb;
    }

    private static int lerp(int from, int to, float t) {
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int gr = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (r << 16) | (gr << 8) | b;
    }
}
