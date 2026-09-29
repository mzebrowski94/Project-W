package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/** How much damage an entity can take before it dies. Anything with this can be killed. */
public final class Health implements Component, Pool.Poolable {

    /** Whole hit points (G0054): no fraction is ever stored here. */
    public int max;

    public int current;

    /** Sets the maximum and starts at full, returning this component, for fluent spawning. */
    public Health set(int newMax) {
        this.max = newMax;
        this.current = newMax;
        return this;
    }

    /** Fraction remaining, in {@code [0, 1]}. For anything that displays a health bar. */
    public float fraction() {
        return max <= 0 ? 0f : Math.max(0, current) / (float) max;
    }

    @Override
    public void reset() {
        max = 0;
        current = 0;
    }
}
