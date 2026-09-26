package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * Where a unit wants to be going this tick, in world units per second, before anything decides
 * whether it may.
 *
 * <p>The seam between deciding and moving: every driver writes here and {@code DriveSystem} commits
 * it to {@link Velocity} once (G0010). <strong>Spent on the tick it is written</strong> - a unit
 * nothing spoke for stops. A velocity, not a force: movement is direct (pillar 1).
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
