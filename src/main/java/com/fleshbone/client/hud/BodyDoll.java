package com.fleshbone.client.hud;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.PartState;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Paper-doll view of the body, Casualties: Unknown style: the outer band of each part reddens as its skin is lost,
 * the inside as its muscle is lost. Drawn on a 20×44 grid; the player's left side is on the left of the screen.
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
        g.fill(x0, y0, x1, y1, tissueColor(s.skin));
        if (x1 - x0 >= 3 && y1 - y0 >= 3) g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, tissueColor(s.muscle));

        if (s.infection > 25f) {
            int alpha = (int) Math.min(170f, s.infection * 1.7f);
            g.fill(x0, y0, x1, y1, (alpha << 24) | 0x9030C0);
        }
        float bleed = body.derived.partBleed[part.ordinal()] + body.derived.partInternal[part.ordinal()];
        if (bleed > 0.001f) {
            float pulse = 0.55f + 0.45f * (float) Math.sin(time * 6.0 + part.ordinal());
            int alpha = (int) (Math.min(1f, 0.25f + bleed / 0.45f) * pulse * 200f);
            g.fill(x0, y0, x1, y1, (alpha << 24) | 0xE00000);
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
