package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * How far along something is towards being stunned by a gun that stuns only once enough of it has
 * landed - the pulse rifle (G0059).
 *
 * <p>Each hit builds it, scaled by the body, so a bigger body takes more. Full is a stun, and the stun
 * empties it; it builds nothing while that stun runs, so a held trigger is not a stun-lock. A tick no hit
 * builds it, it drains. Drawn on the charge ring. Carried permanently by everything that can be killed;
 * a strain that takes none - the boss - carries it switched off.
 */
public final class Shock implements Component, Pool.Poolable {

    /** Full is one; a hit that is worth a little less than its share still fills on the last one. */
    private static final float FULL = 1f - 1e-4f;

    /** How full it is, in {@code [0, 1)}. It never sits at full: the hit that fills it stuns and empties it. */
    public float charge;

    /**
     * Whether a hit built it this tick. Written where the blow lands, read by {@code ShockSystem}, which
     * drains only what nothing built.
     */
    public boolean fed;

    /** True for a strain shock never builds on (G0059). Written from the strain, never by a hit. */
    public boolean immune;

    /** Switches it off for a strain that takes none, or on, returning this component, for fluent spawning. */
    public Shock immuneIf(boolean off) {
        this.immune = off;
        return this;
    }

    /**
     * Adds what one tick's hits are worth. True when that fills it - and then it is empty again, because
     * the stun it is owed is landing.
     */
    public boolean build(float worth) {
        if (immune || worth <= 0f) {
            return false;
        }
        fed = true;
        charge += worth;
        if (charge < FULL) {
            return false;
        }
        charge = 0f;
        return true;
    }

    /** Whether a hit built it this tick, clearing the flag so the next tick starts fresh. */
    public boolean takeFed() {
        boolean was = fed;
        fed = false;
        return was;
    }

    /** A tick's worth of coming off again. Never below nothing. */
    public void drain(float perSecond, float deltaTime) {
        charge -= perSecond * deltaTime;
        if (charge <= 0f) {
            charge = 0f;
        }
    }

    /** True if there is anything on it at all. */
    public boolean charged() {
        return charge > 0f;
    }

    @Override
    public void reset() {
        charge = 0f;
        fed = false;
        immune = false;
    }
}
