package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * A sentry (G0129): a small standing gun on the players' side that nobody crews and nobody hauls. Where it points, how
 * fast it comes round - a turret's turn, at a rate - how far it shoots, how long it has left to stand, and what its
 * going off stuns.
 *
 * <p>It is no turret (G0024: a place a man gets on) and no emplacement (G0113: the aliens' uncrewed shooter, never a
 * target). The aliens hunt it as they hunt a man, from the moment it stands: it has health and is on his side.
 * <strong>It goes off by dying</strong> (G0018) - destroyed or out of time, one path: its blast is its
 * {@code BlastsOnDeath}, and the stun is read here while its body is still on the field.
 *
 * <p>The aim is a unit vector, as a turret's is, so turning it needs no transcendental (pillar 6).
 */
public final class Sentry implements Component, Pool.Poolable {

    /** The slot whose commander set it down: whose tally its kills count for, and whose upgrades reach its gun. */
    public int slot = Tally.NOBODY;

    /** Where the gun points, as a unit vector. */
    public float aimX = 1f;

    public float aimY;

    /** How fast it comes round with the trigger off, as the fraction of the angle left it covers per second. */
    public float turnRate;

    /** What is left of that rate while it fires, in {@code (0, 1]}. */
    public float firingTurnFactor;

    /** How far it will shoot, in world units. */
    public float range;

    /** How long it stands, and how much of that is left, in ticks. At none it goes off. */
    public int lifeTicks;

    public int remainingTicks;

    /** How far round it the aliens are stunned as it goes off, in world units, and for how long, in ticks. */
    public float stunRadius;

    public int stunTicks;

    /** Sets the sentry and returns this component, for fluent spawning. */
    public Sentry set(
            int newSlot,
            float newTurnRate,
            float newFiringTurnFactor,
            float newRange,
            int newLifeTicks,
            float newStunRadius,
            int newStunTicks) {
        this.slot = newSlot;
        this.aimX = 1f;
        this.aimY = 0f;
        this.turnRate = newTurnRate;
        this.firingTurnFactor = newFiringTurnFactor;
        this.range = newRange;
        this.lifeTicks = newLifeTicks;
        this.remainingTicks = newLifeTicks;
        this.stunRadius = newStunRadius;
        this.stunTicks = newStunTicks;
        return this;
    }

    /** Points the gun along a direction that is already a unit vector. */
    public void aim(float x, float y) {
        this.aimX = x;
        this.aimY = y;
    }

    /** How fast it may come round this tick: firing costs it some of its tracking, as a turret's does. */
    public float turnRateFor(boolean firing) {
        return firing ? turnRate * firingTurnFactor : turnRate;
    }

    /** How much of its life is left, in {@code [0, 1]}: what its time left is shown by (pillar 5). */
    public float lifeLeft() {
        return lifeTicks <= 0 ? 0f : Math.max(0, remainingTicks) / (float) lifeTicks;
    }

    @Override
    public void reset() {
        slot = Tally.NOBODY;
        aimX = 1f;
        aimY = 0f;
        turnRate = 0f;
        firingTurnFactor = 0f;
        range = 0f;
        lifeTicks = 0;
        remainingTicks = 0;
        stunRadius = 0f;
        stunTicks = 0;
    }
}
