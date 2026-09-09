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
    public float durationSeconds;
    public float commitSeconds;
    public float margin;

    /** Counts down the sidestep itself. Decremented by {@code CooldownSystem} with every other timer. */
    public float remainingSeconds;

    /** Counts down the window before it may answer anything else. */
    public float commitRemaining;

    public float directionX;
    public float directionY;

    /** Sets how it dodges and returns this component, for fluent spawning. */
    public Dodge set(
            boolean newEnabled,
            float newReactionRange,
            float newSpeed,
            float newDurationSeconds,
            float newCommitSeconds,
            float newMargin) {
        this.enabled = newEnabled;
        this.reactionRange = newReactionRange;
        this.speed = newSpeed;
        this.durationSeconds = newDurationSeconds;
        this.commitSeconds = newCommitSeconds;
        this.margin = newMargin;
        return this;
    }

    /** True while the sidestep is still running. */
    public boolean breaking() {
        return remainingSeconds > 0f;
    }

    /** True while it is too committed to answer anything new. */
    public boolean committed() {
        return commitRemaining > 0f;
    }

    /** Commits to one sidestep, in a direction decided once and then held. */
    public void breakAway(float newDirectionX, float newDirectionY) {
        this.directionX = newDirectionX;
        this.directionY = newDirectionY;
        this.remainingSeconds = durationSeconds;
        this.commitRemaining = commitSeconds;
    }

    @Override
    public void reset() {
        enabled = false;
        reactionRange = 0f;
        speed = 0f;
        durationSeconds = 0f;
        commitSeconds = 0f;
        margin = 0f;
        remainingSeconds = 0f;
        commitRemaining = 0f;
        directionX = 0f;
        directionY = 0f;
    }
}
