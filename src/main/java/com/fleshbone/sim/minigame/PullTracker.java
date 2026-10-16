package com.fleshbone.sim.minigame;

/**
 * Shrapnel extraction: grab the fragment and pull it out slowly along its axis. Pulling faster than the tool allows
 * makes it slip. Tweezers tolerate faster, firmer pulls than fingers.
 */
public final class PullTracker {
    private final float fragmentX;
    private final float fragmentY;
    private final float dirX;
    private final float dirY;
    private final float requiredDistance;
    private final float maxSpeed;
    private final float grabRadius;
    private boolean grabbing;
    private boolean slipped;
    private float progress;
    private float lastX;
    private float lastY;
    private float lastTime;
    private float speed;

    /**
     * @param angle    pull direction in radians
     * @param maxSpeed pixels per second before slipping
     */
    public PullTracker(float fragmentX, float fragmentY, float angle, float requiredDistance, float maxSpeed, float grabRadius) {
        this.fragmentX = fragmentX;
        this.fragmentY = fragmentY;
        this.dirX = (float) Math.cos(angle);
        this.dirY = (float) Math.sin(angle);
        this.requiredDistance = requiredDistance;
        this.maxSpeed = maxSpeed;
        this.grabRadius = grabRadius;
    }

    /** Current fragment position (it follows the pull). */
    public float fragmentX() {
        return fragmentX + dirX * progress;
    }

    public float fragmentY() {
        return fragmentY + dirY * progress;
    }

    public boolean grab(float x, float y, float time) {
        if (slipped || done()) return false;
        float dx = x - fragmentX();
        float dy = y - fragmentY();
        grabbing = dx * dx + dy * dy <= grabRadius * grabRadius;
        lastX = x;
        lastY = y;
        lastTime = time;
        return grabbing;
    }

    public void drag(float x, float y, float time) {
        if (!grabbing || slipped || done()) return;
        float dt = Math.max(0.001f, time - lastTime);
        float dx = x - lastX;
        float dy = y - lastY;
        float along = dx * dirX + dy * dirY;
        speed = Math.abs(along) / dt;
        if (speed > maxSpeed) {
            slipped = true;
            grabbing = false;
            return;
        }
        progress = Math.max(0f, Math.min(requiredDistance, progress + along));
        lastX = x;
        lastY = y;
        lastTime = time;
    }

    public void release() {
        grabbing = false;
        speed = 0f;
    }

    public boolean grabbing() {
        return grabbing;
    }

    public boolean slipped() {
        return slipped;
    }

    public boolean done() {
        return progress >= requiredDistance;
    }

    /** Last pull speed relative to the slip limit (above 1 slips). */
    public float speedRatio() {
        return speed / maxSpeed;
    }

    public float progress() {
        return progress / requiredDistance;
    }

    public float quality() {
        if (slipped) return 0f;
        return done() ? 1f : progress() * 0.5f;
    }
}
