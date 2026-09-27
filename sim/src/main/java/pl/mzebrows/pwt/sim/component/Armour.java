package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * Where on this thing gunfire glances off: an arc round the way it faces (G0045).
 *
 * <p>Carried by every strain and switched off for all but one, so evolving into or out of an armoured
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

    /** Sets what is armoured and returns this component, for fluent spawning. */
    public Armour set(boolean newEnabled, float newArcCosine) {
        this.enabled = newEnabled;
        this.arcCosine = newArcCosine;
        return this;
    }

    @Override
    public void reset() {
        enabled = false;
        arcCosine = 0f;
    }
}
