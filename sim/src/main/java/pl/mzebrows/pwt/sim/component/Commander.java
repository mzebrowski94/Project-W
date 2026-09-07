package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * Marker: The player-controlled unit. There is exactly one today; whether that stays true is an open
 * question in docs/design/README.md.
 *
 * <p>Carries no data on purpose. Systems select on it through a {@code Family} rather than branching
 * on what an entity is, which is the whole reason markers are preferred to a kind enum here.
 */
public final class Commander implements Component, Pool.Poolable {

    @Override
    public void reset() {
        // Nothing to clear - this component is a marker.
    }
}
