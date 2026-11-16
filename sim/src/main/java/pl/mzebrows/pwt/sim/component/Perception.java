package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * What a unit can notice, whether it has noticed, and the man it is after (G0097).
 *
 * <p>{@code aggro} is one latched boolean: hunting, or not. <strong>Who it hunts is asked again every
 * tick and published as a place</strong> (G0010, G0016); {@link #kept} names the man that was, by handle,
 * for one reader only - the question itself, which leans towards him by the switch margin (G0097). Carried
 * permanently, never added mid-tick.
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
     * thing to count as in front of it. Minus one notices all round. Converted once, in
     * {@code PerceptionDef.arcCosine()}.
     */
    public float arcCosine = -1f;

    /** Whether rock between this unit and what it might notice hides that thing (G0015). */
    public boolean requiresClearLine;

    /** How close something must be to be noticed whichever way it is facing, in world units. Zero means none. */
    public float allRoundRange;

    /** True while this unit is hunting something on the player's side. */
    public boolean aggro;

    /**
     * Where that something was this tick. Meaningless unless {@link #aggro}.
     *
     * <p>A place, re-resolved every tick (G0010, G0016).
     */
    public float targetX;

    public float targetY;

    /**
     * The man it is after, by handle: the one it keeps until another is nearer by the switch margin or he goes down
     * (G0097). Nobody until it has hunted somebody, and stale once it stops - which is why the one reader, {@code
     * PerceptionSystem}, hands it to {@code Drivers.nearestLivingTarget} only while {@link #aggro}. Everything else
     * reads the place above.
     */
    public long kept = Handle.NOBODY;

    /**
     * Whether it comes only for a man in the biome it was put down in - the worm (G0096) - and where that was. Every
     * other strain comes for whoever is nearest wherever he stands.
     */
    public boolean ownBiome;

    public float homeX;

    public float homeY;

    /**
     * True while this unit is on its way to somewhere it was provoked from - shot at, struck, or
     * stood next to a point being taken off it.
     *
     * <p>A place and not a target (G0016). Two readers: {@code NavigationSystem} walks there, and
     * {@code BarrageSystem} shoots there, through {@link #hurtFromAPlace()} only.
     */
    public boolean provoked;

    /** Where that was. Meaningless unless {@link #provoked}. */
    public float provokedX;

    public float provokedY;

    /**
     * True while that place is trouble it was sent to look at - a point being taken, a nest being hit - rather
     * than somewhere it was hurt from. Nobody is known to stand there, so nothing shoots at it. Meaningless
     * unless {@link #provoked}.
     */
    public boolean roused;

    /**
     * True while this unit is walking at the ground the level told the other side to come for.
     *
     * <p>The lowest thing that can have its attention. The post's place is read from the field.
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
            boolean newRequiresClearLine,
            float newAllRoundRange) {
        this.alwaysAggressive = newAlwaysAggressive;
        this.losesInterest = newLosesInterest;
        this.detectionRange = newDetectionRange;
        this.loseRange = newLoseRange;
        this.secondaryRange = newSecondaryRange;
        this.arcCosine = newArcCosine;
        this.requiresClearLine = newRequiresClearLine;
        this.allRoundRange = newAllRoundRange;
        return this;
    }

    /** Keeps it to the biome of this place, where it was put down (G0096), returning this component. */
    public Perception keptTo(float x, float y) {
        this.ownBiome = true;
        this.homeX = x;
        this.homeY = y;
        return this;
    }

    /** True while something other than standing about has its attention. */
    public boolean occupied() {
        return aggro || provoked || foraging || marching;
    }

    /** Remembers who it is hunting, and where he is. */
    public void hunt(long who, float x, float y) {
        this.kept = who;
        this.targetX = x;
        this.targetY = y;
    }

    /**
     * Sends it to deal with whatever is at a place, because something there hurt it.
     *
     * <p>Latched until it arrives or the commander turns up, so alternating fire cannot hold it swinging.
     */
    public void provokeAt(float x, float y) {
        this.provoked = true;
        this.roused = false;
        this.provokedX = x;
        this.provokedY = y;
    }

    /**
     * Sends it to see what the trouble is at a place nobody hurt it from - a point being taken, a nest being hit.
     *
     * <p>Latched exactly as {@link #provokeAt}, but nothing it carries shoots at the place.
     */
    public void rouseAt(float x, float y) {
        provokeAt(x, y);
        this.roused = true;
    }

    /** Where it may shoot without having noticed anybody: a place it was hurt from, never one it was roused to. */
    public boolean hurtFromAPlace() {
        return provoked && !roused;
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
        allRoundRange = 0f;
        aggro = false;
        targetX = 0f;
        targetY = 0f;
        kept = Handle.NOBODY;
        ownBiome = false;
        homeX = 0f;
        homeY = 0f;
        provoked = false;
        provokedX = 0f;
        provokedY = 0f;
        roused = false;
        marching = false;
        foraging = false;
        foodX = 0f;
        foodY = 0f;
    }
}
