package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * Marker: a player-controlled unit, and which player controls it.
 *
 * <p>There may be up to {@code Players.MAX} of these. The {@link #slot} is the whole of what tells
 * them apart: one input record per slot arrives each tick, one commander reads each, and a commander
 * killed and brought back is the same player again because the slot comes back with him.
 *
 * <p>It is a number here rather than a roster somewhere because a roster would be a table of
 * entities held across ticks, and a kept entity reference in a pooled engine comes back aimed at
 * something else (G0016). Systems still select on the marker through a {@code Family} and branch on
 * nothing; the slot is read only where two commanders have to be put in a fixed order.
 */
public final class Commander implements Component, Pool.Poolable {

    /** Which player drives this one. Zero is the first player, which is who a lone commander is. */
    public int slot;

    /** Seats this commander and returns the component, for fluent spawning. */
    public Commander set(int newSlot) {
        this.slot = newSlot;
        return this;
    }

    @Override
    public void reset() {
        slot = 0;
    }
}
