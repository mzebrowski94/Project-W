package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * Ground the squad takes, whatever kind of site it is (G0110): a control point today, a mainframe or a relic
 * tomorrow. What a taking earns - the draft, the goal line's count - counts these, so a new kind of site is counted
 * by carrying one. Set taken only by {@code Sites.taken}, the one step a taking goes through.
 */
public final class Site implements Component, Pool.Poolable {

    /** Whether it has been taken. One way. */
    public boolean taken;

    @Override
    public void reset() {
        taken = false;
    }
}
