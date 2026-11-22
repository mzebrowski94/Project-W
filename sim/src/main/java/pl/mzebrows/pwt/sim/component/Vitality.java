package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.sim.def.Harm;

/**
 * How hurt a man on the player's side is: a count of wounds against his maximum, and whether he is
 * down (G0053). He has no health - a blow's amount is never read here, only its {@link Harm}.
 *
 * <p><strong>Standing or down is the whole state machine</strong>; dying and dead are the throes and
 * the reaper, as for everything else. Wounded is not a state: it is a standing man with wounds above
 * zero, who does everything a standing man does. What a harm does is {@link #take}, and nothing else
 * changes a man's wounds or puts him down.
 *
 * <p>Down is a <strong>casualty</strong>: he lies where he fell, bleeding out, and can be put back on
 * his feet by somebody who reaches him in time. The bleed-out is counted by {@code CasualtySystem},
 * not {@code CooldownSystem}, because its rate varies while he is dragged; the grace is an ordinary
 * countdown and is {@code CooldownSystem}'s. Carried permanently by every man; whether he is down is asked
 * of {@link pl.mzebrows.pwt.sim.Life}, never of {@link #begun}. No alien carries it.
 *
 * <p><strong>Both clocks count whole ticks</strong>, converted once where the seconds enter, in
 * hundredths of a tick ({@link #TICK}) so a rate other than one - a drag that slows the bleed, First Aid
 * that speeds the work - is a whole number too. Each takes exactly as many ticks as its number says.
 */
public final class Vitality implements Component, Pool.Poolable {

    /** What one blow did to him. */
    public enum Taken {
        /** Nothing: a wound in grace, or a wound on a man who is down. */
        NOTHING,
        /** One more wound, and he is still on his feet. */
        WOUNDED,
        /** Put down this tick. */
        DOWNED,
        /** Killed: a killing blow, or a blow that finished a man who was down. */
        KILLED
    }

    /** One tick of either clock at the ordinary rate, in the hundredths both are counted in. */
    public static final int TICK = 100;

    /** How long he has before he bleeds out, once he goes down, in ticks. Configuration, not state. */
    public int bleedOutTicks;

    /**
     * How much of his bleed-out must have run before he may redeploy - end it himself, as running out would (G0109) -
     * in ticks. Zero is never: a trooper's. Configuration, set only on a commander by {@link #redeploysAfter}.
     */
    public int redeployAfterTicks;

    /** How long somebody has to stand over him to put him back on his feet, in ticks. Configuration. */
    public int stabiliseTicks;

    /**
     * The same, once he has been hauled, in ticks. Configuration.
     *
     * <p>Hauling stops his clock; this is what it costs instead.
     */
    public int hauledStabiliseTicks;

    /**
     * How many wounds he carries on his feet before any upgrade; one more puts him down. Configuration: the
     * base {@code DamageSystem.carries} folds through the {@code MAX_WOUNDS} stat (ADR 0017).
     */
    public int maxWounds;

    /** How long one wound protects him from the next, in ticks. Configuration. */
    public int graceTicks;

    /** How much of his pace he keeps while wounded. Configuration. */
    public float woundedPace = 1f;

    /** What being wounded multiplies the time between his shots by. Configuration. */
    public float woundedCooldown = 1f;

    public boolean begun;

    /** What is left of his bleed-out, in hundredths of a tick. */
    public int remaining;

    /**
     * The work done on him so far, in hundredths of a tick. Back to nothing the moment the work stops.
     */
    public int stabilised;

    /** The wounds he is carrying. */
    public int wounds;

    /** Ticks of grace left, during which another wound cannot land. */
    public int graceRemaining;

    /** Ticks since his last wound, while he is wounded; his wounds mend once it is long enough. */
    public int mendTicks;

    /** Sets how he is hurt and patched up, for fluent spawning. */
    public Vitality set(
            int newBleedOutTicks,
            int newStabiliseTicks,
            int newHauledStabiliseTicks,
            int newMaxWounds,
            int newGraceTicks,
            float newWoundedPace,
            float newWoundedCooldown) {
        this.bleedOutTicks = newBleedOutTicks;
        this.stabiliseTicks = newStabiliseTicks;
        this.hauledStabiliseTicks = newHauledStabiliseTicks;
        this.maxWounds = newMaxWounds;
        this.graceTicks = newGraceTicks;
        this.woundedPace = newWoundedPace;
        this.woundedCooldown = newWoundedCooldown;
        this.redeployAfterTicks = 0;
        this.begun = false;
        this.remaining = 0;
        this.stabilised = 0;
        this.wounds = 0;
        this.graceRemaining = 0;
        this.mendTicks = 0;
        return this;
    }

    /** Lets him redeploy once this many ticks of his bleed-out have run (G0109), and returns this, for fluent spawning. */
    public Vitality redeploysAfter(int ticks) {
        this.redeployAfterTicks = ticks;
        return this;
    }

