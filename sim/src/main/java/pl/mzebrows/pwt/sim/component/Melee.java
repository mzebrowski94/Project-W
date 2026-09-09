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

    public float cooldownSeconds;
    public float remainingSeconds;
    public float windupSeconds;
    public float activeSeconds;
    public float reach;
    public float radius;
    public float damage;
    public float arcDegrees;
    public float lockSeconds;

    /** Whether that lock is spent only on a target already committed to attacking. */
    public boolean locksCommittedOnly;

    /** How fast a body it kills finishes its lunge into the commander, in world units per second. */
    public float clinchCarrySpeed;

    /** How fast that carry bleeds off, in world units per second per second. */
    public float clinchCarryDecay;

    public float knockbackSpeed;
    public float knockbackDecay;

    /** Counts down a wind-up already asked for. Decremented by {@code CooldownSystem}. */
    public float windupRemaining;

    public boolean swingPending;

    public float pendingAimX;
    public float pendingAimY;

    /** Sets the weapon ready to swing immediately, and returns this component, for fluent spawning. */
    public Melee set(
            float newCooldownSeconds,
            float newWindupSeconds,
            float newActiveSeconds,
            float newReach,
            float newRadius,
            float newDamage,
            float newArcDegrees,
            float newLockSeconds,
            boolean newLocksCommittedOnly,
            float newClinchCarrySpeed,
            float newClinchCarryDecay,
            float newKnockbackSpeed,
            float newKnockbackDecay) {
        this.cooldownSeconds = newCooldownSeconds;
        this.remainingSeconds = 0f;
        this.windupSeconds = newWindupSeconds;
        this.activeSeconds = newActiveSeconds;
        this.reach = newReach;
        this.radius = newRadius;
        this.damage = newDamage;
        this.arcDegrees = newArcDegrees;
        this.lockSeconds = newLockSeconds;
        this.locksCommittedOnly = newLocksCommittedOnly;
        this.clinchCarrySpeed = newClinchCarrySpeed;
        this.clinchCarryDecay = newClinchCarryDecay;
        this.knockbackSpeed = newKnockbackSpeed;
        this.knockbackDecay = newKnockbackDecay;
        this.windupRemaining = 0f;
        this.swingPending = false;
        this.pendingAimX = 0f;
        this.pendingAimY = 0f;
        return this;
    }

    /** True if a fresh swing may be asked for: off cooldown, and not already winding one up. */
    public boolean ready() {
        return remainingSeconds <= 0f && !swingPending;
    }

    /** Remembers where the player was aiming and starts the wind-up. */
    public void beginSwing(float aimX, float aimY) {
        swingPending = true;
        windupRemaining = windupSeconds;
        pendingAimX = aimX;
        pendingAimY = aimY;
        remainingSeconds = cooldownSeconds;
    }

    /** True once a pending swing has finished winding up and the sweep should appear. */
    public boolean releasing() {
        return swingPending && windupRemaining <= 0f;
    }

    /** Clears the pending swing once its sweep has been spawned. */
    public void released() {
        swingPending = false;
        windupRemaining = 0f;
    }

    @Override
    public void reset() {
        cooldownSeconds = 0f;
        remainingSeconds = 0f;
        windupSeconds = 0f;
        activeSeconds = 0f;
        reach = 0f;
        radius = 0f;
        damage = 0f;
        arcDegrees = 0f;
        lockSeconds = 0f;
        locksCommittedOnly = false;
        clinchCarrySpeed = 0f;
        clinchCarryDecay = 0f;
        knockbackSpeed = 0f;
        knockbackDecay = 0f;
        windupRemaining = 0f;
        swingPending = false;
        pendingAimX = 0f;
        pendingAimY = 0f;
    }
}
