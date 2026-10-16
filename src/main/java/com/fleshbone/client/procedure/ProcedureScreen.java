package com.fleshbone.client.procedure;

import java.util.ArrayList;
import java.util.List;

import com.fleshbone.client.ClientBody;
import com.fleshbone.sim.Body;
import com.fleshbone.sim.Skills;
import com.fleshbone.sim.procedure.Mistakes;
import com.fleshbone.sim.procedure.Procedure;
import com.fleshbone.sim.system.ChemistrySystem;
import com.fleshbone.sim.system.SkillSystem;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

/**
 * Base of the hands-on medical procedures. The left square is the work area, where the mouse drives the
 * {@link Procedure} in normalized coordinates (0–1, y downward); the right column lists the steps, the instructions of
 * the current one, what went wrong and the decision buttons. Hand tremor (pain, exhaustion, cold, withdrawal...)
 * moves the tool away from the cursor. Time keeps running in the world while you work, as in Casualties: Unknown.
 * Escape gives up; the harm already done still counts.
 */
public abstract class ProcedureScreen<P extends Procedure> extends Screen {
    /** Receives the outcome; a negative quality means the procedure was given up and only the mistakes count. */
    @FunctionalInterface
    public interface Completion {
        void complete(float quality, int amount, int mistakes);
    }

    protected static final int TEXT = 0xFFE0D8CC;
    protected static final int DIM = 0xFF8C8478;
    protected static final int GOOD = 0xFF7FC060;
    protected static final int WARN = 0xFFE0B040;
    protected static final int BAD = 0xFFE05040;
    protected static final int HEADER = 0xFFFFD27A;
    protected static final int GUIDE = 0x70FFD27A;
    protected static final int SKIN = 0xFFD9A47E;
    protected static final int SKIN_DARK = 0xFFA87556;
    protected static final int FLESH = 0xFFB04440;
    protected static final int BLOOD = 0xFFA01818;
    protected static final int BLOOD_DARK = 0xFF4A0808;
    protected static final int BONE = 0xFFE8E0C8;
    protected static final int METAL = 0xFFC0C6D0;
    protected static final int CLOTH = 0xFFF0ECE2;

    private final Screen parent;
    private final Completion completion;
    protected final P procedure;
    private final String key;
    private final long startNanos = Util.getNanos();
    private final List<int[]> choiceBounds = new ArrayList<>();
    private boolean finished;
    protected int x0;
    protected int y0;
    protected int boxW;
    protected int boxH;
    /** Work area: left, top, side in pixels. */
    protected int wx;
    protected int wy;
    protected int ws;
    protected int sideX;
    protected int sideW;
    /** Seconds since the procedure started, updated every frame. */
    protected float time;
    /**
     * Cursor position from the mouse events, to the sub-pixel: the frame's own mouse position is rounded to whole
     * pixels, and mixing the two made a still hand look like it jumped.
     */
    private double cursorX = Double.NaN;
    private double cursorY;

    protected ProcedureScreen(String name, P procedure, Screen parent, Completion completion) {
        this(name, Component.translatable("fleshbone.procedure." + name), procedure, parent, completion);
    }

    protected ProcedureScreen(String name, Component title, P procedure, Screen parent, Completion completion) {
        super(title);
        this.key = "fleshbone.procedure." + name;
        this.procedure = procedure;
        this.parent = parent;
        this.completion = completion;
    }

    @Override
    protected void init() {
        boxH = Math.min(height - 12, 260);
        ws = boxH - 32;
        sideW = Math.max(110, Math.min(170, width - 12 - ws - 18));
        boxW = ws + sideW + 18;
        x0 = (width - boxW) / 2;
        y0 = (height - boxH) / 2;
        wx = x0 + 6;
        wy = y0 + 26;
        sideX = wx + ws + 6;
        cursorX = Double.NaN;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    protected float time() {
        return (Util.getNanos() - startNanos) / 1e9f;
    }

    protected String key() {
        return key;
    }

    // ------------------------------------------------------------------------------------------------ coordinates

    protected int px(float x) {
        return wx + Math.round(x * ws);
    }

    protected int py(float y) {
        return wy + Math.round(y * ws);
    }

    protected int size(float s) {
        return Math.max(1, Math.round(s * ws));
    }

    private float nx(double screenX) {
        return (float) ((screenX - wx) / ws);
    }

    private float ny(double screenY) {
        return (float) ((screenY - wy) / ws);
    }

    // ------------------------------------------------------------------------------------------------ rendering

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fill(0, 0, width, height, 0x80000000);
    }

