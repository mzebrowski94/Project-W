package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * A pending respawn, counting down.
 *
 * <p>Lives on an entity of its own - no position, no body, nothing to draw - because the unit it
 * will bring back no longer exists to hold it. That keeps the dead entity genuinely dead rather than
 * lingering in a half-alive state that every family would then have to exclude.
 */
public final class Respawning implements Component, Pool.Poolable {

    public int remainingTicks;
    public float x;
    public float y;
    public int invulnerableTicks;

    /**
     * The player slot the man who died was sitting in, so he comes back as the same player.
     *
     * <p>On the ticket rather than looked up on arrival, because the commander who held it no longer
     * exists by then - which is the same reason the countdown is here.
     */
    public int slot;

    /** Sets the pending respawn and returns this component, for fluent spawning. */
    public Respawning set(int newRemainingTicks, float newX, float newY, int newInvulnerableTicks, int newSlot) {
        this.remainingTicks = newRemainingTicks;
        this.x = newX;
        this.y = newY;
        this.invulnerableTicks = newInvulnerableTicks;
        this.slot = newSlot;
        return this;
    }

    @Override
    public void reset() {
        remainingTicks = 0;
        x = 0f;
        y = 0f;
        invulnerableTicks = 0;
        slot = 0;
    }
}
