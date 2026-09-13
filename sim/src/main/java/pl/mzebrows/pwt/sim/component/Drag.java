package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * What the commander has hold of, and which way it is from him.
 *
 * <p><strong>The load is named by a number, not by a reference.</strong> A drag lasts seconds, and a
 * pooled component that held an {@code Entity} that long could outlive what it pointed at and come
 * back aimed at whatever was built from the recycled instance (G0010) - the failure reads as
 * dragging a man who is standing somewhere else. So both ends carry a matching {@link #load} id and
 * {@code DragSystem} re-finds the pair each tick, over a family that is almost always empty. A
 * recycled {@link Dragged} resets its id to zero, so a stale id can never match again; that is the
 * whole safety argument, and it is why the id is issued rather than derived.
 *
 * <p>{@link #towardLoadX}/{@link #towardLoadY} is published for {@code CommanderControlSystem},
 * which owns his velocity and is therefore the only thing that may constrain it. Two systems writing
 * one velocity is how a unit ends up moving in a direction neither of them asked for.
 */
public final class Drag implements Component, Pool.Poolable {

    /** Nothing is held at this id. Zero rather than a negative so a cleared component reads as empty. */
    public static final long NOTHING = 0L;

    /** Which load he has hold of, or {@link #NOTHING}. */
    public long load = NOTHING;

    /** Whether the key was held last tick, so a grab happens on the press rather than every tick. */
    public boolean wasHeld;

    /** Unit direction from him to the load. Meaningless unless something is held. */
    public float towardLoadX;

    public float towardLoadY;

    /** How much of his walking pace he keeps while hauling, in {@code (0, 1]}. Configuration. */
    public float speedFactor;

    /** How much of a sideways step survives, in {@code [0, 1]}. Configuration. */
    public float lateralFactor;

    /** Clearance between the two bodies while he hauls, in world units. Configuration. */
    public float gap;

    /** Sets how hauling feels and returns this component, for fluent spawning. */
    public Drag set(float newSpeedFactor, float newLateralFactor, float newGap) {
        this.speedFactor = newSpeedFactor;
        this.lateralFactor = newLateralFactor;
        this.gap = newGap;
        this.load = NOTHING;
        this.wasHeld = false;
        return this;
    }

    /** True while he has hold of something. */
    public boolean active() {
        return load != NOTHING;
    }

    /** Lets go, keeping the configuration. */
    public void release() {
        load = NOTHING;
        towardLoadX = 0f;
        towardLoadY = 0f;
    }

    @Override
    public void reset() {
        load = NOTHING;
        wasHeld = false;
        towardLoadX = 0f;
        towardLoadY = 0f;
        speedFactor = 0f;
        lateralFactor = 0f;
        gap = 0f;
    }
}
