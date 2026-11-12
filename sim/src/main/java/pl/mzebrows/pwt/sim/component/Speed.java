package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * How fast an entity moves under its own power, in world units per second.
 *
 * <p>Distinct from {@link Velocity}, which is how fast it is moving right now. Systems read this and
 * write that. Keeping it on the entity rather than in a system's config is what lets a trooper, a
 * commander and an enemy share one movement system without any of them being a special case.
 */
public final class Speed implements Component, Pool.Poolable {

    public float value;

    /** Sets the speed and returns this component, for fluent spawning. */
    public Speed set(float newValue) {
        this.value = newValue;
        return this;
    }

    /**
     * Whether it never walks: <strong>rooted</strong> (G0048) - scenery to the solver, which pushes whatever it meets out
     * of its way. The one reading of a strain's speed as that, for the solver's {@code Collider.movable}.
     */
    public boolean rooted() {
        return value <= 0f;
    }

    @Override
    public void reset() {
        value = 0f;
    }
}
