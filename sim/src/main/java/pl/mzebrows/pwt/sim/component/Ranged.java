package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * How a unit uses the gun it is carrying: what it can see, how close it lets you get, how loosely it
 * shoots, and where it is currently looking.
 *
 * <p>What it fires is on {@link Weapon}; this is the half that is about the creature. Carried by every
 * chasing unit and switched on per strain ({@code enabled}), never added mid-tick. Angles are stored
 * as radians and a cosine, converted once in {@code RangedDef}.
 *
 * <p><strong>The aim and the shot count are runtime state</strong>: the aim swings onto its target
 * and the count tightens the cone. Both reset the moment it loses you.
 */
public final class Ranged implements Component, Pool.Poolable {

    public boolean enabled;

    /** How close what it is hunting must be before it fires, in world units. */
    public float range;

    /** Half the cone its first shot may leave in, in radians. Zero fires dead straight. */
    public float halfSpreadRadians;

    /** Whether it plants itself while its target is in range rather than continuing to close. */
    public boolean haltsToFire;

    /** Whether it refuses to advance on what it is hunting at all: it drives you off, never follows. */
    public boolean holdsGround;

    /** The cosine of half its field of view. Only what is inside it is fired at. */
    public float arcCosine = -1f;

    /** How fast its aim swings onto its target, as a fraction of the angle left to cover per second. */
    public float turnRate;

    /**
     * The distance it is best at, in world units - and therefore the distance it walks to.
     *
     * <p>Inside it the thing fires faster and straighter. Zero switches both halves off.
     */
    public float closeRange;

    /** What being close multiplies both the cooldown and the cone by. */
    public float closeFactor = 1f;

    /** How many shots it takes to walk its aim in. */
    public int settleShots;

    /** What the cone has narrowed to by then, as a fraction of the declared one. */
    public float settledSpreadFactor = 1f;

    /** What it carries instead after eating something explosive, or null if one is worth nothing to it. */
    public @Nullable WeaponKind chargedGun;

    /**
     * Where it is looking, as a unit vector. {@code (0, 0)} means not aiming, and its heading decides
     * its facing.
     */
    public float aimX;

    public float aimY;

    /** How many shots it has put into its current target. Reset the moment it loses one. */
    public int shotsOnTarget;

    /**
     * Whether it has reached the distance it wanted against what it is currently hunting.
     *
     * <p><strong>What "it never chases you" is made of</strong>: a ground-holder moves up once, then
     * holds. It outlives {@link #shotsOnTarget} - leaving its range resets the cone, not the approach.
     * Only losing you outright re-arms it.
     */
    public boolean inPosition;

    /** Sets how this unit shoots and returns this component, for fluent spawning. */
    public Ranged set(
            boolean newEnabled,
            float newRange,
            float newHalfSpreadRadians,
            boolean newHaltsToFire,
            boolean newHoldsGround,
            float newArcCosine,
            float newTurnRate,
            float newCloseRange,
            float newCloseFactor,
            int newSettleShots,
            float newSettledSpreadFactor,
            @Nullable WeaponKind newChargedGun) {
        this.enabled = newEnabled;
        this.range = newRange;
        this.halfSpreadRadians = newHalfSpreadRadians;
        this.haltsToFire = newHaltsToFire;
        this.holdsGround = newHoldsGround;
        this.arcCosine = newArcCosine;
        this.turnRate = newTurnRate;
        this.closeRange = newCloseRange;
        this.closeFactor = newCloseFactor;
        this.settleShots = newSettleShots;
        this.settledSpreadFactor = newSettledSpreadFactor;
        this.chargedGun = newChargedGun;
        this.shotsOnTarget = 0;
        this.inPosition = false;
        // Cleared: called again on evolving, and a strain that stopped shooting must stop aiming.
        this.aimX = 0f;
        this.aimY = 0f;
        return this;
    }

    /**
     * Whether it still wants to close the distance: it has not opened fire on this contact and
     * cannot reach it from here.
     *
     * <p>Only ever true of a shooter that holds ground.
     */
    public boolean takingUpPosition(float distance) {
        return !inPosition && distance > preferredRange();
    }

    /**
     * The distance it wants to be at: the range it shoots best from, or the edge of its reach if it
     * has no preference.
     *
     * <p>Not the edge of its range, or it ambles in and out of reach.
     */
    public float preferredRange() {
        return closeRange > 0f ? closeRange : range;
    }

    /**
     * Forgets the contact entirely: the cone, and the position it took against it.
     *
     * <p>Called when it stops hunting, not when it merely stops shooting.
     */
    public void giveUpPosition() {
        shotsOnTarget = 0;
        inPosition = false;
    }

    /** Points it somewhere to start with, so a fresh shooter is not already staring at you. */
    public Ranged aim(float x, float y) {
        this.aimX = x;
        this.aimY = y;
        return this;
    }

    /** True if it is looking at something in particular rather than wherever it happens to be going. */
    public boolean aiming() {
        return aimX != 0f || aimY != 0f;
    }

    /**
     * Forgets what it had: the aim goes with the count.
     */
    public void loseTarget() {
        shotsOnTarget = 0;
    }

    /**
     * How wide the cone is for the next shot, in radians, given how far off the target is.
     *
     * <p>How long it has had you and how close you are narrow it, multiplied.
     */
    public float halfSpreadFor(float distance) {
        float factor = settled();
        if (closeRange > 0f && distance <= closeRange) {
            factor *= closeFactor;
        }
        return halfSpreadRadians * factor;
    }

    /** How long it may wait before the next shot, given how far off the target is. */
    public int cooldownFor(int cooldownTicks, float distance) {
        return closeRange > 0f && distance <= closeRange
                ? Math.max(1, Math.round(cooldownTicks * closeFactor))
                : cooldownTicks;
    }

    /** How far in its aim has walked, as a fraction of the declared cone: one at the first shot. */
    private float settled() {
        if (settleShots <= 0 || shotsOnTarget <= 0) {
            return 1f;
        }
        if (shotsOnTarget >= settleShots) {
            return settledSpreadFactor;
        }
        float through = (float) shotsOnTarget / settleShots;
        return 1f + (settledSpreadFactor - 1f) * through;
    }

    @Override
    public void reset() {
        enabled = false;
        range = 0f;
        halfSpreadRadians = 0f;
        haltsToFire = false;
        holdsGround = false;
        arcCosine = -1f;
        turnRate = 0f;
        closeRange = 0f;
        closeFactor = 1f;
        settleShots = 0;
        settledSpreadFactor = 1f;
        chargedGun = null;
        aimX = 0f;
        aimY = 0f;
        shotsOnTarget = 0;
        inPosition = false;
    }
}
