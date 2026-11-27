package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * A melee weapon: how often it may swing, and what the swing is made of.
 *
 * <p>Every field is something that will differ between weapons, which is why the hand strike is this
 * component with bare-handed numbers rather than a system with constants in it. A heavier weapon is a
 * longer {@code windupSeconds}, a wider {@code arcDegrees} and a harder {@code knockbackSpeed}, and
 * nothing in the systems changes.
 *
 * <p>The aim point is captured when the swing is asked for rather than when it appears, so a weapon
 * with a wind-up commits to a direction at the moment the player decided, not after.
 */
public final class Melee implements Component, Pool.Poolable {

    /** A sweep that travels its arc anticlockwise: the way a weapon's first swing goes. */
    private static final int SWEEP_ANTICLOCKWISE = 1;

    /** A sweep that travels its arc clockwise. */
    private static final int SWEEP_CLOCKWISE = -1;

    public int cooldownTicks;
    public int remainingTicks;
    public int windupTicks;
    public float activeSeconds;
    public float reach;
    public float radius;
    public int damage;
    public float arcDegrees;
    public int lockTicks;

    /** Whether that lock is spent only on a target already committed to attacking. */
    public boolean locksCommittedOnly;

    /** How fast a body it kills finishes its lunge into the commander, in world units per second. */
    public float clinchCarrySpeed;

    /** How fast that carry bleeds off, in world units per second per second. */
    public float clinchCarryDecay;

    public float knockbackSpeed;
    public float knockbackDecay;

    /** Counts down a wind-up already asked for. Decremented by {@code CooldownSystem}. */
    public int windupRemaining;

    public boolean swingPending;

    public float pendingAimX;
    public float pendingAimY;

    /**
     * How many sweeps this weapon has released: the only thing that decides which way the next one
     * goes, so the alternation costs no dice and lives in world state.
     */
    public int swingsReleased;

    /** Sets the weapon ready to swing immediately, and returns this component, for fluent spawning. */
    public Melee set(
            int newCooldownTicks,
            int newWindupTicks,
            float newActiveSeconds,
            float newReach,
            float newRadius,
            int newDamage,
            float newArcDegrees,
            int newLockTicks,
            boolean newLocksCommittedOnly,
            float newClinchCarrySpeed,
            float newClinchCarryDecay,
            float newKnockbackSpeed,
            float newKnockbackDecay) {
        this.cooldownTicks = newCooldownTicks;
        this.remainingTicks = 0;
        this.windupTicks = newWindupTicks;
        this.activeSeconds = newActiveSeconds;
        this.reach = newReach;
        this.radius = newRadius;
        this.damage = newDamage;
        this.arcDegrees = newArcDegrees;
        this.lockTicks = newLockTicks;
        this.locksCommittedOnly = newLocksCommittedOnly;
        this.clinchCarrySpeed = newClinchCarrySpeed;
        this.clinchCarryDecay = newClinchCarryDecay;
        this.knockbackSpeed = newKnockbackSpeed;
        this.knockbackDecay = newKnockbackDecay;
        this.windupRemaining = 0;
        this.swingPending = false;
        this.pendingAimX = 0f;
        this.pendingAimY = 0f;
        this.swingsReleased = 0;
        return this;
    }

    /** True if a fresh swing may be asked for: off cooldown, and not already winding one up. */
    public boolean ready() {
        return remainingTicks <= 0 && !swingPending;
    }

    /** Remembers where the player was aiming and starts the wind-up. */
    public void beginSwing(float aimX, float aimY) {
        swingPending = true;
        windupRemaining = windupTicks;
        pendingAimX = aimX;
        pendingAimY = aimY;
        remainingTicks = cooldownTicks;
    }

    /** True once a pending swing has finished winding up and the sweep should appear. */
    public boolean releasing() {
        return swingPending && windupRemaining <= 0;
    }

    /** Clears the pending swing once its sweep has been spawned. */
    public void released() {
        swingPending = false;
        windupRemaining = 0;
    }

    /**
     * Which way this swing sweeps - anticlockwise across the aim, or the other way round - alternating
     * from swing to swing, and it takes the swing off the count as it answers. Nothing else decides it:
     * no dice, no clock. It holds across a pause, and a weapon read back from a snapshot carries on
     * where its last swing left off.
     *
     * <p>Ask it <strong>once per swing</strong>, wherever that swing's bodies go into the world: a swing
     * made of several sweeps still takes all of them the same way.
     */
    public int nextSweepDirection() {
        return (swingsReleased++ & 1) == 0 ? SWEEP_ANTICLOCKWISE : SWEEP_CLOCKWISE;
    }

    @Override
    public void reset() {
        cooldownTicks = 0;
        remainingTicks = 0;
        windupTicks = 0;
        activeSeconds = 0f;
        reach = 0f;
        radius = 0f;
        damage = 0;
        arcDegrees = 0f;
        lockTicks = 0;
        locksCommittedOnly = false;
        clinchCarrySpeed = 0f;
        clinchCarryDecay = 0f;
        knockbackSpeed = 0f;
        knockbackDecay = 0f;
        windupRemaining = 0;
        swingPending = false;
        pendingAimX = 0f;
        pendingAimY = 0f;
        swingsReleased = 0;
    }
}
