package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * A pending respawn, counting down once it is paid for.
 *
 * <p>An entity of its own, because the unit it brings back no longer exists to hold it. A ticket is
 * paid for with a man from the reserve (G0055): until one is there it waits and does not count down,
 * and where he comes back is decided when he does - the rally point of that tick.
 */
public final class Respawning implements Component, Pool.Poolable {

    public int remainingTicks;

    /** Whether a man has been taken from the reserve for this one. Until then the countdown waits. */
    public boolean paid;
    /** How long the shield he comes back behind lasts, in ticks (G0053). */
    public int shieldTicks;

    /**
     * The player slot the man who died was sitting in, so he comes back as the same player.
     *
     * <p>On the ticket: the commander who held it no longer exists by then.
     */
    public int slot;

    /** Sets the pending respawn and returns this component, for fluent spawning. */
    public Respawning set(int newRemainingTicks, int newShieldTicks, int newSlot) {
        this.remainingTicks = newRemainingTicks;
        this.paid = false;
        this.shieldTicks = newShieldTicks;
        this.slot = newSlot;
        return this;
    }

    @Override
    public void reset() {
        remainingTicks = 0;
        paid = false;
        shieldTicks = 0;
        slot = 0;
    }
}
