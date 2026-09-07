package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * Marker: this entity is finished and will be removed at the end of the tick.
 *
 * <p>The gap between being marked and being removed <em>is</em> the death event (G0004). Any number
 * of reaction systems run in it, and because it is a component rather than a callback it can be
 * inspected in a debugger, asserted in a test and replayed.
 *
 * <p>Carries no killer reference yet. The agreed design has one, for reactions like "an enemy
 * changes after killing a unit", but nothing reads it today and a pooled component holding an entity
 * reference is a leak waiting to happen. Add it with the first reaction that needs it.
 */
public final class Dead implements Component, Pool.Poolable {

    @Override
    public void reset() {
        // Nothing to clear - this component is a marker.
    }
}
