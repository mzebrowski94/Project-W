package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * A unit that has been shot or blasted off its feet but is not dead yet: it lies where it fell,
 * bleeding out, and can be put back on its feet by somebody who reaches it in time.
 *
 * <p><strong>Which blows do this is a property of the blow</strong> ({@link ContactDamage#fatal}).
 * A casualty is at zero health, so the next hit of any kind kills him by the ordinary path.
 *
 * <p>The bleed-out is counted by {@code CasualtySystem}, not {@code CooldownSystem}: its rate varies
 * while he is dragged. Carried permanently, read through {@link #begun}; no alien carries it.
 */
public final class Casualty implements Component, Pool.Poolable {

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

    public boolean begun;

    public float remainingSeconds;

    /**
     * Seconds of work done on him so far. Back to nothing the moment the work stops.
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
        begun = false;
        remainingSeconds = 0f;
        stabilised = 0f;
    }
}
