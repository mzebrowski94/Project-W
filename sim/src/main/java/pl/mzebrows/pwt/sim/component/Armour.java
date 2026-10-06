package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * Where on this thing gunfire glances off: an arc round one end of it, turned from the way it faces (G0045, G0073).
 *
 * <p>Carried by every strain and switched off for all but two, so evolving into or out of an armoured
 * strain is a field write rather than a component coming or going mid-tick. Read in one place, where a
 * shot is spent on what it touched.
 */
public final class Armour implements Component, Pool.Poolable {

    /** Whether any of it is armoured. */
    public boolean enabled;

    /**
     * The smallest dot product between its facing and the way back along a shot for the shot to
     * glance off. Meaningless unless {@link #enabled}.
     */
    public float arcCosine;

    /** The cosine of the turn from its facing to the middle of the arc: one for a head, minus one for a back. */
    public float centreCosine;

    /** The sine of that turn, anticlockwise. */
    public float centreSine;

    /** Sets what is armoured and returns this component, for fluent spawning. */
    public Armour set(boolean newEnabled, float newArcCosine, float newCentreCosine, float newCentreSine) {
        this.enabled = newEnabled;
        this.arcCosine = newArcCosine;
        this.centreCosine = newCentreCosine;
        this.centreSine = newCentreSine;
        return this;
    }

    @Override
    public void reset() {
        enabled = false;
        arcCosine = 0f;
        centreCosine = 0f;
        centreSine = 0f;
    }
}
