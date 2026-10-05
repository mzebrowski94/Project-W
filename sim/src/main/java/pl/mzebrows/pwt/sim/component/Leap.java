package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * How a unit throws itself at the commander, and where it currently is in doing so.
 *
 * <p>The configuration half is per enemy kind, so a quick thing that barely telegraphs and a heavy
 * thing that winds up visibly are one system with different numbers. The state half is a small phase
 * machine, and the phases are the mechanic:
 *
 * <ul>
 *   <li>{@code READY} - walking, watching, waiting for the commander to come inside {@code
 *       triggerRange}.
 *   <li>{@code HESITATING} - committed and standing still. This is the tell, and it is the only part
 *       of the leap the player gets to react to.
 *   <li>{@code AIRBORNE} - travelling along a direction fixed at launch. Fixed, because a leap that
 *       tracked its target would be unavoidable, and an unavoidable attack has nothing to counter.
 *   <li>{@code RECOVERING} - landed, standing still, about to go again if the sequence has leaps
 *       left.
 * </ul>
 *
 * <p>Only the first leap of a sequence hesitates. The rest follow straight on, which is what makes
 * the mechanic easy to learn and hard to master: one tell to read, then a chain to keep track of.
 */
public final class Leap implements Component, Pool.Poolable {

    /** Where a unit is in the act of leaping. */
    public enum Phase {
        READY,
        HESITATING,
        AIRBORNE,
        RECOVERING
    }

    public boolean enabled;
    public float triggerRange;
    public float speed;
    public int airborneTicks;
    public int sequenceLength;
    public int betweenTicks;
    public int cooldownTicks;
    public float hesitationMinSeconds;
    public float hesitationMaxSeconds;

    public Phase phase = Phase.READY;

    /** Counts down the current phase. Decremented by {@code CooldownSystem} with every other timer. */
    public int remainingTicks;

    /** Counts down the wait after a whole sequence. Also decremented by {@code CooldownSystem}. */
    public int cooldownRemaining;

    /** Leaps still to come in the sequence being flown. */
    public int leapsLeft;

    public float directionX;
    public float directionY;

    /**
     * The shut door this leap is at, by handle, or nobody (G0069): a hunter whose way a door shuts leaps at the
     * door instead of at the man behind it, aimed once at its face.
     */
    public long door = Handle.NOBODY;

    /** Sets the configuration and leaves the unit ready to leap, for fluent spawning. */
    public Leap set(
            boolean newEnabled,
            float newTriggerRange,
            float newSpeed,
            int newAirborneTicks,
            int newSequenceLength,
            int newBetweenTicks,
            int newCooldownTicks,
            float newHesitationMinSeconds,
            float newHesitationMaxSeconds) {
        this.enabled = newEnabled;
        this.triggerRange = newTriggerRange;
        this.speed = newSpeed;
        this.airborneTicks = newAirborneTicks;
        this.sequenceLength = newSequenceLength;
        this.betweenTicks = newBetweenTicks;
        this.cooldownTicks = newCooldownTicks;
        this.hesitationMinSeconds = newHesitationMinSeconds;
        this.hesitationMaxSeconds = newHesitationMaxSeconds;
        this.phase = Phase.READY;
        this.remainingTicks = 0;
        this.cooldownRemaining = 0;
        this.leapsLeft = 0;
        this.directionX = 0f;
        this.directionY = 0f;
        this.door = Handle.NOBODY;
        return this;
    }

    /** True while the unit is doing anything other than walking - the leap owns its velocity. */
    public boolean committed() {
        return phase != Phase.READY;
    }

    /** True while the unit is off the ground and travelling on its locked direction. */
    public boolean airborne() {
        return phase == Phase.AIRBORNE;
    }

    /** Puts the unit back to walking and starts the wait before it may commit again. */
    public void finish() {
        phase = Phase.READY;
        remainingTicks = 0;
        cooldownRemaining = cooldownTicks;
        leapsLeft = 0;
        directionX = 0f;
        directionY = 0f;
        door = Handle.NOBODY;
    }

    @Override
    public void reset() {
        enabled = false;
        triggerRange = 0f;
        speed = 0f;
        airborneTicks = 0;
        sequenceLength = 0;
        betweenTicks = 0;
        cooldownTicks = 0;
        hesitationMinSeconds = 0f;
        hesitationMaxSeconds = 0f;
        phase = Phase.READY;
        remainingTicks = 0;
        cooldownRemaining = 0;
        leapsLeft = 0;
        directionX = 0f;
        directionY = 0f;
        door = Handle.NOBODY;
    }
}
