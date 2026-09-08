package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * Marker: a heavy unit that chases the commander and survives a hand strike.
 *
 * <p>A brute carries {@link Enemy} as well, so everything that already works on enemies - chasing,
 * leaping, taking a bullet - works on it with no change. This marker exists only for the things that
 * must tell the two apart: what to spawn, and what colour to draw.
 */
public final class Brute implements Component, Pool.Poolable {

    @Override
    public void reset() {
        // Nothing to clear - this component is a marker.
    }
}
