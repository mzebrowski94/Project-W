package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.sim.def.Look;

/**
 * What an entity is, and what it is currently doing, for the renderer to look at. The renderer reads
 * this and never asks what components an entity has or what class it is (G0003), so swapping
 * placeholder shapes for sprites touches one class in the client and nothing here.
 *
 * <p>{@link Look} names roles and {@link State} names situations; neither names a colour or a shape.
 * The mapping to anything visible lives in the client module - the simulation holds no visual data
 * and does not know a renderer exists.
 *
 * <p>The two halves live in different places on purpose. A look is part of what an entity <em>is</em>,
 * so it belongs with the definitions and is written only by whatever writes the entity's strain; a
 * state is what it is <em>doing</em>, so it is runtime state written by {@code AppearanceSystem} and
 * by nothing else.
 *
 * <p>The state half exists because of pillar 5: a unit winding up to leap, a unit in the air and a
 * unit held still by a blow can all do different things to the player, so they must all look
 * different. A tell nobody can see is not a tell.
 */
public final class Appearance implements Component, Pool.Poolable {

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

    /**
     * How far along the entity is towards something it is filling up for, in {@code [0, 1]}.
     *
     * <p>Pillar 5 in one float. An enemy two mouthfuls into a three-mouthful evolution is tracking a
     * number, and a number the game does not show is a number the player is guessing at. This is the
     * general channel for that - a weapon's remaining charge or an objective's progress would ride
     * here too - so the renderer learns one way to draw "filling up" rather than one per mechanic.
     *
     * <p>Zero means there is nothing to show, which is what almost everything reports almost always.
     */
    public float charge;

    /** Sets the look and returns this component, for fluent spawning. */
    public Appearance set(Look newLook) {
        this.look = newLook;
        this.state = State.NORMAL;
        this.charge = 0f;
        return this;
    }

    @Override
    public void reset() {
        look = Look.NONE;
        state = State.NORMAL;
        charge = 0f;
    }
}
