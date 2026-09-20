package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Pool;
import org.jspecify.annotations.Nullable;

/**
 * One swing of a melee weapon, in the world.
 *
 * <p>A strike is an ordinary entity with a {@link Position} and a {@link Body}, not an overlap query
 * run inside a system (G0005). That is what lets the collision system handle a punch with exactly the
 * rules it already has for a bullet and a claw, keeps the swing visible to the player rather than
 * being an invisible instant of authority, and keeps it replayable.
 *
 * <p><strong>The arc is centred on whoever swung it, tick by tick.</strong> It was anchored where he
 * stood when the swing was released until 2026-09-19, on the argument that a sweep lasting a tenth of
 * a second and one tracking its owner are indistinguishable. Play says otherwise: the commander
 * covers several strides in that tenth of a second, so a sweep left standing where he started reads
 * as coming out of his back, and the fastest way to make the hand strike look wrong was to use it
 * while running. {@link #originX} and {@link #originY} are now where the arc is centred <em>now</em>
 * rather than where it began, and a swing with nobody behind it keeps the last centre it had.
 *
 * <p>{@code owner} is who gets locked when the swing connects. It is the one entity reference held in
 * a pooled component in this codebase, so {@link #reset()} clearing it is not optional - a stale
 * reference here would keep a dead commander alive in the pool.
 */
public final class Strike implements Component, Pool.Poolable {

    public @Nullable Entity owner;

    public float originX;
    public float originY;
    public float reach;

    /** Where the sweep starts, in radians. */
    public float startAngle;

    /** How far around it travels, in radians. Signed, so a weapon could sweep either way. */
    public float sweepRadians;

    public float elapsedSeconds;
    public float durationSeconds;

    /** Sets the sweep and returns this component, for fluent spawning. */
    public Strike set(
            @Nullable Entity newOwner,
            float newOriginX,
            float newOriginY,
            float newReach,
            float newStartAngle,
            float newSweepRadians,
            float newDurationSeconds) {
        this.owner = newOwner;
        this.originX = newOriginX;
        this.originY = newOriginY;
        this.reach = newReach;
        this.startAngle = newStartAngle;
        this.sweepRadians = newSweepRadians;
        this.elapsedSeconds = 0f;
        this.durationSeconds = newDurationSeconds;
        return this;
    }

    /** How far through the sweep it is, in {@code [0, 1]}. */
    public float progress() {
        if (durationSeconds <= 0f) {
            return 1f;
        }
        float fraction = elapsedSeconds / durationSeconds;
        return fraction > 1f ? 1f : fraction;
    }

    /** The angle the sweep is at right now, in radians. */
    public float angle() {
        return startAngle + sweepRadians * progress();
    }

    /** True once the sweep has travelled its whole arc. */
    public boolean spent() {
        return elapsedSeconds >= durationSeconds;
    }

    @Override
    public void reset() {
        owner = null;
        originX = 0f;
        originY = 0f;
        reach = 0f;
        startAngle = 0f;
        sweepRadians = 0f;
        elapsedSeconds = 0f;
        durationSeconds = 0f;
    }
}
