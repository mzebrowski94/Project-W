package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.sim.def.BurrowDef;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.Ticks;

/**
 * A strain that burrows (G0096): whether it is under the ground or up, its heading and bend under it, its rest, where
 * its maw is closing and how many teeth it has left.
 *
 * <p>The configuration half is per strain, written with it by {@code applyStrain}; the rest is {@code BurrowSystem}'s,
 * which also says through {@link Collider} what a burrowing body is to everything else - nothing, under the ground.
 */
public final class Burrow implements Component, Pool.Poolable {

    /** Where it is in its cycle. */
    public enum Phase {
        /** Under the ground: a shadow nothing can hurt, always on the move. */
        BURROWED,
        /** Coming up: its maw closing on where its man stood - the tell. It can be hurt, and is not yet solid. */
        SURFACING,
        /** In its hole after the snap, solid and immovable as a rooted strain, until it dives. */
        STANDING,
        /** Held up by a declared stun that broke its snap off: hurt as it is up, not solid, diving once it runs out. */
        HELD
    }

    /** Whether this strain burrows at all. */
    public boolean enabled;

    /** How many teeth its maw has when whole, and how many it has left - its health shared out, rounded up. */
    public int teeth;

    public int teethLeft;

    public Phase phase = Phase.BURROWED;

    /** Ticks left of the phase it is in; unused while it burrows. */
    public int phaseTicks;

    /** Ticks left of its rest, in which it may not surface; counted down while it burrows. */
    public int restTicks;

    /** How long the tell and the stand last, and the rest's range, in ticks. */
    public int tellTicks;

    public int standTicks;
    public int restMinTicks;
    public int restMaxTicks;

    /** How fast it goes with nobody to come for, in world units a second. */
    public float idleSpeed;

    /** How near its man its middle must be to surface under him. */
    public float surfaceReach;

    /** How wide its maw opens, as a multiple of its body. Read by the view. */
    public float mawScale;

    /** What its snap does to a man inside the maw. */
    public Harm harm = Harm.WOUND;

    /** One tick's turn onto its man, and one tick's bend with nobody, as cosine and sine. */
    public float huntCos;

    public float huntSin;
    public float turnCos;
    public float turnSin;

    /** How many ticks of its hunting turn take it a quarter of the way round: how far ahead it looks for an edge. */
    public int quarterTurnTicks;

    /** How far from home it bends about before it bends back. */
    public float roam;

    /** How long one bend lasts, in ticks, drawn between these. */
    public int bendMinTicks;

    public int bendMaxTicks;

    /** The bend it is in - -1 to the right, 0 straight on, 1 to the left - and the ticks left of it. */
    public int bend;

    public int bendTicks;

    /** Where it was put down. */
    public float homeX;

    public float homeY;

    /** Which way it is going, of unit length; set from its facing the first tick it moves. */
    public float headingX;

    public float headingY;

    /**
     * Where it is looking while it is up, of unit length: at its man, turned onto him at its hunting turn, or about it
     * with nobody - so a worm in its hole is never frozen. Read by {@code FacingSystem}; meaningless under the ground.
     */
    public float lookX;

    public float lookY;

    /** Where its maw is closing: where its man stood the tick it came up. */
    public float mawX;

    public float mawY;

    /** Sets the configuration from the strain's definition, for spawning and evolving alike. */
    public Burrow set(BurrowDef def, float tickSeconds) {
        this.enabled = def.enabled();
        this.teeth = def.teeth();
        this.teethLeft = def.teeth();
        this.phase = Phase.BURROWED;
        this.phaseTicks = 0;
        this.restTicks = 0;
        this.tellTicks = Math.max(1, Ticks.of(def.tellSeconds(), tickSeconds));
        this.standTicks = Ticks.of(def.standSeconds(), tickSeconds);
        this.restMinTicks = Ticks.of(def.restMinSeconds(), tickSeconds);
        this.restMaxTicks = Ticks.of(def.restMaxSeconds(), tickSeconds);
        this.idleSpeed = def.idleSpeed();
        this.surfaceReach = def.surfaceReach();
        this.mawScale = def.mawScale();
        this.harm = def.harm();
        float hunt = (float) StrictMath.toRadians(def.huntTurnDegrees()) * tickSeconds;
        float turn = (float) StrictMath.toRadians(def.turnDegrees()) * tickSeconds;
        this.huntCos = (float) StrictMath.cos(hunt);
        this.huntSin = (float) StrictMath.sin(hunt);
        this.turnCos = (float) StrictMath.cos(turn);
        this.turnSin = (float) StrictMath.sin(turn);
        this.quarterTurnTicks = def.huntTurnDegrees() > 0f ? Ticks.of(90f / def.huntTurnDegrees(), tickSeconds) : 0;
        this.roam = def.roam();
        this.bendMinTicks = Ticks.of(def.bendMinSeconds(), tickSeconds);
        this.bendMaxTicks = Ticks.of(def.bendMaxSeconds(), tickSeconds);
        this.bend = 0;
        this.bendTicks = 0;
        return this;
    }

    /** Puts its home where it stands. Its heading is taken from its facing the first tick it moves. */
    public Burrow home(float x, float y) {
        this.homeX = x;
        this.homeY = y;
        this.headingX = 0f;
        this.headingY = 0f;
        this.mawX = x;
        this.mawY = y;
        this.lookX = 0f;
        this.lookY = 0f;
        return this;
    }

    /** Whether it is under the ground: nothing lands on it, nothing fires at it, nothing it meets meets it. */
    public boolean burrowed() {
        return enabled && phase == Phase.BURROWED;
    }

    /** Whether it is up and looking about - not under the ground. */
    public boolean looking() {
        return enabled && phase != Phase.BURROWED;
    }

    /** How much of its maw's closing is left, in {@code [0, 1]}: one as it comes up, zero at the snap and after. */
    public float mawLeft() {
        if (phase != Phase.SURFACING || tellTicks <= 0) {
            return 0f;
        }
        return Math.min(1f, phaseTicks / (float) tellTicks);
    }

    @Override
    public void reset() {
        enabled = false;
        teeth = 0;
        teethLeft = 0;
        phase = Phase.BURROWED;
        phaseTicks = 0;
        restTicks = 0;
        tellTicks = 0;
        standTicks = 0;
        restMinTicks = 0;
        restMaxTicks = 0;
        idleSpeed = 0f;
        surfaceReach = 0f;
        mawScale = 0f;
        harm = Harm.WOUND;
        huntCos = 0f;
        huntSin = 0f;
        turnCos = 0f;
        turnSin = 0f;
        quarterTurnTicks = 0;
        roam = 0f;
        bendMinTicks = 0;
        bendMaxTicks = 0;
        bend = 0;
        bendTicks = 0;
        homeX = 0f;
        homeY = 0f;
        headingX = 0f;
        headingY = 0f;
        mawX = 0f;
        mawY = 0f;
        lookX = 0f;
        lookY = 0f;
    }
}
