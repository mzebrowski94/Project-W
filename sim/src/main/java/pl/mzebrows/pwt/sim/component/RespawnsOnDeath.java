package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * A death reaction: this entity comes back, after a delay, somewhere specific.
 *
 * <p>Data on the entity rather than a rule in a system, so a unit that does not respawn simply does
 * not carry this - no flag, no special case in whatever kills it (G0004).
 */
public final class RespawnsOnDeath implements Component, Pool.Poolable {

    public float delaySeconds;
    public float x;
    public float y;

    /** How long it is invulnerable on arrival, so it does not die again inside the crowd that killed it. */
    public float invulnerableSeconds;

    /** Sets the reaction and returns this component, for fluent spawning. */
    public RespawnsOnDeath set(float newDelaySeconds, float newX, float newY, float newInvulnerableSeconds) {
        this.delaySeconds = newDelaySeconds;
        this.x = newX;
        this.y = newY;
        this.invulnerableSeconds = newInvulnerableSeconds;
        return this;
    }

    @Override
    public void reset() {
        delaySeconds = 0f;
        x = 0f;
        y = 0f;
        invulnerableSeconds = 0f;
    }
}
