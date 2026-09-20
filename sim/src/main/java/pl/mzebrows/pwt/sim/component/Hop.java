package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * Where a creature is in its gait: mid-hop, or sitting between hops.
 *
 * <p>{@link #phase} is the whole of the runtime state, and it is a <strong>fraction of a cycle</strong>
 * rather than a count of seconds. That is not a detail: it is seeded from where the unit was spawned,
 * which happens before its strain's numbers are written into this component, and a phase in seconds
 * would have had to be divided by a cycle that was still zero. As a fraction the seed needs to know
 * nothing about the gait, which is what lets the spawn point set it - and that is what stops a pack
 * of them hopping in unison without anything being drawn from the {@code Rng}, the same trick
 * {@link Weave} plays for the same reason.
 *
 * <p>A clock rather than ground covered, which is the one place this differs from a weave. A weave
 * must not change shape when something slows the creature down; a gait must, because legs have a
 * rhythm and a creature pressed against a wall is still trying to hop.
 */
public final class Hop implements Component, Pool.Poolable {

    public boolean enabled;

    /** One hop and the sit after it, in seconds. */
    public float cycleSeconds;

    /** How much of that cycle is spent in the air, in {@code [0, 1]}. */
    public float hopFraction;

    /** What a hop multiplies the pace by, so a whole cycle covers what a walk would. */
    public float burst = 1f;

    /** Where it is in the cycle, as a fraction of one, always inside {@code [0, 1)}. */
    public float phase;

    /** Sets how this creature gets about and returns this component, for fluent spawning. */
    public Hop set(boolean newEnabled, float newHopSeconds, float newRestSeconds, float newBurst) {
        this.enabled = newEnabled;
        this.cycleSeconds = newHopSeconds + newRestSeconds;
        this.hopFraction = cycleSeconds <= 0f ? 0f : newHopSeconds / cycleSeconds;
        this.burst = newBurst;
        return this;
    }

    /**
     * Starts the gait part-way through, from where the unit was spawned.
     *
     * <p>Any function of the spawn point would do; what matters is that two put down in different
     * places are out of step, that the same level puts them down the same way every run, and that it
     * asks nothing about the gait - because at the moment a unit is spawned, nothing has been
     * written into the rest of this component yet.
     */
    public Hop phaseFrom(float x, float y) {
        float sum = x + y;
        float fraction = sum - (float) Math.floor(sum);
        this.phase = fraction;
        return this;
    }

    /** True if this creature gets about in bursts at all. */
    public boolean active() {
        return enabled && cycleSeconds > 0f && hopFraction > 0f;
    }

    /** True while it is actually going anywhere. */
    public boolean airborne() {
        return phase < hopFraction;
    }

    /** What the pace is multiplied by right now: the burst mid-hop, and nothing at all at rest. */
    public float pace() {
        return airborne() ? burst : 0f;
    }

    /** Advances the gait, wrapping at the end of a cycle. */
    public void advance(float deltaTime) {
        if (cycleSeconds <= 0f) {
            return;
        }
        phase += deltaTime / cycleSeconds;
        while (phase >= 1f) {
            phase -= 1f;
        }
    }

    @Override
    public void reset() {
        enabled = false;
        cycleSeconds = 0f;
        hopFraction = 0f;
        burst = 1f;
        phase = 0f;
    }
}
