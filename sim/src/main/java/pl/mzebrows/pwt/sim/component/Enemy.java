package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * A unit that chases the commander, or anything else the squad is there to shoot.
 *
 * <p>Systems select on it through a {@code Family} rather than branching on what an entity is, which
 * is the whole reason markers are preferred to a kind enum here. Its one number is how it reads as a
 * target, so a trooper's choice never asks what kind of thing it is.
 */
public final class Enemy implements Component, Pool.Poolable {

    /**
     * How much further off than his sight a trooper picks this out from, and holds it, in world units:
     * a nest's {@code sim.nest.standsOut}. Zero, for everything that walks, means no further.
     */
    public float standsOut;

    /** Sets how far it stands out, and returns this component, for fluent spawning. */
    public Enemy standingOut(float newStandsOut) {
        this.standsOut = newStandsOut;
        return this;
    }

    @Override
    public void reset() {
        standsOut = 0f;
    }
}
