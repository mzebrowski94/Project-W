package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * A pending respawn, counting down.
 *
 * <p>An entity of its own, because the unit it brings back no longer exists to hold it.
 */
public final class Respawning implements Component, Pool.Poolable {

    public int remainingTicks;
    public float x;
    public float y;
    /** How long the shield he comes back behind lasts, in ticks (G0053). */
    public int shieldTicks;

    /**
     * The player slot the man who died was sitting in, so he comes back as the same player.
     *
     * <p>On the ticket: the commander who held it no longer exists by then.
     */
    public int slot;

    /** Sets the pending respawn and returns this component, for fluent spawning. */
    public Respawning set(int newRemainingTicks, float newX, float newY, int newShieldTicks, int newSlot) {
        this.remainingTicks = newRemainingTicks;
        this.x = newX;
        this.y = newY;
        this.shieldTicks = newShieldTicks;
        this.slot = newSlot;
        return this;
    }

    @Override
    public void reset() {
        remainingTicks = 0;
        x = 0f;
        y = 0f;
        shieldTicks = 0;
        slot = 0;
    }
}
