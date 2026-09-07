package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/** Where an entity is, in world units. */
public final class Position implements Component, Pool.Poolable {

    public float x;
    public float y;

    /** Sets both coordinates and returns this component, for fluent spawning. */
    public Position set(float newX, float newY) {
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
