package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.sim.def.Whole;

/** How much damage an entity can take before it dies. Anything with this can be killed. */
public final class Health implements Component, Pool.Poolable {

    /** Whole hit points (G0054): no fraction is ever stored here. */
    public int max;

    public int current;

    /**
     * How many per cent of a blast's damage it shrugs off (G0082): zero, for everything but what its kind names -
     * a turret. Read in one place, where a blast lands; gunfire, fire and claws land in full.
     */
    public int blastResistance;

    /** Sets the maximum and starts at full, returning this component, for fluent spawning. */
    public Health set(int newMax) {
        this.max = newMax;
        this.current = newMax;
        return this;
    }

    /** Sets how much of a blast it shrugs off, in per cent, returning this component, for fluent spawning. */
    public Health resisting(int blastPercent) {
        this.blastResistance = blastPercent;
        return this;
    }

    /** What a blast that owes {@code damage} takes from it: the rest, after what it shrugs off, rounded once (G0054). */
    public int fromBlast(int damage) {
        return Whole.less(damage, blastResistance);
    }

    /** Fraction remaining, in {@code [0, 1]}. For anything that displays a health bar. */
    public float fraction() {
        return max <= 0 ? 0f : Math.max(0, current) / (float) max;
    }

    @Override
    public void reset() {
        max = 0;
        current = 0;
        blastResistance = 0;
    }
}
