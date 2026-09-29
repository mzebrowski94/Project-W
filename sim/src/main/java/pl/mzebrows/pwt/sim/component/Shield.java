package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * Something that takes the next hit of any harm instead of the man carrying it, and breaks (G0053).
 *
 * <p>One hit, whatever it was - a wound, a blast that would put him down, claws that would kill him, a
 * grab, a stun - and it is gone; the shove of that hit still lands. It never stacks: raising one that is
 * already up only keeps the longer of the two clocks. A shield that runs out on its own - the one a
 * commander comes back behind - also counts down, in {@code CooldownSystem}; one raised until hit does
 * not.
 *
 * <p>Carried permanently by every man and read through {@link #up}, rather than added and removed:
 * adding one mid-tick mutates the families being iterated.
 */
public final class Shield implements Component, Pool.Poolable {

    /** Whether it will take the next hit. */
    public boolean up;

    /** Whether it also runs out on its own. */
    public boolean timed;

    /** Ticks left, while it is timed. */
    public int remainingTicks;

    /** How long it had when it was raised, while it is timed; for drawing it running down. */
    public int fromTicks;

    /** Raises one that runs out after this many ticks or at the first hit, whichever is first. */
    public void raiseFor(int ticks) {
        if (ticks <= 0) {
            return;
        }
        if (up && !timed) {
            return;
        }
        up = true;
        timed = true;
        if (ticks > remainingTicks) {
            remainingTicks = ticks;
            fromTicks = ticks;
        }
    }

    /** Raises one that lasts until it is hit. */
    public void raise() {
        up = true;
        timed = false;
        remainingTicks = 0;
        fromTicks = 0;
    }

    /** Takes a hit: the shield is gone. */
    public void shatter() {
        up = false;
        timed = false;
        remainingTicks = 0;
        fromTicks = 0;
    }

    /** How much of it is left, in {@code [0, 1]}: one for a shield that lasts until hit, zero when down. */
    public float fraction() {
        if (!up) {
            return 0f;
        }
        if (!timed || fromTicks <= 0) {
            return 1f;
        }
        return Math.max(0f, remainingTicks / (float) fromTicks);
    }

    @Override
    public void reset() {
        up = false;
        timed = false;
        remainingTicks = 0;
        fromTicks = 0;
    }
}
