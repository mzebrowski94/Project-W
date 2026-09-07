package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/** An entity's circular extent, used for collision and for placeholder rendering. */
public final class Body implements Component, Pool.Poolable {

    public float radius;

    /** Sets the radius and returns this component, for fluent spawning. */
    public Body set(float newRadius) {
        this.radius = newRadius;
        return this;
    }

    @Override
    public void reset() {
        radius = 0f;
    }
}
