package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * How a unit uses the gun it is carrying: what it can see, how close it lets you get, how loosely it
 * shoots, and where it is currently looking.
 *
 * <p>What it fires is on {@link Weapon}, which is the same component a trooper and the commander
 * carry and is filled from the same table. This is only the half that is a fact about the creature
 * rather than about the gun.
 *
 * <p>Carried by every chasing unit and switched on per strain, exactly as {@link Leap},
 * {@link Wander} and {@link Avoidance} are. A strain that does not shoot reports {@code enabled}
 * false and costs one comparison; nothing adds or removes a component to make a unit a shooter,
 * because changing an entity's component set mid-tick silently skips entities.
 *
 * <p>The cone is held as half its width in radians and the field of view as a cosine, rather than as
 * degrees, because that is what the firing system wants and because converting either in a
 * per-entity loop would mean a transcendental in a hot path, which is banned here. Both conversions
 * happen once, at spawn, in {@code RangedDef}.
 *
 * <p><strong>Two of these fields are runtime state rather than configuration</strong>, and they are
 * what make a shooter something to get away from rather than something to stand in front of. The aim
 * is where it is actually looking, swung onto its target over time; the shot count is how long it has
 * had you, which is what tightens the cone. Both are reset the moment it loses you - and losing it is
 * therefore the counter to it, which is the whole point.
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
     * <p>Inside it the thing fires faster and straighter, and a shooter that holds ground closes to
     * it rather than to the edge of its reach. One number with one meaning: this is where it wants to
     * be. Zero switches both halves off, leaving a shooter that shoots the same everywhere and stops
     * as soon as it can reach.
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
     * Where it is looking, as a unit vector. Published for the renderer, and the thing its field of
     * view is measured from.
     *
     * <p>{@code (0, 0)} means it is not aiming at anything and whatever else decides its facing -
     * where it is walking - has it. That is what makes an idling shooter look like it is idling.
     */
    public float aimX;

    public float aimY;

    /** How many shots it has put into its current target. Reset the moment it loses one. */
    public int shotsOnTarget;

    /**
     * Whether it has reached the distance it wanted against what it is currently hunting.
     *
     * <p><strong>This is what "it never chases you" is actually made of.</strong> A shooter that
     * holds ground still has to cross the gap between noticing you and being able to fight you - it
     * notices at thirty and is best at nine - so a creature that refused to move at all would stand
     * and watch. It moves up once, and from the moment it has the ground it wanted it holds that
     * ground however far you then go.
     *
     * <p>It therefore outlives {@link #shotsOnTarget} on purpose. Stepping out of its range resets
     * the cone - a shooter has to walk its aim in again - but it does <em>not</em> re-arm the
     * approach, or backing off would simply pull the thing after you, which is the behaviour holding
     * ground exists to rule out. Only losing you outright does that.
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
        // Cleared, not kept. This is called again when a unit evolves, and a strain that has stopped
        // being a shooter must stop publishing a facing that nothing is driving any more.
        this.aimX = 0f;
        this.aimY = 0f;
        return this;
    }

    /**
     * Whether it still wants to close the distance: it has not opened fire on this contact and
     * cannot reach it from here.
     *
     * <p>Only ever true of a shooter that holds ground - everything else closes for its own reasons
     * and never asks. What it buys is a creature that walks up to a fight and then defends the ground
     * it chose, rather than one that either charges you or stands rooted where it happened to spawn.
     */
    public boolean takingUpPosition(float distance) {
        return !inPosition && distance > preferredRange();
    }

    /**
     * The distance it wants to be at: the range it shoots best from, or the edge of its reach if it
     * has no preference.
     *
     * <p>Not the edge of its range, which is where it <em>can</em> shoot from. Stopping there leaves
     * it ambling in and out of its own reach, firing on about half the ticks it should - and a
     * creature that walks all the way up to a fight and then cannot hold a shot is worse than one
     * that stayed put. {@link #closeRange} is already the distance it is best at, so it is the
     * distance it goes to; a shooter that names none settles for being able to reach at all.
     */
    public float preferredRange() {
        return closeRange > 0f ? closeRange : range;
    }

    /**
     * Forgets the contact entirely: the cone, and the position it took against it.
     *
     * <p>Called when it stops hunting rather than when it merely stops shooting. That is the whole
     * difference between "you got out of its way" and "you got away".
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
     * Forgets what it had. The aim goes with the count, so a shooter that has lost you stops staring
     * at where you were - and has to walk its aim in again from the first wild shot when it finds you.
     */
    public void loseTarget() {
        shotsOnTarget = 0;
    }

    /**
     * How wide the cone is for the next shot, in radians, given how far off the target is.
     *
     * <p>Two things narrow it and they multiply: how long it has had you, and how close you are.
     * Neither is a special case in the firing system - both are numbers a strain declares, and a
     * strain that declares neither shoots exactly as loosely on its tenth shot as on its first.
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
