package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.sim.def.Look;

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

    /**
     * How wide instead, if something bit into it rather than shooting it.
     *
     * <p>Smaller: a creature that walks onto a barrel and sets it off with its mouth is standing on
     * top of the thing, and most of the blast goes into it. Shooting one from across the field is
     * still the full circle, which is what keeps a barrel worth aiming at rather than worth waiting
     * next to.
     *
     * <p>Two numbers on the reaction rather than a second death path: <strong>anything that explodes
     * still does so by dying, through one reaction</strong> (G0018). What changed is a field read
     * before the death, not where the blast comes from.
     */
    public float bittenRadius;

    /**
     * Whether the blast leaves the ground burning behind it.
     *
     * <p>True for everything that has ever exploded, which is why it is a field with a default
     * rather than a question. G0035 made the <em>side</em> of what a blast leaves a declaration
     * rather than a property of the mechanic; this makes its <em>existence</em> one, for the one
     * case that wanted it: a body pumped full is a flash, not a fire, and a corpse bomb that fenced
     * off the ground it went off on would be a tool the player could not use twice in one place.
     *
     * <p>It is still one reaction and one death. What changed is a field read before the death, not
     * whether a second path to an explosion exists.
     */
    public boolean leavesFire = true;

    /**
     * What the circle is drawn as.
     *
     * <p>{@link Look#BLAST} for everything that has ever exploded, which is why it is a field with a
     * default rather than a question. A body pumped full is the one explosion in the game the player
     * <em>made</em> rather than set off, and it has to read as the payoff of the ring that charged
     * it - the same colour, so the two are one event rather than two things that happened in the
     * same place.
     */
    public Look look = Look.BLAST;

    /** Sets the reaction and returns this component, for fluent spawning. */
    public BlastsOnDeath set(float newRadius, float newBittenRadius) {
        this.radius = newRadius;
        this.bittenRadius = newBittenRadius;
        this.leavesFire = true;
        this.look = Look.BLAST;
        return this;
    }

    /** Makes this a flash rather than a fire, and returns this component, for fluent spawning. */
    public BlastsOnDeath withoutFire() {
        this.leavesFire = false;
        return this;
    }

    /** Draws this circle as something other than an ordinary explosion, for fluent spawning. */
    public BlastsOnDeath drawnAs(Look newLook) {
        this.look = newLook;
        return this;
    }

    /** Records that this was set off by something biting into it, which makes for a smaller bang. */
    public void bitten() {
        this.radius = bittenRadius;
    }

    /**
     * Records that this was taken whole rather than set off, which makes for no bang at all.
     *
     * <p>A reaction with no radius leaves nothing behind. It is still the same reaction and the same
     * one death (G0018) - what changed is a number read before the death, not whether a second path
     * to an explosion exists.
     */
    public void swallowed() {
        this.radius = 0f;
    }

    @Override
    public void reset() {
        radius = 0f;
        bittenRadius = 0f;
        leavesFire = true;
        look = Look.BLAST;
    }
}
