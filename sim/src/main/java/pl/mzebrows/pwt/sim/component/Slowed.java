package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * How much of its own speed an entity has been robbed of, and for how long.
 *
 * <p>A slow is not a stun. A stunned unit acts on nothing and is owned by {@code StunSystem}; a
 * slowed one goes on doing everything it was doing and simply gets less of the speed it asked for.
 * That is why it lives here rather than as a field on {@link Stunned}: the two compose, and a unit
 * that is both is stunned first and slow afterwards.
 *
 * <p><strong>The worst one wins, and the clock is refreshed rather than added to.</strong> A beam
 * held on one creature keeps it at its worst for as long as the beam is on it and no longer, which
 * is what makes holding the trigger down the thing that buys the slow - banking seconds of it would
 * make a tap worth as much as a commitment.
 *
 * <p>What it does <em>not</em> hold is the ground. Ruined footing is a fact about where a unit is
 * standing rather than something that happened to it, so it is asked of the map every tick and never
 * written here; {@code Pace} is the one place the two are multiplied together.
 *
 * <p>Counted down by {@code CooldownSystem} with everything else that counts down, and spent by
 * {@code Pace}.
 */
public final class Slowed implements Component, Pool.Poolable {

    /** How much of its speed it keeps while this is running, as a fraction. */
    public float factor;

    public int remainingTicks;

    /**
     * Robs it of speed, keeping the worst asked for and restarting the clock.
     *
     * <p>A factor of one or more is not a slow and is ignored outright, so a source that declares
     * none draws nothing and touches nothing - the same "zero means not for me" shape as everywhere
     * else, asked of a number whose neutral value happens to be one.
     */
    public void apply(float newFactor, int ticks) {
        if (newFactor >= 1f || ticks <= 0) {
            return;
        }
        if (remainingTicks <= 0 || newFactor < factor) {
            factor = newFactor;
        }
        remainingTicks = ticks;
    }

    /** How much of its speed it keeps right now. One once the slow has run out. */
    public float factor() {
        return remainingTicks > 0 ? factor : 1f;
    }

    @Override
    public void reset() {
        factor = 0f;
        remainingTicks = 0;
    }
}