    /**
     * Whether he may redeploy now (G0109): down, with time still left, and at least {@link #redeployAfterTicks} of his
     * bleed-out run. Counted in the bleed-out's own hundredths, so a haul that stops his clock holds the window shut
     * until enough of it has run in all.
     */
    public boolean redeployOpen() {
        return redeployAfterTicks > 0 && active() && bleedOutTicks * TICK - remaining >= redeployAfterTicks * TICK;
    }

    /**
     * What one tick's worst blow does to him - the whole of G0053's table.
     *
     * <p>A harmless blow is nothing (G0057). A wound in grace, or on a man who is down, is nothing. A
     * wound on a standing man adds one, and one past his maximum puts him down; otherwise it starts his
     * grace. A down blow puts a standing man down whatever his wounds. A down or a killing blow finishes
     * a man who is down, and a killing blow kills a standing one.
     *
     * @param carries how many wounds he carries on his feet now, upgrades included
     */
    public Taken take(Harm harm, int carries) {
        if (begun) {
            return harm == Harm.NONE || harm == Harm.WOUND ? Taken.NOTHING : Taken.KILLED;
        }
        return switch (harm) {
            case NONE -> Taken.NOTHING;
            case KILL -> Taken.KILLED;
            case DOWN -> {
                begin();
                yield Taken.DOWNED;
            }
            case WOUND -> wound(carries);
        };
    }

    private Taken wound(int carries) {
        if (graceRemaining > 0) {
            return Taken.NOTHING;
        }
        wounds++;
        mendTicks = 0;
        if (wounds > carries) {
            begin();
            return Taken.DOWNED;
        }
        graceRemaining = graceTicks;
        return Taken.WOUNDED;
    }

    /**
     * Whether a wound landed on him this tick: his grace has just started and not yet run a tick. What loses the
     * work on a door he is opening (G0069). Read after {@code DamageSystem}.
     */
    public boolean woundedThisTick() {
        return graceTicks > 0 && graceRemaining == graceTicks && wounded();
    }

    /** True while he is on his feet with at least one wound. */
    public boolean wounded() {
        return !begun && wounds > 0;
    }

    /** How much of his pace he keeps right now: one unless he is wounded. Read only by {@code Pace}. */
    public float paceFactor() {
        return wounded() ? woundedPace : 1f;
    }

    /** What his gun's time between shots is multiplied by right now. Read only by {@code Guns}. */
    public float cooldownFactor() {
        return wounded() ? woundedCooldown : 1f;
    }

    /**
     * How much work this man takes, in hundredths of a tick, which depends on whether he has been hauled
     * anywhere.
     */
    public int stabiliseNeeded(boolean hauled) {
        return (hauled ? hauledStabiliseTicks : stabiliseTicks) * TICK;
    }

    /** How far through being patched up he is, in {@code [0, 1]}. */
    public float stabiliseProgress(boolean hauled) {
        int needed = stabiliseNeeded(hauled);
        if (needed <= 0) {
            return 0f;
        }
        float done = (float) stabilised / needed;
        return done < 1f ? done : 1f;
    }

    /** True once somebody has stood over him long enough. */
    public boolean stabilisedEnough(boolean hauled) {
        return begun && stabilised >= stabiliseNeeded(hauled);
    }

    /**
     * Puts him back on his feet with every wound cleared - the one way anything heals a man (G0053).
     */
    public void recover() {
        begun = false;
        remaining = 0;
        stabilised = 0;
        wounds = 0;
        graceRemaining = 0;
        mendTicks = 0;
    }

    /**
     * Puts him down. Ignored if he is already down, so a second blow in the same tick cannot restart
     * the clock.
     */
    public void begin() {
        if (begun) {
            return;
        }
        begun = true;
        remaining = bleedOutTicks * TICK;
        stabilised = 0;
        graceRemaining = 0;
    }

    /**
     * One tick of his bleed-out, at this rate in hundredths of a tick: {@link #TICK} as a rule, less while
     * he is dragged. Returns whether that was the last of it.
     */
    public boolean bleed(int rate) {
        remaining = Math.max(0, remaining - rate);
        return remaining == 0;
    }

    /** True while he is down and still has time left. */
    public boolean active() {
        return begun && remaining > 0;
    }

    /** True once he has lain there too long. */
    public boolean spent() {
        return begun && remaining <= 0;
    }

    /**
     * How much of the bleed-out is left, in {@code [0, 1]}, for the renderer.
     *
     * <p>Pillar 5.
     */
    public float fraction() {
        if (!begun || bleedOutTicks <= 0) {
            return 0f;
        }
        return (float) remaining / (bleedOutTicks * TICK);
    }

    @Override
    public void reset() {
        bleedOutTicks = 0;
        stabiliseTicks = 0;
        hauledStabiliseTicks = 0;
        maxWounds = 0;
        graceTicks = 0;
        woundedPace = 1f;
        woundedCooldown = 1f;
        redeployAfterTicks = 0;
        begun = false;
        remaining = 0;
        stabilised = 0;
        wounds = 0;
        graceRemaining = 0;
        mendTicks = 0;
    }
}
