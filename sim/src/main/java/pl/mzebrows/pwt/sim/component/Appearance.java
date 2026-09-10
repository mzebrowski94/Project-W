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

    /** Below this squared length a direction is noise rather than a heading. */
    private static final float FACING_EPSILON = 1e-4f;

    /** What the entity is doing, where that changes how it must read at a glance. */
    public enum State {
        NORMAL,
        /**
         * On the field but not hunting anybody - it has not noticed the commander, or has lost him.
         * Pillar 5: which of the things in front of you are already coming for you is exactly the sort
         * of thing a player must be able to read, and a pack that looks identical asleep and awake
         * would make a detection range something the player could only learn by dying to it.
         */
        UNAWARE,
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

    /**
     * Which way the entity is pointing, as a unit vector; {@code (0, 0)} means it has no facing worth
     * showing and the renderer draws none.
     *
     * <p>A vector rather than an angle because an angle would have to be written with {@code atan2}
     * and read back with trigonometry, and {@code ArchitectureTest} bans both here - they are only
     * specified to within an ulp and may differ between JVMs, which pillar 6 does not allow. The
     * renderer wants a direction anyway, so nothing is lost.
     *
     * <p>Pillar 5, and load-bearing rather than decorative: a unit that engages only what is in front
     * of it is unreadable unless the player can see where its front is (G0015).
     */
    public float facingX;

    public float facingY;

    /**
     * Whether this entity is acting on an order rather than on its own judgement (3.f).
     *
     * <p>Pillar 5 applied to command: a player who cannot see which of his men heard him has no way
     * to learn how far his voice carries, and would be guessing every time he gave an order. Published
     * as a plain flag so the client decides what it looks like (G0003).
     */
    public boolean ordered;

    /** Sets the look and returns this component, for fluent spawning. */
    public Appearance set(Look newLook) {
        this.look = newLook;
        this.state = State.NORMAL;
        this.charge = 0f;
        return this;
    }

    /**
     * Points the entity along the given direction, which need not be normalised, and returns this
     * component for fluent spawning.
     *
     * <p>A direction too short to mean anything leaves the facing alone, so a unit that stops keeps
     * looking where it was looking instead of snapping to nowhere.
     */
    public Appearance face(float dirX, float dirY) {
        float lengthSquared = dirX * dirX + dirY * dirY;
        if (lengthSquared < FACING_EPSILON) {
            return this;
        }
        float scale = 1f / (float) Math.sqrt(lengthSquared);
        this.facingX = dirX * scale;
        this.facingY = dirY * scale;
        return this;
    }

    @Override
    public void reset() {
        look = Look.NONE;
        state = State.NORMAL;
        charge = 0f;
        facingX = 0f;
        facingY = 0f;
        ordered = false;
    }
}
