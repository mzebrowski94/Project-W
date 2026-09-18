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

    public float remainingSeconds;
    public float x;
    public float y;
    public float invulnerableSeconds;

    /**
     * The player slot the man who died was sitting in, so he comes back as the same player.
     *
     * <p>On the ticket rather than looked up on arrival, because the commander who held it no longer
     * exists by then - which is the same reason the countdown is here.
     */
    public int slot;

    /** Sets the pending respawn and returns this component, for fluent spawning. */
    public Respawning set(
            float newRemainingSeconds, float newX, float newY, float newInvulnerableSeconds, int newSlot) {
        this.remainingSeconds = newRemainingSeconds;
        this.x = newX;
        this.y = newY;
        this.invulnerableSeconds = newInvulnerableSeconds;
        this.slot = newSlot;
        return this;
    }

    @Override
    public void reset() {
        remainingSeconds = 0f;
        x = 0f;
        y = 0f;
        invulnerableSeconds = 0f;
        slot = 0;
    }
}
