package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * A puff (G0107): the see-through smoke a dash leaves where it began, and all a press with no dash left does. A look
 * only - feedback, spawned rather than sent (G0004), on a pop's pattern (G0078).
 */
public final class Puff implements Component, Pool.Poolable {

    /** How long it lasts, in whole ticks. */
    public int lifeTicks;

    /** How much of that is left, in whole ticks; zero is over. */
    public int remainingTicks;

    /** Starts it, this many ticks long, and returns this component, for fluent spawning. */
    public Puff set(int newLifeTicks) {
        this.lifeTicks = newLifeTicks;
        this.remainingTicks = newLifeTicks;
        return this;
    }

    /** Whether it has run out. */
    public boolean isOver() {
        return remainingTicks <= 0;
    }

    /** How much of it is left, in {@code [0, 1]}: one when it appears, zero when it is over. */
    public float left() {
        if (lifeTicks <= 0) {
            return 0f;
        }
        return (float) remainingTicks / lifeTicks;
    }

    @Override
    public void reset() {
        lifeTicks = 0;
        remainingTicks = 0;
    }
}
