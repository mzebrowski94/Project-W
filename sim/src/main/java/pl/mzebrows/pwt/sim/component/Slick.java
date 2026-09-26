package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * One circle of ground that has been ruined, for a while.
 *
 * <p>It holds no radius of its own to be drawn from, exactly as {@link Blast} holds none: the
 * entity's {@code Body} is both the circle that hurts and the circle the renderer draws, so the ring
 * the player sees cannot drift from the ring that catches him. What it holds is the radius the body
 * is <strong>going</strong> to be asked for - a circle that swells and collapses is one number
 * written onto the body every tick rather than two kept in step.
 *
 * <p>Unlike a blast it <strong>is</strong> in the pairwise path, and that is the difference between
 * the two. A blast is an event - it catches what is standing in it on the one tick it goes off, and
 * a blast-sized body in the collision grid would set the cell size for the whole level to serve a
 * quarter of a second. A slick is a <em>place</em>: it has to catch whatever walks into it for as
 * long as it lasts, and it is small enough to be an ordinary body while it does. Being one is what
 * lets it borrow the rules the game already has - the blow it lands is a {@code ContactDamage} like
 * any other, and the side it is on decides who it can reach with no branch anywhere.
 *
 * <p>The clock is counted down by {@code CooldownSystem} with everything else that counts down, and
 * read by {@code SlickSystem}, which is the only thing that takes one away and the only thing that
 * writes the body.
 */
public final class Slick implements Component, Pool.Poolable {

    /** How long this circle lasts altogether. */
    public int durationTicks;

    public int remainingTicks;

    /** How wide it gets at its widest, in world units. The body is this once it has finished growing. */
    public float peakRadius;

    /** How much of its life it spends swelling to {@link #peakRadius}, as a fraction. Zero is instant. */
    public float growFraction;

    /** How much of its life it spends collapsing again, as a fraction. Zero is gone at full size. */
    public float fadeFraction;

    /** Sets the life and shape of the slick and starts it, returning this component, for fluent spawning. */
    public Slick set(int newDurationTicks, float newPeakRadius, float newGrowFraction, float newFadeFraction) {
        this.durationTicks = newDurationTicks;
        this.remainingTicks = newDurationTicks;
        this.peakRadius = newPeakRadius;
        this.growFraction = newGrowFraction;
        this.fadeFraction = newFadeFraction;
        return this;
    }

    /** How much of its life is left, in {@code [0, 1]}, for the renderer to fade it out by. */
    public float fade() {
        return durationTicks <= 0 ? 0f : remainingTicks / (float) durationTicks;
    }

    /** True if this circle changes size over its life, and therefore has a body to rewrite each tick. */
    public boolean animated() {
        return growFraction > 0f || fadeFraction > 0f;
    }

    /**
     * How wide it is right now, in world units.
     *
     * <p>Swells over the first {@link #growFraction} of its life, holds, and collapses over the last
     * {@link #fadeFraction} of it. Two ramps and a flat, with no curve between them: what the player
     * has to be able to answer is "am I in it", and the circle on the screen is this number, so any
     * shape at all is honest as long as the body and the drawing are one number - which they are.
     */
    public float radiusNow() {
        if (durationTicks <= 0) {
            return peakRadius;
        }
        float elapsed = 1f - fade();
        if (growFraction > 0f && elapsed < growFraction) {
            return peakRadius * (elapsed / growFraction);
        }
        if (fadeFraction > 0f && elapsed > 1f - fadeFraction) {
            return peakRadius * ((1f - elapsed) / fadeFraction);
        }
        return peakRadius;
    }

    /** True once the ground is clean again. */
    public boolean spent() {
        return remainingTicks <= 0;
    }

    @Override
    public void reset() {
        durationTicks = 0;
        remainingTicks = 0;
        peakRadius = 0f;
        growFraction = 0f;
        fadeFraction = 0f;
    }
}
