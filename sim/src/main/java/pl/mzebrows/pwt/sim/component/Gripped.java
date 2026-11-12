package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * Whether this man is in something's jaws, and whose (G0045).
 *
 * <p>Carried by everyone a leap can take hold of - the commander and every trooper - rather than added
 * when one does, because adding a component mid-tick mutates the families being iterated. On his own
 * feet is a value, and it is what almost everyone reports almost always.
 *
 * <p>The other half of the pair on {@link Grip}, named by a handle for the same reason. While he is
 * held still he drives nothing and fires nothing; the one thing he may still do is swing, and that is the
 * whole of how a man gets himself out. A thing that only clings to him (G0083) leaves him everything.
 */
public final class Gripped implements Component, Pool.Poolable {

    /** The handle of what has hold of him, or {@link Handle#NOBODY}. */
    public long by = Handle.NOBODY;

    /**
     * Whether what has hold of him holds him still - in its jaws - or only <strong>clings</strong> to him, leaving him
     * his legs and his gun (G0083). Meaningless unless {@link #held()}.
     */
    public boolean pinned;

    /**
     * How much of his time he has left, in {@code [0, 1]}, published each tick by {@code GripSystem} from
     * the clock on the creature holding him, so it can be drawn on him (pillar 5).
     */
    public float fraction;

    /** True while something has hold of him. */
    public boolean held() {
        return by != Handle.NOBODY;
    }

    /** Puts him back on his own feet. */
    public void release() {
        by = Handle.NOBODY;
        pinned = false;
        fraction = 0f;
    }

    @Override
    public void reset() {
        by = Handle.NOBODY;
        pinned = false;
        fraction = 0f;
    }
}
