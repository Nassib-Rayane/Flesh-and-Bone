package com.fleshbone.sim.procedure;

/**
 * A medical procedure in steps, as pure logic: the client feeds it mouse input in normalized work-area coordinates
 * (0–1 on both axes, y downward) and the time in seconds, draws its state, and sends the outcome (quality, amount used,
 * mistakes) to the server when it is done.
 */
public abstract class Procedure {
    public enum Phase { RUNNING, DONE, ABORTED }

    protected final Mistakes mistakes = new Mistakes();
    private int step;
    private Phase phase = Phase.RUNNING;
    private float stepStart;
    private float lastTime = -1f;
    /** Mouse held down. */
    protected boolean holding;

    /** Translation key stems of the steps, in order. */
    public abstract String[] steps();

    public final int step() {
        return Math.min(step, steps().length - 1);
    }

    public final String stepKey() {
        return steps()[step()];
    }

    public final Phase phase() {
        return phase;
    }

    public final boolean done() {
        return phase != Phase.RUNNING;
    }

    /** Mouse button held. */
    public final boolean holding() {
        return holding;
    }

    public final Mistakes mistakes() {
        return mistakes;
    }

    /** 0–1, how well it went. */
    public abstract float quality();

    /** How much of the item was used (bandage turns, saw strokes, energy level...), 0 = default. */
    public int amount() {
        return 0;
    }

    /** Whether the procedure reached its goal (a fragment out, a vein found...). */
    public boolean succeeded() {
        return phase == Phase.DONE;
    }

    public void press(float x, float y, float t) {
        holding = true;
        onPress(x, y, t);
    }

    public void drag(float x, float y, float t) {
        onDrag(x, y, t);
    }

    public void release(float x, float y, float t) {
        holding = false;
        onRelease(x, y, t);
    }

    /** The cursor moved without a button down. */
    public void move(float x, float y, float t) {
        onMove(x, y, t);
    }

    /** A choice button of the procedure (decision steps). */
    public void choose(int option, float t) {
        onChoose(option, t);
    }

    /** Called every frame. */
    public final void tick(float t) {
        if (done()) return;
        float dt = lastTime < 0f ? 0f : Math.max(0f, Math.min(0.1f, t - lastTime));
        lastTime = t;
        onTick(t, dt);
    }

    protected void onPress(float x, float y, float t) {}

    protected void onDrag(float x, float y, float t) {}

    protected void onRelease(float x, float y, float t) {}

    protected void onMove(float x, float y, float t) {}

    protected void onChoose(int option, float t) {}

    protected void onTick(float t, float dt) {}

    protected final void advance(float t) {
        step++;
        stepStart = t;
        if (step >= steps().length) phase = Phase.DONE;
    }

    /** Back to an earlier step (lost traction, slipped fragment...). */
    protected final void goTo(int target, float t) {
        step = Math.max(0, Math.min(steps().length - 1, target));
        stepStart = t;
    }

    protected final void finish() {
        phase = Phase.DONE;
    }

    protected final void abort() {
        phase = Phase.ABORTED;
    }

    /** Seconds spent in the current step. */
    protected final float stepTime(float t) {
        return t - stepStart;
    }

    protected static float dist(float x0, float y0, float x1, float y1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    protected static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(1f, v);
    }
}
