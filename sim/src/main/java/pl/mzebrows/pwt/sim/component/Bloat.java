package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * How far along something is towards bursting, and the body it will go back to if it is let alone.
 *
 * <p>Sustained beam pumps a thing up; on the living it is only a number, so holding the beam
 * <em>through</em> a kill makes a bomb. It is <strong>drawn on</strong> the thing, never done to the
 * body. At the brim a dying body bursts, by dying (G0018). Let go, it falls away; the corpse waits
 * while any is left. A bigger body takes longer. Carried permanently.
 */
public final class Bloat implements Component, Pool.Poolable {

    /** How full it is, in {@code [0, 1]}. One is a body about to go off. */
    public float charge;

    /**
     * Whether a shot reached it this tick.
     *
     * <p>A thing being charged does not settle. Written where the blow lands, read by
     * {@code BloatSystem}.
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
