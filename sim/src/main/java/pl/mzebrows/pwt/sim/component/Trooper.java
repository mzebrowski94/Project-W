package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.def.Order;

/**
 * One AI-controlled member of the squad, and the whole of what it is thinking.
 *
 * <p>A trooper is not a strain. Grunt and brute are forms of one creature and differ only in numbers,
 * which is why they share a {@code UnitKind} and a definition table; a trooper is on the other side,
 * fights with a gun rather than its body, takes orders, and has nothing to evolve into. Making it a
 * strain would mean every strain carrying a feed threshold and a leap it can never use, and would put
 * troopers inside {@code everyStrain(...)} in the fixtures, quietly reconfiguring the squad in every
 * test about aliens.
 *
 * <p><strong>What it is doing is a field here, not a component that comes and goes</strong> (G0010).
 * One system writes {@link #mode} and where it is headed; another reads them and drives. Nothing
 * holds a reference to what it is interested in - a trooper walks to a <em>place</em>, so a point
 * reaped between ticks cannot come back as whatever was built from the recycled instance.
 */
public final class Trooper implements Component, Pool.Poolable {

    /**
     * What a trooper is doing. Chosen once a tick, upstream, by mutually exclusive conditions rather
     * than by priority - so there is no arbitration to get wrong (G0010).
     */
    public enum Mode {
        /** Walking to somewhere the level wants taken, looking around on the way. */
        ADVANCE,
        /** Nothing left to take. Roaming the map, looking for whatever is still alive. */
        EXPLORE,
        /** Something in front of it. Standing its ground and shooting (1.d). */
        FIGHT,
        /** Keeping up with the commander, loosely (3.a). */
        FOLLOW,
        /** Told to hold here. Stationary, still watching (3.c). */
        HOLD,
        /** Standing on a point it is taking. Stationary, watching, and not walking at the middle. */
        SECURE,
        /**
         * Going to pick somebody up, or standing over him doing it.
         *
         * <p>One mode for both halves, because they differ only in whether it has arrived - which is
         * a distance, not a state to keep. It ranks below a standing order and above the level's
         * objectives: a man told to hold a corner holds it while somebody bleeds, and a squad with
         * nothing but an objective to walk to goes and fetches him.
         */
        AID,
        /**
         * Walking to a gun on a mount, or sitting on one.
         *
         * <p>One mode for both halves, as {@link #AID} is: they differ only in whether it has
         * arrived. It ranks below going to fetch somebody and above the level's objectives - a gun
         * is worth leaving a walk for and never worth leaving a man on the ground for.
         */
        CREW
    }

    public Mode mode = Mode.ADVANCE;

    /** Where it is headed. A place, never an entity. Meaningless unless {@link #hasTarget}. */
    public float targetX;

    public float targetY;

    public boolean hasTarget;

    /**
     * Which way it is looking, as a unit vector.
     *
     * <p>Written by whatever is driving it and published by {@code AppearanceSystem}, so a trooper's
     * facing is a decision rather than a side effect of where it happens to be walking. That matters
     * for two things it will be asked to do: engage only what is in front of it, and stand still
     * while still looking around.
     */
    public float lookX = 1f;

    public float lookY;

    /**
     * What it was last told to do, or null for nothing standing.
     *
     * <p>A standing instruction rather than a queued message. It outranks the level's objectives and
     * never an ongoing fight - and because what a trooper is doing is chosen fresh every tick, with
     * the fight looked at first, "carry it out once the shooting stops" needs no queue and no memory
     * of having been interrupted (G0017).
     */
    public @Nullable Order order;

    /**
     * How much further this trooper is pushing on at speed, in world units.
     *
     * <p>A distance rather than a countdown, because "forward" is about ground covered: a squad told
     * to push on and then held up by a fight should still get its full push afterwards, rather than
     * having spent it standing still.
     */
    public float forwardRemaining;

    /**
     * Which player's slot gave the standing order, so "follow me" means the man who said it.
     *
     * <p>A number rather than the commander himself: a component may not hold an entity across ticks
     * (G0016), and the commander this refers to is one of the likeliest in the game to be killed and
     * recycled. It is meaningless while {@link #order} is null, and is set fresh every time an order
     * lands, so a stale slot is never read.
     */
    public int orderedBy;

