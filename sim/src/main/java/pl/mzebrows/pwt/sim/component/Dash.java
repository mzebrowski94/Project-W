package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * A commander's dashes (G0107): the one he is in, which way it goes, which way he last moved, and how many he has
 * spent.
 *
 * <p><strong>A count, kept as what was spent</strong>, as a {@link Thrower}'s is (G0077): what he has left is his most
 * less what he has spent, and his most is folded where it is read ({@code Dashes}). So a new body comes in full, and an
 * upgrade that raises the most hands him the dash it adds, with nothing else to write.
 *
 * <p>The burst's clock ({@link #remainingTicks}) is counted down by {@code CooldownSystem} with everything else that
 * counts down; the recharge runs only while something is spent, so it is counted where that is decided, by {@code
 * CommanderControlSystem}.
 */
public final class Dash implements Component, Pool.Poolable {

    /** Where {@link #sinceTicks} stops counting: longer ago than anything is drawn for. */
    public static final int LONG_AGO = 1 << 20;

    /** How much of the burst he is in is left, in whole ticks; zero is none. */
    public int remainingTicks;

    /** Which way the burst goes, as a unit vector: fixed at launch, never steered. Kept after it ends, for its tail. */
    public float directionX;

    public float directionY;

    /** Which way he last moved, as a unit vector; {@code (0, 0)} if he never has. Where a neutral stick dashes. */
    public float lastMoveX;

    public float lastMoveY;

    /** How many he has spent since he was last full. */
    public int spent;

    /** How long until one he spent comes back, in whole ticks; zero while nothing is spent. */
    public int rechargeTicks;

    /**
     * How many ticks since his last burst ended, held at {@link #LONG_AGO}; {@link #LONG_AGO} if he never dashed. A
     * look's clock - the thrusters' tail is drawn from it - and nothing in the simulation reads it.
     */
    public int sinceTicks = LONG_AGO;

    /** Whether a burst is running. */
    public boolean running() {
        return remainingTicks > 0;
    }

    @Override
    public void reset() {
        remainingTicks = 0;
        directionX = 0f;
        directionY = 0f;
        lastMoveX = 0f;
        lastMoveY = 0f;
        spent = 0;
        rechargeTicks = 0;
        sinceTicks = LONG_AGO;
    }
}
