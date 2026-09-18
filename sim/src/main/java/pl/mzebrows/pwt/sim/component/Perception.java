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

    /** Whether it can ever stop hunting once it has started. Off, noticing him is a one-way door. */
    public boolean losesInterest;

    /** How close the commander must come before an unaware unit notices him, in world units. */
    public float detectionRange;

    /** How far he must get before a hunting unit gives up, in world units. */
    public float loseRange;

    /** How close something edible has to be before an idle unit goes for it, in world units. */
    public float secondaryRange;

    /**
     * The smallest dot product between this unit's facing and the direction to something for that
     * thing to count as in front of it. Minus one notices all round, which is what every alien does.
     *
     * <p>Stored as a cosine rather than as the arc it came from so the per-tick test is a dot product
     * and a comparison. The conversion happens once, at spawn, in {@code PerceptionDef.arcCosine()}.
     */
    public float arcCosine = -1f;

    /** Whether rock between this unit and what it might notice hides that thing (G0015). */
    public boolean requiresClearLine;

    /** True while this unit is hunting something on the player's side. */
    public boolean aggro;

    /**
     * Where that something was this tick. Meaningless unless {@link #aggro}.
     *
     * <p>A place, re-resolved every tick, never a reference (G0010, G0016). What it is hunting can be
     * shot, reaped and its component recycled between one tick and the next; a unit charging whatever
     * was built from that instance is a bug that reads as the AI hallucinating.
     */
    public float targetX;

    public float targetY;

    /**
     * True while this unit is on its way to somewhere it was provoked from - shot at, struck, or
     * stood next to a point being taken off it.
     *
     * <p>A place and not a target (G0016). What it fights when it gets there is whatever is standing
     * there, decided by contact like every other fight. The trade that buys is written down in the
     * decision: focusing the first attacker until it dies becomes emergent rather than guaranteed.
     */
    public boolean provoked;

    /** Where that was. Meaningless unless {@link #provoked}. */
    public float provokedX;

    public float provokedY;

    /**
     * True while this unit is walking at the ground the level told the other side to come for.
     *
     * <p>The lowest thing that can have its attention: anything it notices, anything that hurt it and
     * anything it could eat all outrank the walk. Where the post is is not kept here - unlike food
     * and a grudge, there is exactly one and it never moves, so the system that drives it reads it
     * from the field rather than from a copy that could go stale.
     */
    public boolean marching;

    /** True while this unit is on its way to something edible. Never true at the same time as aggro. */
    public boolean foraging;

    /** Where that something is. Meaningless unless {@link #foraging}. */
    public float foodX;

    public float foodY;

    /** Sets what it can notice and returns this component, for fluent spawning. */
    public Perception set(
            boolean newAlwaysAggressive,
            boolean newLosesInterest,
            float newDetectionRange,
            float newLoseRange,
            float newSecondaryRange,
            float newArcCosine,
            boolean newRequiresClearLine) {
        this.alwaysAggressive = newAlwaysAggressive;
        this.losesInterest = newLosesInterest;
        this.detectionRange = newDetectionRange;
        this.loseRange = newLoseRange;
        this.secondaryRange = newSecondaryRange;
        this.arcCosine = newArcCosine;
        this.requiresClearLine = newRequiresClearLine;
        return this;
    }

    /** True while something other than standing about has its attention. */
    public boolean occupied() {
        return aggro || provoked || foraging || marching;
    }

    /**
     * Remembers where it is hunting.
     *
     * <p>It used to record whether that was the commander, because routes were built for him alone
     * and anything hunting somebody else had to walk straight at them. The field is built for every
     * man on the player's side now, so there is nothing left for the answer to change.
     */
    public void hunt(float x, float y) {
        this.targetX = x;
        this.targetY = y;
    }

    /**
     * Sends it to deal with whatever is at a place, because something there hurt it.
     *
     * <p>Latched: it keeps walking there until it arrives or the commander turns up, rather than
     * re-deciding every tick. Without the latch, two troopers firing alternately would hold a unit
     * swinging between them instead of pulling it onto either.
     */
    public void provokeAt(float x, float y) {
        this.provoked = true;
        this.provokedX = x;
        this.provokedY = y;
    }

    /** Sends it after something edible at a place, rather than at an entity that may not last the tick. */
    public void feedAt(float x, float y) {
        this.foraging = true;
        this.foodX = x;
        this.foodY = y;
    }

    /** How far the commander may be right now without this unit losing interest, in world units. */
    public float rangeInPlay() {
        return aggro ? loseRange : detectionRange;
    }

    @Override
    public void reset() {
        alwaysAggressive = false;
        losesInterest = false;
        detectionRange = 0f;
        loseRange = 0f;
        secondaryRange = 0f;
        arcCosine = -1f;
        requiresClearLine = false;
        aggro = false;
        targetX = 0f;
        targetY = 0f;
        provoked = false;
        provokedX = 0f;
        provokedY = 0f;
        marching = false;
        foraging = false;
        foodX = 0f;
        foodY = 0f;
    }
}
