package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * What a unit can notice, and whether it has noticed.
 *
 * <p>{@code aggro} is the whole of a unit's mind: hunting, or not. It is one latched boolean rather
 * than a mode enum on purpose - a second field saying the same thing is a second field that can
 * disagree with the first. When a unit has somewhere else to be than "after him" or "around here",
 * that is the moment a mode earns its own field, and not before.
 *
 * <p><strong>Nothing here holds a reference to what it noticed.</strong> The target is resolved from
 * scratch every tick and only the latch survives between ticks. Components are pooled, so an entity
 * reference kept across ticks can outlive the entity and come back pointing at whatever was built
 * from the recycled instance - a bug that reads as a unit occasionally charging something that is not
 * there.
 *
 * <p>Carried permanently by anything that can notice, rather than added when it first does. Adding or
 * removing a component mid-tick mutates the families being iterated, which silently skips entities.
 */
public final class Perception implements Component, Pool.Poolable {

    /** Whether this unit skips noticing altogether and hunts from the first tick. */
    public boolean alwaysAggressive;

    /** How close the commander must come before an unaware unit notices him, in world units. */
    public float detectionRange;

    /** How far he must get before a hunting unit gives up, in world units. */
    public float loseRange;

    /** True while this unit is hunting the commander. */
    public boolean aggro;

    /** Sets what it can notice and returns this component, for fluent spawning. */
    public Perception set(boolean newAlwaysAggressive, float newDetectionRange, float newLoseRange) {
        this.alwaysAggressive = newAlwaysAggressive;
        this.detectionRange = newDetectionRange;
        this.loseRange = newLoseRange;
        return this;
    }

    /** How far the commander may be right now without this unit losing interest, in world units. */
    public float rangeInPlay() {
        return aggro ? loseRange : detectionRange;
    }

    @Override
    public void reset() {
        alwaysAggressive = false;
        detectionRange = 0f;
        loseRange = 0f;
        aggro = false;
    }
}
