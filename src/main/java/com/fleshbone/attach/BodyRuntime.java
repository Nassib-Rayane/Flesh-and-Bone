package com.fleshbone.attach;

import com.fleshbone.damage.DamageClassifier;
import com.fleshbone.sim.Environment;

import net.minecraft.world.damagesource.DamageSource;

/** Per-player state that only matters while the server runs: never saved, never synced. */
public final class BodyRuntime {
    /** Plan of the damage event being processed (computed when it starts, applied once the final damage is known). */
    public DamageClassifier.Plan pendingPlan;
    /** Source of the damage event {@link #pendingPlan} belongs to. */
    public DamageSource pendingSource;
    /** Ticks left of "stuck in a block" suffocation. */
    public int suffocationTicks;
    public long lastSyncTick;
    /** Game time of this player's last treatment as a medic (rate limiting). */
    public long lastTreatTick = -1000;
    /** Game time of the last CPR update received from this medic. */
    public long lastCprTick = -1000;
    /** The body changed in a way the client should see quickly. */
    public boolean dirty = true;
    public double lastX;
    public double lastZ;
    public boolean hasLastPosition;
    /** Horizontal speed in blocks per second, measured from the position: the server never sees a player's velocity. */
    public float moveSpeed;
    /** Last values applied as attribute modifiers, to avoid touching attributes every tick. */
    public float appliedMove = 1f;
    public float appliedJump = 1f;
    public float appliedAttack = 1f;
    public float appliedAttackSpeed = 1f;
    public float appliedMining = 1f;
    public float appliedSafeFall;
    public boolean forcedPose;
    /** The vanilla food bar is held at 19 while the C:U hunger runs; false until the first locked tick. */
    public boolean foodLocked;
    /** Bare-handed mining of hard blocks: last tick counted, and ticks since the last bruise. */
    public long lastPunchTick;
    public int punchTicks;
    /** Ticks counted between two checks for scratchy plants. */
    public int scratchTimer;
    /** Workout chosen from the health panel. */
    public Environment.Exercise exercise = Environment.Exercise.NONE;
    // Environment sampled once a second
    public long lastSampleTick = -1;
    public float ambientTemp = 20f;
    public float humidity = 0.5f;
    public float waterTemp = 15f;
    public float heatTarget = 20f;
    public float heatStrength;
    public float insulation;
    public int light = 15;
    public boolean skyVisible = true;
    public boolean comfort;
    /** Kilograms carried. */
    public float load;
    /** Quality of the ground for a sleep, 0–4. */
    public int sleepQuality = 1;
}
