package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.sim.def.Look;

/**
 * What an entity is, and what it is currently doing, for the renderer to look at, so the renderer
 * never asks what components an entity has (G0003, pillar 5).
 *
 * <p>{@link Look} names roles and {@link State} names situations; neither names a colour or a shape.
 * The look is written with the strain; everything else is written by {@code AppearanceSystem} alone.
 */
public final class Appearance implements Component, Pool.Poolable {

    /** What the entity is doing, where that changes how it must read at a glance. */
    public enum State {
        NORMAL,
        /**
         * On the field but not hunting anybody - it has not noticed the commander, or has lost him.
         */
        UNAWARE,
        /** Committed to a leap and standing still. The window the player has to answer it. */
        WINDING_UP,
        AIRBORNE,
        /**
         * A rider in the air, slung (G0074): nothing can hurt it and it passes over everything in its way, so it must
         * read as off the ground - drawn above its own shadow.
         */
        SLUNG,
        /**
         * Locked together with whatever it just traded blows with - both parties, winner and loser
         * alike, for the whole lock.
         */
        GRAPPLING,
        /** Shoved and briefly unable to act, with nothing holding the other party. */
        STAGGERED,
        /**
         * Stunned by something that declares a stun (G0053): unable to act for as long as it said. Must
         * read unlike {@link #STAGGERED}, which flashes past; this is a window to punish or to cover.
         */
        STUNNED,
        /**
         * Wading: robbed of some of its speed and still doing everything else.
         *
         * <p>Below {@link #STAGGERED}. Only what was <em>done</em> to it reports this, not slowing
         * ground, which is drawn already.
         */
        SLOWED,
        /**
         * Shot off his feet and bleeding out, but not gone. Must read unmistakably unlike
         * {@link #DYING}.
         */
        CASUALTY,
        /** Out of health, body still on the field. */
        DYING,
        /**
         * Inside something rather than standing on the ground - a man on a turret; the gun shows it.
         */
        ABOARD,
        /**
         * Manned: readable from outside its arc.
         */
        CREWED,
        /**
         * In something's jaws and being carried off (G0045). His clock is on the charge channel, read
         * backwards like a casualty's; this says why it is running.
         */
        GRIPPED
    }

    public Look look = Look.NONE;
    public State state = State.NORMAL;

    /**
     * How far along the entity is towards something it is filling up for, in {@code [0, 1]}.
     *
     * <p>The general channel for "filling up" (pillar 5), so the renderer draws it one way. Zero is
     * nothing to show.
     */
    public float charge;

    /**
     * What that charge is, where it is worth telling apart from every other thing that fills up.
     *
     * <p>{@link Look#NONE} is the ordinary one, drawn in the entity's own colour brightened. Anything
     * else names a look to colour it with, so a new kind of charge is an entry in {@link Look} and a
     * line in {@code graphic.properties} (G0003). Charges asking opposite reactions must differ.
     */
    public Look chargeLook = Look.NONE;

    /**
     * The far end of the streak this entity is drawn as, in world units; equal to its own position
     * means it is a dot, which is what everything in the game publishes but one.
     *
     * <p>How a fast stream of shots reads as a beam: the line is the path a real shot took - no hitscan.
     */
    public float trailX;

    public float trailY;

    /**
     * Whether this entity is acting on an order rather than on its own judgement (3.f).
     *
     * <p>So the player can see who heard him (pillar 5).
     */
    public boolean ordered;

    /**
     * Whether what this entity is carrying goes off where it lands.
     *
     * <p>Derived from the gun, not the strain, so anything carrying an exploding weapon reads so.
     */
    public boolean charged;

    /**
     * How hot this entity's gun is, in {@code [0, 1]}, and whether it has stopped itself.
     *
     * <p>Its own channel, not {@link #charge}: it fills because of what the player did, and must be
     * readable alongside a charge.
     */
    public float heat;

