package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * Whether this man is aboard something, and which thing.
 *
 * <p>Carried by everyone who could ever get on a gun - the commander and every trooper - rather than
 * added when one does, because adding a component mid-tick mutates the families being iterated and
 * silently skips entities. On foot is a value, and it is what almost everyone reports almost always.
 *
 * <p><strong>The station is named by a number, not by a reference</strong> (G0010), for the reason
 * {@link Drag} names its load by one: a man sits on a gun for many seconds, and a pooled component
 * holding an {@code Entity} that long can outlive what it pointed at and come back aimed at whatever
 * was built from the recycled instance. A station's id is issued once, at its spawn, from a counter
 * that never goes back, so the id of a turret that has been blown up can never be handed to another
 * one - and a man whose gun went out from under him is simply aboard nothing, which
 * {@code TurretSystem} answers by putting him back on his feet.
 *
 * <p>It is the rider's half of the pair that is the truth. A station publishes whether it is crewed
 * for the renderer and for anything deciding whether to walk over to it, but that flag is recomputed
 * from the riders every tick rather than trusted, so a crew that died cannot leave a gun looking
 * occupied for the rest of the level.
 */
public final class Crew implements Component, Pool.Poolable {

    /** Not aboard anything. Zero rather than a negative, so a cleared component reads as on foot. */
    public static final long ON_FOOT = Handle.NOBODY;

    /** The id of the station this man is on, or {@link #ON_FOOT}. */
    public long station = ON_FOOT;

    /**
     * How long he has sat on it with nothing in its reach, in ticks.
     *
     * <p>His patience rather than the gun's, which is why it lives here: two men on two guns run
     * their own clocks, and getting off resets it because what it measures is this sitting.
     */
    public int quietTicks;

    /** True while he is on something rather than standing on the ground. */
    public boolean aboard() {
        return station != ON_FOOT;
    }

    @Override
    public void reset() {
        station = ON_FOOT;
        quietTicks = 0;
    }
}
