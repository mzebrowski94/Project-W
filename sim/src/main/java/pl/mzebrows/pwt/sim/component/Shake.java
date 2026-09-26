package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * Something violent happened here, this hard, and it is this far through.
 *
 * <p>The simulation's half of a screen effect, and the whole of its half. It says <em>what
 * happened</em> and stops there; what a view does about it - shake, flash, or nothing at all - is
 * entirely the client's, exactly as {@link Focus} says who matters and says nothing about where the
 * camera goes.
 *
 * <p>Published as a component rather than reached for by the client, because the client is never
 * allowed to ask what an entity <em>is</em> (G0003). A renderer that looked for explosions by kind
 * would have to be taught about every future thing that shakes the ground; one that reads this is
 * told.
 *
 * <p>It carries a clock of its own rather than borrowing the blast's. The two run in step today, and
 * they must be able to stop doing so without the client learning what a blast is.
 */
public final class Shake implements Component, Pool.Poolable {

    /** How hard the ground moved at the middle of it, in world units. */
    public float strength;

    /** How far it is felt at all, in world units. Nothing beyond this notices. */
    public float radius;

    public int durationTicks;

    public int remainingTicks;

    /** Sets the event and starts its clock, returning this component, for fluent spawning. */
    public Shake set(float newStrength, float newRadius, int newDurationTicks) {
        this.strength = newStrength;
        this.radius = newRadius;
        this.durationTicks = newDurationTicks;
        this.remainingTicks = newDurationTicks;
        return this;
    }

    /** How much of it is left, in {@code [0, 1]}. Zero means it is over. */
    public float fade() {
        return durationTicks <= 0 ? 0f : remainingTicks / (float) durationTicks;
    }

    @Override
    public void reset() {
        strength = 0f;
        radius = 0f;
        durationTicks = 0;
        remainingTicks = 0;
    }
}