    /** Whether that gun has stopped itself. The half of the bar that changes what to do next. */
    public boolean overheated;

    /**
     * Which of a look's several appearances this one wears. Zero is the ordinary one.
     *
     * <p>Each player's commander wears a different one (pillar 5, G0003). A look with no such variant
     * is drawn in its ordinary colour.
     */
    public int variant;

    /**
     * How many wounds a man on his feet is carrying (G0053). Zero for everything else, and for a man
     * who is down - his bleed-out says more.
     *
     * <p>A count, so the renderer can draw one mark per wound and the player never has to guess one
     * (pillar 5).
     */
    public int wounds;

    /**
     * How far round it the circle of a slam it has just fired reaches, in world units; zero is none (G0071). Drawn
     * as a white outline over a pale fill, fading by {@link #burstFade}.
     */
    public float burst;

    /** How much of that circle is left to draw, in {@code [0, 1]}. */
    public float burstFade;

    /**
     * How much of a pop is left, in {@code [0, 1]}; zero for everything that is no pop (G0078). Drawn rising off where
     * it appeared and fading as it runs down.
     */
    public float pop;

    /**
     * How much of a declared stun it is under is left, in {@code [0, 1]}; zero with none (G0053). Drawn as a pale
     * circle shrinking onto it as the stun runs out.
     */
    public float daze;

    /**
     * How much of a shield is left in front of him, in {@code [0, 1]} (G0053): one for a shield that
     * lasts until it is hit, running down for one that runs out on its own, zero for none.
     */
    public float shield;

    /**
     * How many riders it still carries on its back (G0074), one circle each, one fewer for every sling; zero for
     * everything that carries none.
     */
    public int riders;

    /** What its riders are drawn as. */
    public Look riderLook = Look.NONE;

    /**
     * How many squares across and down a thing with no body is drawn as, centred on where it stands: a door, its
     * doorway's tiles (G0069). Zero for everything drawn from its body.
     */
    public int squaresAcross;

    public int squaresDown;

    /** How far apart the middles of its squares are, in world units: one tile. */
    public float squarePitch;

    /** How much of each square is drawn, in {@code [0, 1]}: one whole, shrinking to nothing as a door sinks. */
    public float squareFill;

    /** Sets the look and returns this component, for fluent spawning. */
    public Appearance set(Look newLook) {
        return set(newLook, 0);
    }

    /** Draws this as squares across and down, this far apart and whole, and returns this component. */
    public Appearance squares(int across, int down, float pitch) {
        this.squaresAcross = across;
        this.squaresDown = down;
        this.squarePitch = pitch;
        this.squareFill = 1f;
        return this;
    }

    /** Draws this back to the given point rather than as a dot, and returns this component. */
    public Appearance trailingFrom(float fromX, float fromY) {
        this.trailX = fromX;
        this.trailY = fromY;
        return this;
    }

    /** True if this is drawn as a line rather than as a dot. */
    public boolean streaks() {
        return trailX != 0f || trailY != 0f;
    }

    /** Sets the look and which of its appearances to wear, and returns this component. */
    public Appearance set(Look newLook, int newVariant) {
        this.look = newLook;
        this.variant = newVariant;
        this.state = State.NORMAL;
        this.charge = 0f;
        this.chargeLook = Look.NONE;
        return this;
    }

    @Override
    public void reset() {
        look = Look.NONE;
        variant = 0;
        state = State.NORMAL;
        charge = 0f;
        chargeLook = Look.NONE;
        trailX = 0f;
        trailY = 0f;
        ordered = false;
        charged = false;
        heat = 0f;
        overheated = false;
        wounds = 0;
        burst = 0f;
        burstFade = 0f;
        pop = 0f;
        daze = 0f;
        shield = 0f;
        riders = 0;
        riderLook = Look.NONE;
        squaresAcross = 0;
        squaresDown = 0;
        squarePitch = 0f;
        squareFill = 0f;
    }
}
