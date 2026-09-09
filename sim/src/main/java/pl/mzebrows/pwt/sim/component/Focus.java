package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * Marker: the view should keep this entity in shot.
 *
 * <p>The simulation says <em>who</em> matters and stops there. Where the camera actually goes, how
 * fast it gets there and how far out it sits are the client's business entirely - the simulation does
 * not know a camera exists, and a rendering setting must never change what it does.
 *
 * <p>It is a component rather than "find the commander" in the client for one reason: local co-op.
 * Two commanders means two of these and a camera that frames both, with nothing in the client
 * changed and nothing here branching on what an entity is. It also leaves room for the view to be
 * drawn to something that is not a player at all - an objective, a scripted moment - without
 * inventing a second mechanism for it.
 */
public final class Focus implements Component, Pool.Poolable {

    @Override
    public void reset() {
        // Nothing to clear - this component is a marker.
    }
}
