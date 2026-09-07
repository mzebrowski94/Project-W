package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * Where an entity was at the start of the current tick, so the renderer can draw it partway between
 * ticks instead of snapping once every simulation step.
 *
 * <p>This is the only component that exists purely so rendering can look right, and it earns its
 * place: the simulation runs at a fixed 60 ticks per second while the window may refresh at 144, and
 * without it every entity visibly steps.
 *
 * <p>It must be seeded at the spawn point, not left at the origin - an entity spawned mid-tick has
 * no history, and a zeroed one would streak in from the corner of the arena on its first frame.
 */
public final class PreviousPosition implements Component, Pool.Poolable {

    public float x;
    public float y;

    /** Sets both coordinates and returns this component, for fluent spawning. */
    public PreviousPosition set(float newX, float newY) {
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
