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
 * countdown and is {@code CooldownSystem}'s. Carried permanently by every man, read through {@link
 * #begun}; no alien carries it.
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

    /** How long he has before he bleeds out, once he goes down. Configuration, not state. */
    public float bleedOutSeconds;

    /** How long somebody has to stand over him to put him back on his feet. Configuration. */
    public float stabiliseSeconds;

    /**
     * What being hauled multiplies that by. Configuration.
     *
     * <p>Hauling stops his clock; this is what it costs instead.
     */
    public float hauledStabiliseFactor = 1f;

    /** How many wounds he carries on his feet before any upgrade; one more puts him down. Configuration. */
    public int maxWounds;

    /**
     * How many he carries right now: {@link #maxWounds} folded through the {@code MAX_WOUNDS} stat, by
     * {@code DamageSystem} - the one place it is read - on every tick before any hit is weighed. What the
     * next hit is weighed against, and what he is drawn with; never written anywhere else.
     */
    public int carries;

    /** How long one wound protects him from the next, in ticks. Configuration. */
    public int graceTicks;

    /** How much of his pace he keeps while wounded. Configuration. */
    public float woundedPace = 1f;

    /** What being wounded multiplies the time between his shots by. Configuration. */
    public float woundedCooldown = 1f;

    public boolean begun;

    public float remainingSeconds;

    /**
     * Seconds of work done on him so far. Back to nothing the moment the work stops.
     */
    public float stabilised;

    /** The wounds he is carrying. */
    public int wounds;

    /** Ticks of grace left, during which another wound cannot land. */
    public int graceRemaining;

    /** Ticks since his last wound, while he is wounded; his wounds mend once it is long enough. */
    public int mendTicks;

    /** Sets how he is hurt and patched up, for fluent spawning. */
    public Vitality set(
            float newBleedOutSeconds,
            float newStabiliseSeconds,
            float newHauledStabiliseFactor,
            int newMaxWounds,
            int newGraceTicks,
            float newWoundedPace,
            float newWoundedCooldown) {
        this.bleedOutSeconds = newBleedOutSeconds;
        this.stabiliseSeconds = newStabiliseSeconds;
        this.hauledStabiliseFactor = newHauledStabiliseFactor;
        this.maxWounds = newMaxWounds;
        this.carries = newMaxWounds;
        this.graceTicks = newGraceTicks;
        this.woundedPace = newWoundedPace;
        this.woundedCooldown = newWoundedCooldown;
        this.begun = false;
        this.remainingSeconds = 0f;
        this.stabilised = 0f;
        this.wounds = 0;
        this.graceRemaining = 0;
        this.mendTicks = 0;
        return this;
    }

    /**
     * What one tick's worst blow does to him - the whole of G0053's table.
     *
     * <p>A wound in grace, or on a man who is down, is nothing. A wound on a standing man adds one, and
     * one past his maximum puts him down; otherwise it starts his grace. A down blow puts a standing man
     * down whatever his wounds. A down or a killing blow finishes a man who is down, and a killing blow
     * kills a standing one.
     */
    public Taken take(Harm harm) {
        if (begun) {
            return harm == Harm.WOUND ? Taken.NOTHING : Taken.KILLED;
        }
        return switch (harm) {
            case KILL -> Taken.KILLED;
            case DOWN -> {
                begin();
                yield Taken.DOWNED;
            }
            case WOUND -> wound();
        };
    }

    private Taken wound() {
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

    /** How long the work takes on this man, which depends on whether he has been hauled anywhere. */
    public float stabiliseNeeded(boolean hauled) {
        return hauled ? stabiliseSeconds * hauledStabiliseFactor : stabiliseSeconds;
    }

    /** How far through being patched up he is, in {@code [0, 1]}. */
    public float stabiliseProgress(boolean hauled) {
        float needed = stabiliseNeeded(hauled);
        if (needed <= 0f) {
            return 0f;
        }
        float done = stabilised / needed;
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
        remainingSeconds = 0f;
        stabilised = 0f;
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
        remainingSeconds = bleedOutSeconds;
        stabilised = 0f;
        graceRemaining = 0;
    }

    /** True while he is down and still has time left. */
    public boolean active() {
        return begun && remainingSeconds > 0f;
    }

    /** True once he has lain there too long. */
    public boolean spent() {
        return begun && remainingSeconds <= 0f;
    }

    /**
     * How much of the bleed-out is left, in {@code [0, 1]}, for the renderer.
     *
     * <p>Pillar 5.
     */
    public float fraction() {
        if (!begun || bleedOutSeconds <= 0f) {
            return 0f;
        }
        float left = remainingSeconds / bleedOutSeconds;
        return left > 0f ? left : 0f;
    }

    @Override
    public void reset() {
        bleedOutSeconds = 0f;
        stabiliseSeconds = 0f;
        hauledStabiliseFactor = 1f;
        maxWounds = 0;
        carries = 0;
        graceTicks = 0;
        woundedPace = 1f;
        woundedCooldown = 1f;
        begun = false;
        remainingSeconds = 0f;
        stabilised = 0f;
        wounds = 0;
        graceRemaining = 0;
        mendTicks = 0;
    }
}
