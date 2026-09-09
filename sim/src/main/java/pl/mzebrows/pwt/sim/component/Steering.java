package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * Where a unit wants to be going this tick, in world units per second, before anything decides
 * whether it may.
 *
 * <p>This is the seam between deciding and moving. Everything that drives a unit - navigating toward
 * a target, wandering, steering around an obstacle - writes here, and {@code DriveSystem} commits it
 * to {@link Velocity} once, at the end. Systems used to write velocity directly, which worked while
 * there were two of them and the later one simply overwrote the earlier: with more, "last writer
 * wins" stops being readable, and a system that avoids obstacles cannot do its job at all unless it
 * can see what the unit was intending before it adjusts it.
 *
 * <p><strong>A desired velocity is spent on the tick it is written.</strong> {@code DriveSystem}
 * clears it after committing it, so a unit nothing spoke for this tick asks for nothing and stops,
 * rather than coasting on a decision made when the world looked different.
 *
 * <p>It is a velocity rather than a force or an acceleration. Top-down arcade movement is direct -
 * pillar 1 - and a unit that had to accelerate up to speed would read as sliding on ice.
 */
public final class Steering implements Component, Pool.Poolable {

    public float desiredX;
    public float desiredY;

    /** Asks to move this way this tick, and returns this component, for fluent spawning. */
    public Steering set(float newDesiredX, float newDesiredY) {
        this.desiredX = newDesiredX;
        this.desiredY = newDesiredY;
        return this;
    }

    /** Asks for nothing, which is how a unit stands still. */
    public void clear() {
        desiredX = 0f;
        desiredY = 0f;
    }

    @Override
    public void reset() {
        clear();
    }
}
