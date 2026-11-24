package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * A unit that chases the commander, or anything else the squad is there to shoot.
 *
 * <p>Systems select on it through a {@code Family} rather than branching on what an entity is - never a
 * kind enum. Its one number is how it reads as a target, so a trooper's choice never asks what kind of
 * thing it is.
 */
public final class Enemy implements Component, Pool.Poolable {

    /**
     * How much further off than his sight a trooper picks this out from, and holds it, in world units:
     * a nest's {@code sim.nest.standsOut}. Zero, for everything that walks, means no further.
     */
    public float standsOut;

    /**
     * Whether a trooper weighs it as a threat whatever it is doing - at its own distance, as he would something
     * coming for somebody: a nest's {@code sim.nest.threat}. False, for everything that walks, is a threat only
     * while it hunts.
     */
    public boolean threat;

    /**
     * Whether a man who has picked it out keeps on it until it dies or something is inside his all-round range:
     * a nest's {@code sim.nest.kept}. False, for everything that walks, is a target chosen afresh every tick.
     */
    public boolean kept;

    /**
     * Whether a lair raised it (G0122): what the squad assaults once it has risen, its nests first and then the rest.
     * Set by the lair that puts it down; false for everything else.
     */
    public boolean raised;

    /** Marks it raised by a lair, and returns this component. */
    public Enemy raisedByALair() {
        this.raised = true;
        return this;
    }

    /** Sets how far it stands out, and returns this component, for fluent spawning. */
    public Enemy standingOut(float newStandsOut) {
        this.standsOut = newStandsOut;
        return this;
    }

    /** Sets whether it is a threat whatever it does and whether a man keeps on it, and returns this component. */
    public Enemy weighed(boolean newThreat, boolean newKept) {
        this.threat = newThreat;
        this.kept = newKept;
        return this;
    }

    @Override
    public void reset() {
        standsOut = 0f;
        threat = false;
        kept = false;
        raised = false;
    }
}
