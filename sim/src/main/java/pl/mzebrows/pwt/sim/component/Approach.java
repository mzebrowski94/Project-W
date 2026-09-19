package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * Which side of its target a creature closes on, and how wide.
 *
 * <p>{@link #bearing} is the whole of the per-creature state and it never changes: it is seeded from
 * where the unit was spawned, which is what puts a pack abreast rather than in a queue without a
 * number being drawn from the {@code Rng} (G0012). Two creatures put down in different places take
 * different lines in; the same level lays them down the same way on every run.
 *
 * <p>Carried permanently by anything that chases, like every other "how does this strain behave"
 * component - adding one mid-tick would mutate the family being iterated.
 */
public final class Approach implements Component, Pool.Poolable {

    /** Two strides across the plane, deliberately incommensurate, so a row of spawns does not repeat. */
    private static final float STRIDE_X = 0.37f;

    private static final float STRIDE_Y = 0.61f;

    public boolean enabled;

    /** How far to either side of the line it closes, in world units, at full swing. */
    public float offset;

    /** The distance by which it is walking straight at its target again, in world units. */
    public float closeRange;

    /**
     * Which side, and how much of the offset, in {@code [-1, 1)}. Seeded at spawn and never rewritten.
     *
     * <p>Positive is the creature's own <strong>left</strong>: the offset is the direction to its
     * target turned a quarter turn anticlockwise, which is the same handedness everything else in the
     * codebase uses for a perpendicular.
     */
    public float bearing;

    /** Sets how wide this creature closes and returns this component, for fluent spawning. */
    public Approach set(boolean newEnabled, float newOffset, float newCloseRange) {
        this.enabled = newEnabled;
        this.offset = newOffset;
        this.closeRange = newCloseRange;
        return this;
    }

    /**
     * Picks this creature's own line in, from where it was put down.
     *
     * <p>A sawtooth rather than anything cleverer, and that is the useful property: creatures laid
     * out along a line get bearings spread evenly across the whole range, so a spawn row arrives as a
     * fan. Any function of the spawn point would satisfy determinism; this one also satisfies the
     * thing the mechanic is actually for.
     */
    public Approach bearingFrom(float x, float y) {
        float phase = x * STRIDE_X + y * STRIDE_Y;
        this.bearing = (phase - (float) Math.floor(phase)) * 2f - 1f;
        return this;
    }

    /** True if this creature takes a line of its own at all. */
    public boolean active() {
        return enabled && offset > 0f && closeRange > 0f;
    }

    /**
     * How far sideways of its target it is aiming right now, in world units, signed.
     *
     * <p>At its widest past twice {@link #closeRange} and nothing at all by {@link #closeRange}, so
     * the fan is open while the ground is being crossed and closed by the time anything arrives.
     * Linear in between: a creature is not doing arithmetic, and neither should this.
     */
    public float swingAt(float distance) {
        if (!active() || distance <= closeRange) {
            return 0f;
        }
        float ramp = (distance - closeRange) / closeRange;
        return offset * bearing * (ramp >= 1f ? 1f : ramp);
    }

    @Override
    public void reset() {
        enabled = false;
        offset = 0f;
        closeRange = 0f;
        bearing = 0f;
    }
}