    /**
     * Whether it has been told to leave the point it is holding and go and take the next one.
     *
     * <p>A trooper standing on a <strong>captured</strong> point waits there; "forward" is what sends
     * it on, and one order buys one point. Spent the moment it reaches the next point, so the squad
     * settles again rather than running the level on its own.
     *
     * <p>It is a flag rather than a distance, which {@code forwardRemaining} beside it already is:
     * the two halves of "forward" are a burst of pace, which is ground, and permission to leave, which
     * is not. A distance cannot express the second - the next point may be one tile away or thirty.
     */
    public boolean sentOn;

    /**
     * Whether it has already picked a bearing to watch since it last stopped.
     *
     * <p>A trooper that stops takes a bearing of its own rather than keeping the one it was walking
     * on. Without that, a squad told to follow all stares at the commander and a squad on a point all
     * stares at the middle of it, which reads as four men looking at the same thing rather than as a
     * squad watching its surroundings. Cleared the moment it moves again, so every stop picks afresh.
     */
    public boolean settled;

    /**
     * The direction it is looking before its sweep is applied.
     *
     * <p>Kept apart from {@link #lookX} so that a trooper told to hold still can go on watching: the
     * sweep leans off this, and with nothing driving it this stays put instead of decaying to
     * wherever the last lean left it.
     */
    public float baseLookX = 1f;

    public float baseLookY;

    /**
     * What this trooper can notice, as the tests it declares (G0015).
     *
     * <p>Its own fields rather than a {@code Perception} component, because that component answers a
     * different question - "am I hunting the commander" - and is driven by a system built around him.
     * A trooper asks "is there anything in front of me to shoot", which is the same three tests
     * applied to a different subject. The tests are shared in {@code Sight}; only the numbers live
     * here.
     */
    public float sightRange;

    /**
     * How far a fight can drift before the trooper gives it up and goes back to work.
     *
     * <p>Never below the sight range, for the reason {@code PerceptionDef} gives at length: one
     * number would make a trooper standing at exactly that distance start and stop fighting every
     * tick. Once it is fighting, this is checked on range alone - turning your head must not make you
     * forget what you are shooting at.
     */
    public float holdRange;

    public float arcCosine = -1f;

    public boolean requiresClearLine;

    /** How long until it sweeps its attention the other way, in seconds. */
    public float scanRemaining;

    /** Which way it is currently leaning: {@code +1} or {@code -1}. */
    public int scanSide = 1;

    /**
     * What it is still due before it next swings at a leap, {@code Odds}' budget for
     * {@code AiChance.SWING_AT_A_LEAP}. Zero until the first time the chance comes up.
     */
    public float swingDue;

    /** What it is still due before it next gets on a gun, for {@code AiChance.MAN_A_TURRET}. */
    public float turretDue;

    /**
     * What it is still due before it next goes to pick somebody up. One budget for both rescues,
     * {@code AiChance.AID_A_TROOPER} and {@code AID_THE_COMMANDER}: it is one willingness, spent faster
     * when the man on the ground is the commander.
     */
    public float aidDue;

    /** Sets what this trooper can notice, and returns this component, for fluent spawning. */
    public Trooper sees(float newSightRange, float newHoldRange, float newArcCosine, boolean newRequiresClearLine) {
        this.sightRange = newSightRange;
        this.holdRange = newHoldRange;
        this.arcCosine = newArcCosine;
        this.requiresClearLine = newRequiresClearLine;
        return this;
    }

    /** Points it somewhere, and returns this component, for fluent spawning. */
    public Trooper look(float x, float y) {
        this.lookX = x;
        this.lookY = y;
        this.baseLookX = x;
        this.baseLookY = y;
        return this;
    }

    /** Whether it is pushing on at speed. */
    public boolean pushingForward() {
        return forwardRemaining > 0f;
    }

    /** Sends it to a place. */
    public void headFor(float x, float y) {
        this.hasTarget = true;
        this.targetX = x;
        this.targetY = y;
    }

    /** Gives it nowhere to be, which is how it stands still. */
    public void clearTarget() {
        hasTarget = false;
    }

    @Override
    public void reset() {
        mode = Mode.ADVANCE;
        targetX = 0f;
        targetY = 0f;
        hasTarget = false;
        lookX = 1f;
        lookY = 0f;
        scanRemaining = 0f;
        scanSide = 1;
        swingDue = 0f;
        turretDue = 0f;
        aidDue = 0f;
        order = null;
        orderedBy = 0;
        sentOn = false;
        settled = false;
        forwardRemaining = 0f;
        baseLookX = 1f;
        baseLookY = 0f;
        sightRange = 0f;
        holdRange = 0f;
        arcCosine = -1f;
        requiresClearLine = false;
    }
}
