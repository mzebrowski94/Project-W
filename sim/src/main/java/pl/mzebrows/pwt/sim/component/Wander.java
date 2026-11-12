package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * A unit's patch of ground, and where inside it it is currently headed.
 *
 * <p>The home point is written at the spawn, dragged along while the unit hunts, and drifted towards each
 * spot it arrives at (G0086) - evolving rewrites every other number here from the new strain's definition but
 * leaves home alone, because where a thing lives is a fact about that individual and not about what it has
 * turned into.
 *
 * <p>The phases are a two-state machine of the same shape as {@link Leap}'s, and for the same reason:
 * state that changes over time is a field on a component the entity already carries, never a
 * component added and removed, which would mutate the families being iterated.
 */
public final class Wander implements Component, Pool.Poolable {

    /** What an idle unit is doing right now. */
    public enum Phase {
        /** Standing still, waiting out a pause before it picks somewhere new. */
        RESTING,
        /** Walking to the spot it picked. */
        WALKING
    }

    public boolean enabled;
    public float radius;
    public float speedFactor;
    public float arriveRadius;
    public float pauseMinSeconds;
    public float pauseMaxSeconds;
    public int waypointTimeoutTicks;

    /** How far home moves towards each spot it arrives at, in world units; zero means it stays put. */
    public float driftPerStop;

    /** Where it was spawned: the centre of its patch, and where losing the commander takes it back to. */
    public float homeX;

    public float homeY;

    /** The spot inside the patch it is walking to. Meaningless while {@link Phase#RESTING}. */
    public float targetX;

    public float targetY;

    public Phase phase = Phase.RESTING;

    /**
     * Counts down the current phase: the pause while resting, the patience while walking. Decremented
     * by {@code CooldownSystem} with every other timer.
     */
    public int remainingTicks;

    /** Sets what kind of idling this is, and returns this component, for fluent spawning. */
    public Wander set(
            boolean newEnabled,
            float newRadius,
            float newSpeedFactor,
            float newArriveRadius,
            float newPauseMinSeconds,
            float newPauseMaxSeconds,
            int newWaypointTimeoutTicks,
            float newDriftPerStop) {
        this.enabled = newEnabled;
        this.radius = newRadius;
        this.speedFactor = newSpeedFactor;
        this.arriveRadius = newArriveRadius;
        this.pauseMinSeconds = newPauseMinSeconds;
        this.pauseMaxSeconds = newPauseMaxSeconds;
        this.waypointTimeoutTicks = newWaypointTimeoutTicks;
        this.driftPerStop = newDriftPerStop;
        return this;
    }

    /** Fixes the centre of the patch. Called once, at the spawn, and not again when a unit evolves. */
    public Wander anchor(float x, float y) {
        this.homeX = x;
        this.homeY = y;
        return this;
    }

    /** Sends it somewhere, and starts the clock on how long it will keep trying to get there. */
    public void walkTo(float x, float y) {
        this.targetX = x;
        this.targetY = y;
        this.phase = Phase.WALKING;
        this.remainingTicks = waypointTimeoutTicks;
    }

    /** Stops it where it is for a moment. */
    public void rest(int ticks) {
        this.phase = Phase.RESTING;
        this.remainingTicks = ticks;
    }

    @Override
    public void reset() {
        enabled = false;
        radius = 0f;
        speedFactor = 0f;
        arriveRadius = 0f;
        pauseMinSeconds = 0f;
        pauseMaxSeconds = 0f;
        waypointTimeoutTicks = 0;
        driftPerStop = 0f;
        homeX = 0f;
        homeY = 0f;
        targetX = 0f;
        targetY = 0f;
        phase = Phase.RESTING;
        remainingTicks = 0;
    }
}