    @Override
    public void tick() {
        if (!ClientBody.canAct()) onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        if (finished) return;
        if (minecraft.player == null || !minecraft.player.isAlive()) {
            onClose();
            return;
        }
        time = time();
        if (Double.isNaN(cursorX)) {
            cursorX = mouseX;
            cursorY = mouseY;
        }
        double toolX = cursorX + shakeX();
        double toolY = cursorY + shakeY();
        // One sample per frame, at the frame's time; the hand keeps shaking even when the mouse is still
        if (procedure.holding()) procedure.drag(nx(toolX), ny(toolY), time);
        else procedure.move(nx(toolX), ny(toolY), time);
        procedure.tick(time);
        update(time);

        g.fill(x0, y0, x0 + boxW, y0 + boxH, 0xF0141210);
        g.outline(x0, y0, boxW, boxH, 0xFF5E4A36);
        g.centeredText(font, title, x0 + boxW / 2, y0 + 6, HEADER);
        float limit = effectiveTimeLimit();
        if (limit < 1e6f) {
            float left = Math.max(0f, 1f - time / limit);
            g.fill(x0 + 6, y0 + 18, x0 + boxW - 6, y0 + 20, 0xFF2A2420);
            g.fill(x0 + 6, y0 + 18, x0 + 6 + Math.round((boxW - 12) * left), y0 + 20, left > 0.25f ? 0xFF8C7A5A : BAD);
        }
        g.fill(wx, wy, wx + ws, wy + ws, 0xFF1C1814);
        g.enableScissor(wx, wy, wx + ws, wy + ws);
        drawWork(g);
        int tx = (int) Math.round(toolX);
        int ty = (int) Math.round(toolY);
        if (tx >= wx && tx < wx + ws && ty >= wy && ty < wy + ws) drawTool(g, tx, ty);
        g.disableScissor();
        g.outline(wx - 1, wy - 1, ws + 2, ws + 2, 0xFF3A3028);
        drawSide(g, mouseX, mouseY);
        super.extractRenderState(g, mouseX, mouseY, a);

        if (procedure.done()) onDone();
        else if (time > limit) onTimeout();
    }

    /** Draws the work area (clipped to it). */
    protected abstract void drawWork(GuiGraphicsExtractor g);

