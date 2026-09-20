package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * One circle of ground that has been ruined, for a while.
 *
 * <p>It holds no radius of its own, exactly as {@link Blast} holds none: the entity's {@code Body}
 * is both the circle that hurts and the circle the renderer draws, so the ring the player sees
 * cannot drift from the ring that catches him.
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
 * read by {@code SlickSystem}, which is the only thing that takes one away.
 */
public final class Slick implements Component, Pool.Poolable {

    /** How long this circle lasts altogether. */
    public float durationSeconds;

    public float remainingSeconds;

    /** Sets the life of the slick and starts it, returning this component, for fluent spawning. */
    public Slick set(float newDurationSeconds) {
        this.durationSeconds = newDurationSeconds;
        this.remainingSeconds = newDurationSeconds;
        return this;
    }

    /** How much of its life is left, in {@code [0, 1]}, for the renderer to fade it out by. */
    public float fade() {
        return durationSeconds <= 0f ? 0f : remainingSeconds / durationSeconds;
    }

    /** True once the ground is clean again. */
    public boolean spent() {
        return remainingSeconds <= 0f;
    }

    @Override
    public void reset() {
        durationSeconds = 0f;
        remainingSeconds = 0f;
    }
}
