package com.fleshbone.client.minigame;

import com.fleshbone.client.ClientBody;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Base of the hands-on treatment minigames. Time keeps running in the world while you work, as in Casualties: Unknown.
 * Escape cancels without using anything; finishing reports a quality (0–1) and an amount used.
 */
public abstract class MinigameScreen extends Screen {
    /** Receives the result of a finished minigame. */
    @FunctionalInterface
    public interface Completion {
        void complete(float quality, int amount);
    }

    protected static final int TEXT = 0xFFE0D8CC;
    protected static final int DIM = 0xFF8C8478;
    protected static final int GOOD = 0xFF7FC060;
    protected static final int BAD = 0xFFE05040;
    protected static final int HEADER = 0xFFFFD27A;
    protected static final float TIME_LIMIT = 30f;

    private final Screen parent;
    private final Completion completion;
    private final Component instructions;
    private final long startMillis = Util.getMillis();
    private boolean finished;
    protected int cx;
    protected int cy;
    protected int boxW = 280;
    protected int boxH = 200;

    protected MinigameScreen(Component title, Component instructions, Screen parent, Completion completion) {
        super(title);
        this.parent = parent;
        this.completion = completion;
        this.instructions = instructions;
    }

    @Override
    protected void init() {
        cx = width / 2;
        cy = height / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Seconds since the minigame started. */
    protected float time() {
        return (Util.getMillis() - startMillis) / 1000f;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fill(0, 0, width, height, 0x80000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        if (ClientBody.get() == null && minecraft != null && minecraft.player != null && !minecraft.player.isAlive()) {
            onClose();
            return;
        }
        int x0 = cx - boxW / 2;
        int y0 = cy - boxH / 2;
        g.fill(x0, y0, x0 + boxW, y0 + boxH, 0xF0141210);
        g.outline(x0, y0, boxW, boxH, 0xFF5E4A36);
        g.centeredText(font, title, cx, y0 + 6, HEADER);
        float left = Math.max(0f, 1f - time() / Math.min(effectiveTimeLimit(), 1e6f));
        g.fill(x0 + 6, y0 + 18, x0 + boxW - 6, y0 + 20, 0xFF2A2420);
        g.fill(x0 + 6, y0 + 18, x0 + 6 + Math.round((boxW - 12) * left), y0 + 20, left > 0.25f ? 0xFF8C7A5A : BAD);
        g.textWithWordWrap(font, instructions, x0 + 8, y0 + boxH - 30, boxW - 16, DIM);
        drawGame(g, (int) Math.round(mouseX + shakeX()), (int) Math.round(mouseY + shakeY()));
        if (time() > effectiveTimeLimit()) onTimeout();
        super.extractRenderState(g, mouseX, mouseY, a);
    }

    protected abstract void drawGame(GuiGraphicsExtractor g, int mouseX, int mouseY);

    /** Seconds available before {@link #onTimeout()}. */
    protected float timeLimit() {
        return TIME_LIMIT;
    }

    /** Intelligence (+4 % per level from 10) and strength (+2 % per level) buy time. */
    protected float effectiveTimeLimit() {
        com.fleshbone.sim.Body body = ClientBody.get();
        if (body == null) return timeLimit();
        float factor = 1f + 0.04f * (body.skills.intelligence() - com.fleshbone.sim.Skills.START_LEVEL)
                + 0.02f * (body.skills.strength() - com.fleshbone.sim.Skills.START_LEVEL);
        return timeLimit() * Math.max(0.6f, Math.min(1.6f, factor));
    }

    /**
     * Hand tremor in pixels: pain, exhaustion, cold, a damaged brain and withdrawal shake the hands; resilience
     * steadies them (−6 % per level from 10).
     */
    protected float tremor() {
        com.fleshbone.sim.Body body = ClientBody.get();
        if (body == null) return 0f;
        float shake = 0.8f + body.derived.totalPain / 30f + (100f - body.needs.stamina) / 40f
                + Math.max(0f, 35f - body.vitals.bodyTemp) * 1.5f + Math.max(0f, 80f - body.vitals.brainHealth) / 10f
                + com.fleshbone.sim.system.ChemistrySystem.withdrawal(body.chem) / 10f;
        return shake * com.fleshbone.sim.system.SkillSystem.shakiness(body.skills);
    }

    protected double shakeX() {
        double t = Util.getMillis() / 1000.0;
        return (Math.sin(t * 7.3) * 0.6 + Math.sin(t * 13.1 + 1.7) * 0.4) * tremor();
    }

    protected double shakeY() {
        double t = Util.getMillis() / 1000.0;
        return (Math.sin(t * 8.9 + 0.5) * 0.6 + Math.sin(t * 11.7 + 2.3) * 0.4) * tremor();
    }

    private MouseButtonEvent shaken(MouseButtonEvent event) {
        return new MouseButtonEvent(event.x() + shakeX(), event.y() + shakeY(), event.buttonInfo());
    }

    @Override
    public final boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return click(shaken(event), doubleClick);
    }

    @Override
    public final boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return drag(shaken(event), dx, dy);
    }

    @Override
    public final boolean mouseReleased(MouseButtonEvent event) {
        return release(shaken(event));
    }

    /** Clicks, drags and releases arrive with the tremor already applied. */
    protected boolean click(MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(event, doubleClick);
    }

    protected boolean drag(MouseButtonEvent event, double dx, double dy) {
        return super.mouseDragged(event, dx, dy);
    }

    protected boolean release(MouseButtonEvent event) {
        return super.mouseReleased(event);
    }

    /** Out of time: by default the work is finished with whatever was achieved. */
    protected abstract void onTimeout();

    protected void finish(float quality, int amount) {
        if (finished) return;
        finished = true;
        completion.complete(Math.max(0f, Math.min(1f, quality)), amount);
        minecraft.setScreen(parent);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        finished = true;
        minecraft.setScreen(parent);
    }

    /** Filled circle drawn with horizontal spans. */
    protected static void disc(GuiGraphicsExtractor g, int x, int y, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.round(Math.sqrt(r * r - dy * dy));
            g.fill(x - half, y + dy, x + half + 1, y + dy + 1, color);
        }
    }

    /** Ring of dots. */
    protected static void dottedRing(GuiGraphicsExtractor g, int x, int y, int r, int color, int dots) {
        for (int i = 0; i < dots; i++) {
            double a = i * 2 * Math.PI / dots;
            int px = x + (int) Math.round(Math.cos(a) * r);
            int py = y + (int) Math.round(Math.sin(a) * r);
            g.fill(px, py, px + 1, py + 1, color);
        }
    }

    /** Line approximated with small squares. */
    protected static void line(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int color) {
        int steps = (int) Math.max(1, Math.ceil(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0))));
        for (int i = 0; i <= steps; i++) {
            int px = Math.round(x0 + (x1 - x0) * i / steps);
            int py = Math.round(y0 + (y1 - y0) * i / steps);
            g.fill(px, py, px + 1, py + 1, color);
        }
    }
}
