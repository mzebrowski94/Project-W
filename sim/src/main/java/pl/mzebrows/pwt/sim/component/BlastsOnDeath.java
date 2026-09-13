package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * A death reaction: this entity leaves a blast where it fell.
 *
 * <p><strong>Everything in the game explodes by dying</strong>, and this is the whole of the rule. A
 * grenade that has run out of flight dies; a barrel that is shot, swept or bitten into dies; and both
 * arrive at the same explosion through the same reaction, so there is one path to a blast rather than
 * one per thing that makes one.
 *
 * <p>Data on the entity rather than a rule in a system, exactly as {@link RespawnsOnDeath} is, so
 * whatever killed it needs to know nothing about explosions (G0004).
 */
public final class BlastsOnDeath implements Component, Pool.Poolable {

    /** How wide the blast it leaves is, in world units. Also the circle the player will see. */
    public float radius;

    /** Sets the reaction and returns this component, for fluent spawning. */
    public BlastsOnDeath set(float newRadius) {
        this.radius = newRadius;
        return this;
    }

    @Override
    public void reset() {
        radius = 0f;
    }
}
