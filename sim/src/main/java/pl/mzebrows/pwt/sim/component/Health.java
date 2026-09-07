package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/** How much damage an entity can take before it dies. Anything with this can be killed. */
public final class Health implements Component, Pool.Poolable {

    public float max;
    public float current;

    /** Sets the maximum and starts at full, returning this component, for fluent spawning. */
    public Health set(float newMax) {
        this.max = newMax;
        this.current = newMax;
        return this;
    }

    /** Fraction remaining, in {@code [0, 1]}. For anything that displays a health bar. */
    public float fraction() {
        return max <= 0f ? 0f : Math.max(0f, current) / max;
    }

    @Override
    public void reset() {
        max = 0f;
        current = 0f;
    }
}
