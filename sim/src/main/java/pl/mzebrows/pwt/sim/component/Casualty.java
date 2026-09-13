package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * A unit that has been shot or blasted off its feet but is not dead yet: it lies where it fell,
 * bleeding out, and can be put back on its feet by somebody who reaches it in time.
 *
 * <p><strong>Which blows do this is a property of the blow, not of the amount.</strong> Gunfire and
 * explosions leave a casualty; claws, a sweep and a leap kill outright. That split is carried on
 * {@link ContactDamage} and {@link Damage} rather than decided here, so a new damage source declares
 * what kind of thing it is once and never learns that casualties exist.
 *
 * <p>A casualty is at zero health, which is what makes "anything that reaches him finishes him"
 * fall out of the arithmetic rather than out of a branch: the next hit of any kind takes him below
 * zero again and {@code DamageSystem} kills him by the path it always used. Being eaten is that
 * rule, not an exception to it.
 *
 * <p>The bleed-out is counted by {@code CasualtySystem} rather than by {@code CooldownSystem}, and
 * that is deliberate: the rate it runs at is something else's decision - it stops while he is being
 * dragged - and counting down at a rate you had to decide is deciding.
 *
 * <p>Carried permanently by anything that can be one, and read through {@link #begun}. Adding it at
 * the moment somebody goes down would mutate a family mid-iteration, which silently skips entities -
 * the same trap {@link Dying}, {@link Damage} and {@link Stunned} are all carried permanently to
 * avoid. Nothing that carries it is an alien: aliens die when they run out of health, and a strain
 * that could be rescued would need somebody on its side to do the rescuing.
 */
public final class Casualty implements Component, Pool.Poolable {

    /** How long he has before he bleeds out, once he goes down. Configuration, not state. */
    public float bleedOutSeconds;

    /** How long somebody has to stand over him to put him back on his feet. Configuration. */
    public float stabiliseSeconds;

    /**
     * What being hauled multiplies that by. Configuration.
     *
     * <p>The other half of the bargain the drag offers. Hauling him stops his clock, so the ground
     * you cross costs nothing; what it costs is the work at the far end, which is far longer than
     * patching him up where he fell. Distance is therefore the thing the player is really trading,
     * and neither answer - treat him here under fire, or take him somewhere quiet and pay for it -
     * is the right one every time.
     */
    public float hauledStabiliseFactor = 1f;

    public boolean begun;

    public float remainingSeconds;

    /**
     * Seconds of work done on him so far. It falls back to nothing the moment the key is released,
     * which is what makes a stabilise a commitment rather than an accumulation - a man interrupted
     * halfway is a man you have to start again on, and that is the whole reason dragging him
     * somewhere quiet is worth what it costs.
     */
    public float stabilised;

    /** Sets how long he will last and how long he takes to patch up, for fluent spawning. */
    public Casualty set(float newBleedOutSeconds, float newStabiliseSeconds, float newHauledStabiliseFactor) {
        this.bleedOutSeconds = newBleedOutSeconds;
        this.stabiliseSeconds = newStabiliseSeconds;
        this.hauledStabiliseFactor = newHauledStabiliseFactor;
        this.begun = false;
        this.remainingSeconds = 0f;
        this.stabilised = 0f;
        return this;
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

    /** Puts him back on his feet. What he gets back of his health is {@code ActionSystem}'s to say. */
    public void recover() {
        begun = false;
        remainingSeconds = 0f;
        stabilised = 0f;
    }

    /**
     * Puts him down. Ignored if he is already down, so a second hit in the same tick cannot restart
     * the clock - and the hit that would have restarted it kills him instead, because he is at zero.
     */
    public void begin() {
        if (begun) {
            return;
        }
        begun = true;
        remainingSeconds = bleedOutSeconds;
        stabilised = 0f;
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
     * <p>Pillar 5: how long a man on the ground has is exactly the sort of number a player would
     * otherwise be counting in his head, and a rescue he cannot time is a rescue he cannot plan.
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
        begun = false;
        remainingSeconds = 0f;
        stabilised = 0f;
    }
}
