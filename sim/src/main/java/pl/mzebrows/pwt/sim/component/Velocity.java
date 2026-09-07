package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/** How fast an entity moves, in world units per second. */
public final class Velocity implements Component, Pool.Poolable {

    public float x;
    public float y;

    /** Sets both components and returns this component, for fluent spawning. */
    public Velocity set(float newX, float newY) {
        this.x = newX;
        this.y = newY;
        return this;
    }

    @Override
    public void reset() {
        x = 0f;
        y = 0f;
    }
}
