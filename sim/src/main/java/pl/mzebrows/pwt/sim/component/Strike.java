package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * One swing of a melee weapon, in the world.
 *
 * <p>An ordinary entity with a {@link Position} and a {@link Body}, not an overlap query (G0005), so
 * collision treats it like any blow and it stays visible and replayable.
 *
 * <p><strong>The arc is centred on whoever swung it, tick by tick</strong>: {@link #originX} and
 * {@link #originY} are the centre <em>now</em>. A swing with nobody behind it keeps its last centre.
 * {@code owner} is who gets locked when it connects.
 */
public final class Strike implements Component, Pool.Poolable {

    /** Who swung it, by {@link Handle}; {@link Handle#NOBODY} for a swing with nobody behind it. */
    public long owner = Handle.NOBODY;

    public float originX;
    public float originY;
    public float reach;

    /** Where the sweep starts, in radians. */
    public float startAngle;

    /** How far around it travels, in radians. Signed, so a weapon could sweep either way. */
    public float sweepRadians;

    /** Ticks since it was swung, and how many the sweep lasts. */
    public int elapsedTicks;

    public int durationTicks;

    /** Sets the sweep and returns this component, for fluent spawning. */
    public Strike set(
            long newOwner,
            float newOriginX,
            float newOriginY,
            float newReach,
            float newStartAngle,
            float newSweepRadians,
            int newDurationTicks) {
        this.owner = newOwner;
        this.originX = newOriginX;
        this.originY = newOriginY;
        this.reach = newReach;
        this.startAngle = newStartAngle;
        this.sweepRadians = newSweepRadians;
        this.elapsedTicks = 0;
        this.durationTicks = newDurationTicks;
        return this;
    }

    /** How far through the sweep it is, in {@code [0, 1]}. */
    public float progress() {
        if (durationTicks <= 0) {
            return 1f;
        }
        float fraction = (float) elapsedTicks / durationTicks;
        return fraction > 1f ? 1f : fraction;
    }

    /** The angle the sweep is at right now, in radians. */
    public float angle() {
        return startAngle + sweepRadians * progress();
    }

    /** True once the sweep has travelled its whole arc. */
    public boolean spent() {
        return elapsedTicks >= durationTicks;
    }

    @Override
    public void reset() {
        owner = Handle.NOBODY;
        originX = 0f;
        originY = 0f;
        reach = 0f;
        startAngle = 0f;
        sweepRadians = 0f;
        elapsedTicks = 0;
        durationTicks = 0;
    }
}
