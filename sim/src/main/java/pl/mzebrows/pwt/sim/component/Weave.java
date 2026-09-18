package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * How far through its weave a creature is, and how hard it weaves.
 *
 * <p>{@link #travelled} is the whole of the runtime state: the lean is a function of it and of
 * nothing else, so the same creature covering the same ground leans the same way every run. It is
 * distance rather than time on purpose - a creature held up by a wall does not carry on weaving on
 * the spot, and one slowed down does not weave faster.
 *
 * <p>It is seeded from where the unit was spawned rather than at zero, which is what puts a pack out
 * of step with itself without anything being drawn from the {@code Rng} (G0012).
 */
public final class Weave implements Component, Pool.Poolable {

    public boolean enabled;

    /** How hard it leans off its course, as a fraction of its forward direction. */
    public float amplitude;

    /** How far it travels through one full left-right-left cycle, in world units. */
    public float wavelength;

    /** Ground covered, in world units, offset by where it started. */
    public float travelled;

    /** Sets how this creature weaves and returns this component, for fluent spawning. */
    public Weave set(boolean newEnabled, float newAmplitude, float newWavelength) {
        this.enabled = newEnabled;
        this.amplitude = newAmplitude;
        this.wavelength = newWavelength;
        return this;
    }

    /**
     * Starts the weave part-way through, from where the unit was spawned.
     *
     * <p>Any function of the spawn point would do; what matters is that two units put down in
     * different places are out of step and that the same level lays them down the same way every run.
     */
    public Weave phaseFrom(float x, float y) {
        this.travelled = x + y;
        return this;
    }

    /** True if this creature leans off its course at all. */
    public boolean active() {
        return enabled && amplitude > 0f && wavelength > 0f;
    }

    /**
     * How far it is leaning right now, in {@code [-1, 1]}.
     *
     * <p>A triangle rather than a sine: piecewise linear, so there is no trigonometry in the tick at
     * all - {@code Math.sin} is banned in this module because it is specified only to within an ulp
     * and may differ between JVMs (pillar 6). A creature leaning on a triangle reads as a creature
     * changing its mind at the ends of the weave, which is what one does.
     */
    public float lean() {
        float cycles = travelled / wavelength;
        float phase = cycles - (float) Math.floor(cycles);
        float quarter = phase * 4f;
        if (quarter < 1f) {
            return quarter;
        }
        if (quarter < 3f) {
            return 2f - quarter;
        }
        return quarter - 4f;
    }

    @Override
    public void reset() {
        enabled = false;
        amplitude = 0f;
        wavelength = 0f;
        travelled = 0f;
    }
}
