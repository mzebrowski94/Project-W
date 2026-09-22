package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * How far along something is towards bursting, and the body it will go back to if it is let alone.
 *
 * <p>Sustained beam on a thing pumps it up. While it is <strong>alive</strong> that is a number and
 * nothing else - a creature does not visibly inflate while it is still coming at you, and a beam
 * that killed by inflation rather than by damage would make health beside the point. What it buys
 * is that the charge is already there when the thing dies, so holding the beam <em>through</em> a
 * kill is what turns a body into a bomb. Sweeping across a crowd buys nothing at all.
 *
 * <p>What it fills is <strong>drawn on</strong> the thing rather than done to it: a bright ring
 * growing over the body, published on the charge channel and sized by the client. The body itself is
 * never touched - a corpse that swelled was a corpse that shoved everything around it and changed
 * what a doorway was worth, which is a lot of consequence for a tell. At the brim it bursts, by
 * dying, through the one death reaction everything that explodes uses (G0018).
 *
 * <p>Let go and the charge falls away and the ring shrinks with it; the corpse waits while there is
 * any charge on it at all, so a man who has spent half a heat bar on one can go and deal with
 * something and come back to it.
 *
 * <p><strong>A bigger body takes longer.</strong> What one shot is worth is divided by how large the
 * thing is, so a brute is a real decision and a grunt is an afterthought - and nothing had to be
 * said per strain, because a strain's size is already the thing that differs.
 *
 * <p>Carried permanently by everything that can be killed, like {@link Dying}: adding it at the
 * moment the first shot lands would mutate a family mid-iteration.
 */
public final class Bloat implements Component, Pool.Poolable {

    /** How full it is, in {@code [0, 1]}. One is a body about to go off. */
    public float charge;

    /**
     * Whether a shot reached it this tick.
     *
     * <p>Without it the charge fell away on the very ticks the beam was on it, so a corpse gained
     * what the gun put in and lost what the clock took out - a net of almost nothing on a grunt and
     * an actual <em>loss</em> on a brute, whose bigger body takes the beam more slowly than the clock
     * empties it. A thing being charged does not settle; that is the whole of the rule, and it needs
     * one bit to say so.
     *
     * <p>Written where the blow lands and read once, later in the same tick, by the system that
     * owns everything else about this.
     */
    public boolean fed;

    /** Adds what one shot is worth, and stops at the brim. */
    public void feed(float perShot) {
        if (perShot <= 0f) {
            return;
        }
        fed = true;
        charge += perShot;
        if (charge > 1f) {
            charge = 1f;
        }
    }

    /** Whether a shot reached it this tick, clearing the flag so the next tick starts fresh. */
    public boolean takeFed() {
        boolean was = fed;
        fed = false;
        return was;
    }

    /** A tick's worth of going down again, for a body nothing is pointed at. Never below nothing. */
    public void settle(float perSecond, float deltaTime) {
        charge -= perSecond * deltaTime;
        if (charge <= 0f) {
            charge = 0f;
        }
    }

    /** True once it is full. Only a body that is already dying is allowed to act on this. */
    public boolean full() {
        return charge >= 1f;
    }

    /** True if there is anything on it at all - which is what keeps a corpse on the field. */
    public boolean charged() {
        return charge > 0f;
    }

    @Override
    public void reset() {
        charge = 0f;
        fed = false;
    }
}
