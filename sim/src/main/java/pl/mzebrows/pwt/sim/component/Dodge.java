package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * A sidestep away from an incoming shot, and how long it is committed to it.
 *
 * <p>The direction is worked out from the shot's geometry at the moment it commits and then held, so
 * that a dodge is one decision rather than a course correction every tick. Re-deciding each tick would
 * make it curve away from a shot and become impossible to lead, which is the opposite of what a
 * learnable dodge is for.
 */
public final class Dodge implements Component, Pool.Poolable {

    public boolean enabled;
    public float reactionRange;
    public float speed;
    public int durationTicks;
    public int commitTicks;
    public float margin;

    /** Counts down the sidestep itself. Decremented by {@code CooldownSystem} with every other timer. */
    public int remainingTicks;

    /** Counts down the window before it may answer anything else. */
    public int commitRemaining;

    public float directionX;
    public float directionY;

    /** Sets how it dodges and returns this component, for fluent spawning. */
    public Dodge set(
            boolean newEnabled,
            float newReactionRange,
            float newSpeed,
            int newDurationTicks,
            int newCommitTicks,
            float newMargin) {
        this.enabled = newEnabled;
        this.reactionRange = newReactionRange;
        this.speed = newSpeed;
        this.durationTicks = newDurationTicks;
        this.commitTicks = newCommitTicks;
        this.margin = newMargin;
        return this;
    }

    /** True while the sidestep is still running. */
    public boolean breaking() {
        return remainingTicks > 0;
    }

    /** True while it is too committed to answer anything new. */
    public boolean committed() {
        return commitRemaining > 0;
    }

    /** Commits to one sidestep, in a direction decided once and then held. */
    public void breakAway(float newDirectionX, float newDirectionY) {
        this.directionX = newDirectionX;
        this.directionY = newDirectionY;
        this.remainingTicks = durationTicks;
        this.commitRemaining = commitTicks;
    }

    @Override
    public void reset() {
        enabled = false;
        reactionRange = 0f;
        speed = 0f;
        durationTicks = 0;
        commitTicks = 0;
        margin = 0f;
        remainingTicks = 0;
        commitRemaining = 0;
        directionX = 0f;
        directionY = 0f;
    }
}
