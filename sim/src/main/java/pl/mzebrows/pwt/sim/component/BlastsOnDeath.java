package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.Look;

/**
 * A death reaction: this entity leaves a blast where it fell.
 *
 * <p><strong>Everything in the game explodes by dying</strong> (G0018): one path to a blast. Data on
 * the entity, as {@link RespawnsOnDeath}, so the killer knows nothing about explosions (G0004).
 */
public final class BlastsOnDeath implements Component, Pool.Poolable {

    /** How wide the blast it leaves is, in world units. Also the circle the player will see. */
    public float radius;

    /**
     * How wide instead, if something bit into it rather than shooting it.
     *
     * <p>A field read before the death, not a second path (G0018).
     */
    public float bittenRadius;

    /**
     * What the blast it leaves does to a man (G0053), declared by the kind of thing that explodes. Set
     * wherever one is spawned; a grenade's is its own, everything else's the blast's.
     */
    public Harm harm = Harm.DOWN;

    /**
     * Whether the blast leaves the ground burning behind it.
     *
     * <p>False only for a body pumped full: a flash, not a fire (G0035).
     */
    public boolean leavesFire = true;

    /**
     * What the circle is drawn as.
     *
     * <p>{@link Look#BLAST}, except a body pumped full, drawn as the ring that charged it.
     */
    public Look look = Look.BLAST;

    /**
     * The slot whose tally the blast it leaves credits: the thrower of a grenade, the shooter of an
     * explosive shot, or - written by the blow that kills a barrel or a charged body - whoever landed
     * it. {@link Tally#NOBODY} otherwise.
     */
    public int credit = Tally.NOBODY;

    /** Credits the blast this leaves to a player's slot, returning this component, for fluent spawning. */
    public BlastsOnDeath credited(int slot) {
        this.credit = slot;
        return this;
    }

    /** Sets the reaction and returns this component, for fluent spawning. */
    public BlastsOnDeath set(float newRadius, float newBittenRadius) {
        this.radius = newRadius;
        this.bittenRadius = newBittenRadius;
        this.leavesFire = true;
        this.look = Look.BLAST;
        return this;
    }

    /** Declares what the blast this leaves does to a man, and returns this component, for fluent spawning. */
    public BlastsOnDeath harming(Harm newHarm) {
        this.harm = newHarm;
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
     * <p>A reaction with no radius leaves nothing behind (G0018).
     */
    public void swallowed() {
        this.radius = 0f;
    }

    @Override
    public void reset() {
        radius = 0f;
        bittenRadius = 0f;
        harm = Harm.DOWN;
        leavesFire = true;
        look = Look.BLAST;
        credit = Tally.NOBODY;
    }
}
