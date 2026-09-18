package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.sim.def.ActionKind;

/**
 * What the commander could do right now, and how far through doing it he is.
 *
 * <p>Carried by the commander, who acts on a key, and by a trooper, which acts on its own judgement
 * in {@code AidSystem}. One component for "what this pair of hands is doing" means the interface and
 * the renderer learn it once: the green core drawn on a man putting somebody back on his feet is the
 * same mark whether it is the player doing it or one of his men.
 *
 * <p><strong>It names a kind, never a target.</strong> Which casualty he is working on is resolved
 * from scratch every tick by {@code ActionSystem}, exactly as {@code Perception} resolves what a
 * unit is hunting - components are pooled, so an entity reference kept here across ticks could
 * outlive what it pointed at and come back aimed at whatever was built from the recycled instance
 * (G0010). Nothing is lost by re-deciding: if he has walked away from the man he was reviving, the
 * right answer is that he is no longer reviving him.
 *
 * <p>{@link #available} is published for the interface (pillar 5). An action the player cannot see
 * is an action he has to already know about, which is the same as not having it.
 */
public final class Action implements Component, Pool.Poolable {

    /** What the action key would do this tick. {@link ActionKind#NONE} for almost every tick. */
    public ActionKind available = ActionKind.NONE;

    /** How close something has to be, surface to surface, to be within arm's length. */
    public float reach;

    /** How far through the current action he is, in {@code [0, 1]}. Zero when he is doing nothing. */
    public float progress;

    /**
     * Whether the key was held last tick, so that getting on or off a gun happens on the press.
     *
     * <p>The two kinds of action the key does want opposite things from it. Putting a man back on
     * his feet is a <em>hold</em>: the work accumulates while the key is down and is lost the moment
     * it is not. Getting on a turret is an <em>event</em>: without an edge, one press would put him
     * on and take him straight off again, sixty times a second.
     */
    public boolean wasHeld;

    /** Sets how far he can reach and returns this component, for fluent spawning. */
    public Action set(float newReach) {
        this.reach = newReach;
        this.available = ActionKind.NONE;
        this.progress = 0f;
        this.wasHeld = false;
        return this;
    }

    /** Nothing within reach worth doing anything to, and no work in progress. Most ticks. */
    public void idle() {
        this.available = ActionKind.NONE;
        this.progress = 0f;
    }

    @Override
    public void reset() {
        available = ActionKind.NONE;
        reach = 0f;
        progress = 0f;
        wasHeld = false;
    }
}
