package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * What an entity is, and what it is currently doing, for the renderer to look at. The renderer reads
 * this and never asks what components an entity has or what class it is (G0003), so swapping
 * placeholder shapes for sprites touches one class in the client and nothing here.
 *
 * <p>{@link Look} names roles and {@link State} names situations; neither names a colour or a shape.
 * The mapping to anything visible lives in the client module - the simulation holds no visual data
 * and does not know a renderer exists.
 *
 * <p>The state half exists because of pillar 5: a unit winding up to leap, a unit in the air and a
 * unit held still by a blow can all do different things to the player, so they must all look
 * different. A tell nobody can see is not a tell.
 */
public final class Appearance implements Component, Pool.Poolable {

    /** The role a renderer draws. */
    public enum Look {
        /** Nothing meaningful set - a pooled component that was reset and not yet reused. */
        NONE,
        COMMANDER,
        ENEMY,
        BRUTE,
        PROJECTILE,
        STRIKE,
        OBSTACLE
    }

    /** What the entity is doing, where that changes how it must read at a glance. */
    public enum State {
        NORMAL,
        /** Committed to a leap and standing still. The window the player has to answer it. */
        WINDING_UP,
        AIRBORNE,
        /**
         * Locked together with whatever it just traded blows with. Both parties to a landed lock are
         * in this state for its whole duration, winner and loser alike, and that is the point: for
         * that beat the player cannot yet tell which of them is getting back up. The grapple animation
         * will play here.
         */
        GRAPPLING,
        /** Shoved and briefly unable to act, with nothing holding the other party. */
        STAGGERED,
        /** Out of health, body still on the field. */
        DYING
    }

    public Look look = Look.NONE;
    public State state = State.NORMAL;

    /** Sets the look and returns this component, for fluent spawning. */
    public Appearance set(Look newLook) {
        this.look = newLook;
        this.state = State.NORMAL;
        return this;
    }

    @Override
    public void reset() {
        look = Look.NONE;
        state = State.NORMAL;
    }
}
