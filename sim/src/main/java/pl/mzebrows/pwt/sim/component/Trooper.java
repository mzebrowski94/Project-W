package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.def.Order;

/**
 * One AI-controlled member of the squad, and the whole of what it is thinking.
 *
 * <p>Not a strain: it takes orders, fights with a gun and has nothing to evolve into.
 *
 * <p><strong>What it is doing is a field here, not a component that comes and goes</strong> (G0010).
 * {@code TrooperMindSystem} writes {@link #mode} and where it is headed; {@code TrooperMoveSystem}
 * drives. It walks to a <em>place</em>, never an entity.
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
         * <p>Ranks below a standing order and above the level's objectives.
         */
        AID,
        /**
         * Walking to a gun on a mount, or sitting on one.
         *
         * <p>Ranks below {@link #AID} and above the level's objectives.
         */
        CREW,
        /**
         * Wounded, and walking to a medkit (G0053).
         *
         * <p>Ranks below {@link #AID} and above {@link #CREW}.
         */
        MEND
    }

    public Mode mode = Mode.ADVANCE;

    /** Where it is headed. A place, never an entity. Meaningless unless {@link #hasTarget}. */
    public float targetX;

    public float targetY;

    public boolean hasTarget;

    /**
     * Whether, in a fight, it is walking in on what it is fighting because its gun does not reach it
     * yet, rather than standing to shoot. Never true outside {@link Mode#FIGHT}.
     *
     * <p>Remembered across ticks for the hysteresis: once walking, it walks to well inside the reach.
     */
    public boolean closing;

    /**
     * Which way it is looking, as a unit vector.
     *
     * <p>Published as its {@code Facing} by {@code FacingSystem}: a decision, not a side effect of
     * where it walks, so it can stand still and keep watching.
     */
    public float lookX = 1f;

    public float lookY;

    /**
     * What it was last told to do, or null for nothing standing.
     *
     * <p>A standing instruction, not a queued message: it outranks the level's objectives and never a
     * fight (G0017).
     */
    public @Nullable Order order;

    /**
     * How much further this trooper is pushing on at speed, in world units.
     *
     * <p>A distance, not a countdown: a fight does not spend it.
     */
    public float forwardRemaining;

    /**
     * Which player's slot gave the standing order, so "follow me" means the man who said it.
     *
     * <p>Meaningless while {@link #order} is null; set fresh with every order.
     */
    public int orderedBy;

    /**
     * Whether it has been told to leave the point it is holding and go and take the next one.
     *
     * <p>One "forward" buys one point: spent the moment it reaches the next one.
     */
    public boolean sentOn;

    /**
     * Whether it is holding a point the squad has taken, which is when {@link #holdRemaining} is counting.
     *
     * <p>Cleared when it stands on no taken point. A fight on the point pauses the count.
     */
    public boolean holding;

    /**
     * How much longer it holds before following a commander who has gone on ahead, in ticks (G0040).
     * Drawn afresh each time it starts holding.
     */
    public int holdRemaining;

    /**
     * Whether it is timing a walk for progress, from {@link #sampleX}, {@link #sampleY}. Cleared
     * whenever it stands still on purpose, so a man who stopped is never counted as a man who is stuck.
     */
    public boolean sampling;

    /** Where it stood when the current sample began. */
    public float sampleX;

    public float sampleY;

    /** How long the current sample has run, in ticks. */
    public int sampleClock;

    /** How much longer it is backing off to one side before trying its way again, in ticks. */
    public int detourRemaining;

    /** How many detours it has taken. Its parity picks the side, so a second try goes the other way. */
    public int detours;

    /**
     * How much longer it is looking all round for whatever hit it, in ticks. Zero is calm.
     *
     * <p>Written where damage lands, read by the mind to forgive the arc and by the legs to turn the
     * man towards {@link #alertX}, {@link #alertY}.
     */
    public int alertRemaining;

    /** Where the last hit came from: a place, the muzzle, never who fired it (G0016). */
    public float alertX;

    public float alertY;

    /**
     * Whether it has already picked a bearing to watch since it last stopped.
     *
     * <p>Every stop picks a bearing of its own; cleared the moment it moves again.
     */
    public boolean settled;

    /**
     * The direction it is looking before its sweep is applied.
     *
     * <p>The sweep leans off this, so a trooper holding still keeps watching the same way.
     */
    public float baseLookX = 1f;

    public float baseLookY;

    /**
     * What this trooper can notice, as the tests it declares (G0015).
     *
     * <p>Not a {@code Perception}, which answers "am I hunting somebody". The tests are {@code Sight}'s.
     */
    public float sightRange;

    /**
     * How far a fight can drift before the trooper gives it up and goes back to work.
     *
     * <p>Never below the sight range (hysteresis, as {@code PerceptionDef}). Checked on range alone.
     */
    public float holdRange;

    public float arcCosine = -1f;

    public boolean requiresClearLine;

    /** How long until it sweeps its attention the other way, in ticks. */
    public int scanRemaining;

    /** Which way it is currently leaning: {@code +1} or {@code -1}. */
    public int scanSide = 1;

    /**
     * The first compass slot of its <strong>sector</strong>, or {@code -1} for none.
     *
     * <p>Men standing on a point split the circle between them. Written by {@code TrooperMindSystem}
     * once a tick.
     */
    public int sectorFrom = -1;

    /** How many compass slots its sector covers. Zero means no sector, and a bearing of its own instead. */
    public int sectorSpan;

    /**
     * How many times it has swung its attention since it started watching, which picks the slot inside
     * its sector it is looking at now. Written by {@code TrooperMoveSystem} as the sweep turns over.
     */
    public int sectorStep;

    /**
     * What it is still due before it next swings at a leap, {@code Odds}' budget for
     * {@code AiChance.SWING_AT_A_LEAP}. Zero until the first time the chance comes up.
     */
    public float swingDue;

    /**
     * What it is still due before it next swings at whatever has it in its jaws, for
     * {@code AiChance.STRUGGLE} (G0045).
     */
    public float struggleDue;

    /** What it is still due before it next gets on a gun, for {@code AiChance.MAN_A_TURRET}. */
    public float turretDue;

    /**
     * What it is still due before it next goes to pick somebody up. One budget for both rescues,
     * {@code AiChance.AID_A_TROOPER} and {@code AID_THE_COMMANDER}: it is one willingness, spent faster
     * when the man on the ground is the commander.
     */
    public float aidDue;

    /** What it is still due before it next throws a grenade, for {@code AiChance.THROW_A_GRENADE} (G0051). */
    public float throwDue;

    /** Sets what this trooper can notice, and returns this component, for fluent spawning. */
    public Trooper sees(float newSightRange, float newHoldRange, float newArcCosine, boolean newRequiresClearLine) {
        this.sightRange = newSightRange;
        this.holdRange = newHoldRange;
        this.arcCosine = newArcCosine;
        this.requiresClearLine = newRequiresClearLine;
        return this;
    }

    /** Starts it looking for whatever hit it, from that place, for that long. */
    public void hitFrom(float x, float y, int ticks) {
        this.alertX = x;
        this.alertY = y;
        this.alertRemaining = ticks;
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
        closing = false;
        lookX = 1f;
        lookY = 0f;
        scanRemaining = 0;
        scanSide = 1;
        sectorFrom = -1;
        sectorSpan = 0;
        sectorStep = 0;
        swingDue = 0f;
        struggleDue = 0f;
        turretDue = 0f;
        aidDue = 0f;
        throwDue = 0f;
        order = null;
        orderedBy = 0;
        sentOn = false;
        holding = false;
        holdRemaining = 0;
        sampling = false;
        sampleX = 0f;
        sampleY = 0f;
        sampleClock = 0;
        detourRemaining = 0;
        detours = 0;
        alertRemaining = 0;
        alertX = 0f;
        alertY = 0f;
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
