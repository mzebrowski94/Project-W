package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * How far ahead a unit looks for things in its way, and how hard it leans off them.
 *
 * <p>Numbers only: which way it actually goes is decided fresh every tick from where everything is,
 * and there is nothing worth remembering between ticks. A unit that had committed to a way round
 * would keep going that way after the thing moved.
 */
public final class Avoidance implements Component, Pool.Poolable {

    public boolean enabled;

    /** How close a hazard has to be, surface to surface, before it is worth avoiding. */
    public float scanRange;

    /** How far forward both parties are projected before the gap is measured. */
    public float lookaheadSeconds;

    /** How heavily danger counts against a direction compared with wanting to go that way. */
    public float weight;

    /** Sets what it watches for and returns this component, for fluent spawning. */
    public Avoidance set(boolean newEnabled, float newScanRange, float newLookaheadSeconds, float newWeight) {
        this.enabled = newEnabled;
        this.scanRange = newScanRange;
        this.lookaheadSeconds = newLookaheadSeconds;
        this.weight = newWeight;
        return this;
    }

    @Override
    public void reset() {
        enabled = false;
        scanRange = 0f;
        lookaheadSeconds = 0f;
        weight = 0f;
    }
}