    /** The tool, where the shaking hand really is. */
    protected void drawTool(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x - 3, y, x + 4, y + 1, 0xC0FFFFFF);
        g.fill(x, y - 3, x + 1, y + 4, 0xC0FFFFFF);
    }

    /** Called every frame before drawing, after the procedure ticked. */
    protected void update(float t) {
    }

    /** A line under the instructions (progress, gauges in words), or null. */
    protected Component status() {
        return null;
    }

    /** Decision buttons of the current step, or null. */
    protected Component[] choices() {
        return null;
    }

    private void drawSide(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int y = wy;
        String[] steps = procedure.steps();
        for (int i = 0; i < steps.length; i++) {
            boolean done = i < procedure.step() || procedure.succeeded();
            boolean current = i == procedure.step() && !procedure.done();
            String mark = done ? "✔ " : current ? "▶ " : "· ";
            g.text(font, Component.literal(mark).append(Component.translatable(key + "." + steps[i])), sideX, y,
                    done ? GOOD : current ? HEADER : DIM, false);
            y += 10;
        }
        y += 4;
        Component help = Component.translatable(key + "." + procedure.stepKey() + ".help");
        g.textWithWordWrap(font, help, sideX, y, sideW, TEXT);
        y += font.wordWrapHeight(help, sideW) + 5;
        Component status = status();
        if (status != null) {
            g.textWithWordWrap(font, status, sideX, y, sideW, HEADER);
            y += font.wordWrapHeight(status, sideW) + 5;
        }
        Mistakes m = procedure.mistakes();
        if (m.total() > 0) {
            Component mistakes = Component.translatable("fleshbone.procedure.mistakes", m.pain, m.tear, m.dirt);
            g.textWithWordWrap(font, mistakes, sideX, y, sideW, BAD);
        }
        choiceBounds.clear();
        Component[] choices = choices();
        if (choices == null) return;
        int by = y0 + boxH - 6 - choices.length * 17;
        for (int i = 0; i < choices.length; i++) {
            boolean hovered = mouseX >= sideX && mouseX < sideX + sideW && mouseY >= by && mouseY < by + 15;
            g.fill(sideX, by, sideX + sideW, by + 15, hovered ? 0xFF4A3C2C : 0xFF2C241C);
            g.outline(sideX, by, sideW, 15, 0xFF8C7A5A);
            g.centeredText(font, choices[i], sideX + sideW / 2, by + 4, TEXT);
            choiceBounds.add(new int[] {sideX, by, sideX + sideW, by + 15, i});
            by += 17;
        }
    }

    // ------------------------------------------------------------------------------------------------ input

    @Override
    public final boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || finished) return super.mouseClicked(event, doubleClick);
        track(event.x(), event.y());
        for (int[] b : choiceBounds) {
            if (event.x() >= b[0] && event.x() < b[2] && event.y() >= b[1] && event.y() < b[3]) {
                choose(b[4]);
                return true;
            }
        }
        procedure.press(nx(event.x() + shakeX()), ny(event.y() + shakeY()), time());
        return true;
    }

    @Override
    public final boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        track(event.x(), event.y());
        // The procedure is fed once per frame (see extractRenderState): samples a millisecond apart only measure jitter
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && procedure.holding()) return true;
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public final boolean mouseReleased(MouseButtonEvent event) {
        track(event.x(), event.y());
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && procedure.holding()) {
            procedure.release(nx(event.x() + shakeX()), ny(event.y() + shakeY()), time());
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void mouseMoved(double x, double y) {
        track(x, y);
    }

    private void track(double x, double y) {
        cursorX = x;
        cursorY = y;
    }

    /** A decision button was clicked. */
    protected void choose(int option) {
        procedure.choose(option, time());
    }

    // ------------------------------------------------------------------------------------------------ outcome

    /** Finished or failed: by default a success is applied, a failure counts only its harm. */
    protected void onDone() {
        if (procedure.succeeded()) finish(procedure.quality(), procedure.amount());
        else abandon();
    }

    /** Out of time: by default the procedure is given up. */
    protected void onTimeout() {
        abandon();
    }

    protected final void finish(float quality, int amount) {
        if (finished) return;
        finished = true;
        completion.complete(Math.max(0f, Math.min(1f, quality)), amount, procedure.mistakes().pack());
        minecraft.setScreen(parent);
    }

    /** Gives up: the item is not used, but the harm done so far still reaches the patient. */
    protected final void abandon() {
        if (finished) return;
        finished = true;
        if (procedure.mistakes().total() > 0) completion.complete(-1f, 0, procedure.mistakes().pack());
        minecraft.setScreen(parent);
    }

    /** Closes without sending anything. */
    protected final void close() {
        finished = true;
        minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        abandon();
        if (minecraft.screen == this) minecraft.setScreen(parent);
    }

    // ------------------------------------------------------------------------------------------------ the medic's hands

    /** Seconds available. */
    protected float timeLimit() {
        return 45f;
    }

    /** Intelligence (+4 % per level from 10) and strength (+2 % per level) buy time. */
    protected float effectiveTimeLimit() {
        Body body = ClientBody.get();
        if (body == null || timeLimit() >= 1e6f) return timeLimit();
        float factor = 1f + 0.04f * (body.skills.intelligence() - Skills.START_LEVEL) + 0.02f * (body.skills.strength() - Skills.START_LEVEL);
        return timeLimit() * Math.max(0.6f, Math.min(1.6f, factor));
    }

    /** The medic's strength as a multiplier (traction, sawing). */
    protected static float strength() {
        Body body = ClientBody.get();
        return body == null ? 1f : SkillSystem.handSpeed(body.skills);
    }

    /**
     * Hand tremor in pixels: pain, exhaustion, cold, a damaged brain, withdrawal and stimulants shake the hands;
     * resilience steadies them (−6 % per level from 10).
     */
    protected float tremor() {
        Body body = ClientBody.get();
        if (body == null) return 0f;
        float shake = 0.8f + body.derived.totalPain / 30f + (100f - body.needs.stamina) / 40f
                + Math.max(0f, 35f - body.vitals.bodyTemp) * 1.5f + Math.max(0f, 80f - body.vitals.brainHealth) / 10f
                + ChemistrySystem.withdrawal(body.chem) / 5f + body.chem.caffeine / 40f + body.chem.epinephrine / 30f;
        return shake * SkillSystem.shakiness(body.skills);
    }

    protected double shakeX() {
        double t = Util.getNanos() / 1e9;
        return (Math.sin(t * 7.3) * 0.6 + Math.sin(t * 13.1 + 1.7) * 0.4) * tremor();
    }

    protected double shakeY() {
        double t = Util.getNanos() / 1e9;
        return (Math.sin(t * 8.9 + 0.5) * 0.6 + Math.sin(t * 11.7 + 2.3) * 0.4) * tremor();
    }

    // ------------------------------------------------------------------------------------------------ drawing helpers

    /** Rectangle in work-area coordinates. */
    protected void rect(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int color) {
        g.fill(px(Math.min(x0, x1)), py(Math.min(y0, y1)), px(Math.max(x0, x1)), py(Math.max(y0, y1)), color);
    }

    /** Filled circle in work-area coordinates. */
    protected void disc(GuiGraphicsExtractor g, float x, float y, float r, int color) {
        disc(g, px(x), py(y), size(r), color);
    }

    /** Circle outline in work-area coordinates. */
    protected void ring(GuiGraphicsExtractor g, float x, float y, float r, int color) {
        int cx = px(x);
        int cy = py(y);
        int radius = size(r);
        int dots = Math.max(12, radius * 6);
        for (int i = 0; i < dots; i++) {
            double a = i * 2 * Math.PI / dots;
            int dx = cx + (int) Math.round(Math.cos(a) * radius);
            int dy = cy + (int) Math.round(Math.sin(a) * radius);
            g.fill(dx, dy, dx + 1, dy + 1, color);
        }
    }

    /** Line of a given pixel width in work-area coordinates. */
    protected void nline(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int width, int color) {
        line(g, (float) px(x0), (float) py(y0), (float) px(x1), (float) py(y1), width, color);
    }

    /** Dotted line in work-area coordinates. */
    protected void dotted(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int color) {
        int steps = Math.max(1, Math.round(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) * ws / 3f));
        for (int i = 0; i <= steps; i++) {
            int x = px(x0 + (x1 - x0) * i / steps);
            int y = py(y0 + (y1 - y0) * i / steps);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    protected static void disc(GuiGraphicsExtractor g, int x, int y, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.round(Math.sqrt(r * r - dy * dy));
            g.fill(x - half, y + dy, x + half + 1, y + dy + 1, color);
        }
    }

    /** Thick line in pixels, drawn with small squares. */
    protected static void line(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int width, int color) {
        int steps = (int) Math.max(1, Math.ceil(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0))));
        int half = width / 2;
        int lastX = Integer.MIN_VALUE;
        int lastY = Integer.MIN_VALUE;
        for (int i = 0; i <= steps; i++) {
            int x = Math.round(x0 + (x1 - x0) * i / steps);
            int y = Math.round(y0 + (y1 - y0) * i / steps);
            if (x == lastX && y == lastY) continue;
            lastX = x;
            lastY = y;
            g.fill(x - half, y - half, x - half + width, y - half + width, color);
        }
    }

    /** Horizontal gauge with a good band. */
    protected void gauge(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, float value, float max,
                         float goodMin, float goodMax, int color) {
        rect(g, x0, y0, x1, y1, 0xFF2A2420);
        float w = x1 - x0;
        rect(g, x0 + w * goodMin / max, y0, x0 + w * goodMax / max, y1, 0xFF2E5A24);
        float v = Math.max(0f, Math.min(max, value));
        rect(g, x0, y0 + (y1 - y0) * 0.3f, x0 + w * v / max, y1 - (y1 - y0) * 0.3f, color);
    }

    /** Pulsing 0–1 value for blinking marks. */
    protected float pulse(float speed) {
        return 0.5f + 0.5f * (float) Math.sin(time * speed);
    }

    /** Color with its alpha replaced. */
    protected static int alpha(int color, float a) {
        return (Math.round(Math.max(0f, Math.min(1f, a)) * 255f) << 24) | (color & 0xFFFFFF);
    }

    protected static int percent(float value) {
        return Math.round(value * 100f);
    }
}
