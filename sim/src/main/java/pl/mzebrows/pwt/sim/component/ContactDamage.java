package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * What an entity does to something it touches on the other side.
 *
 * <p>Claws, a shot and a melee sweep are all this component, differing only in numbers, so the
 * collision system has a single rule for "attacker touched target".
 */
public final class ContactDamage implements Component, Pool.Poolable {

    public float amount;

    /** How long after a hit before this can strike again. Zero for a projectile, which only hits once. */
    public int cooldownTicks;

    public int remainingTicks;

    /**
     * How long the victim is invulnerable after being hit by this. Stops a crowd deleting a unit in
     * one tick; zero for projectiles, so rapid fire is not wasted on a target that just took a shot.
     */
    public int victimInvulnerableTicks;

    /**
     * How long a landed blow holds <em>both</em> parties still. Zero for a projectile - shooting
     * something does not commit the shooter to anything.
     */
    public int lockTicks;

    /**
     * Whether the lock is only spent on a target that had already committed to attacking.
     *
     * <p>True for a melee weapon: a sweep on something in the air is a clinch, on something walking a
     * swat.
     */
    public boolean locksCommittedOnly;

    /**
     * How fast a body this blow kills finishes its lunge into whoever landed it, in world units per
     * second. Zero for a blow that has no gap to close.
     */
    public float clinchCarrySpeed;

    /** How fast that carry bleeds off, in world units per second per second. */
    public float clinchCarryDecay;

    /** How hard a target that survives the blow is thrown, in world units per second. */
    public float knockbackSpeed;

    /** How fast that throw bleeds off, in world units per second per second. */
    public float knockbackDecay;

    /**
     * Whether this blow kills outright rather than leaving a casualty on the ground.
     *
     * <p>False only for gunfire, blasts and ruined ground: those leave a casualty (G0023).
     */
    public boolean fatal = true;

    /**
     * The cosine of half the arc this blow can reach into, measured from the way the attacker is
     * going. {@code -1} reaches all round.
     *
     * <p><strong>A creature has to be facing you to bite you.</strong>
     */
    public float arcCosine = -1f;

    /**
     * How much of its speed a unit this blow lands on keeps, and for how long.
     *
     * <p>One and zero - not mine - for everything but the beam.
     */
    public float slowFactor = 1f;

    public int slowTicks;

    /** How much of a body this blow pumps up. Zero for everything but the beam. */
    public float bloat;

    /** Sets the attack and leaves it ready to strike, returning this component, for fluent spawning. */
    public ContactDamage set(float newAmount, int newCooldownTicks, int newVictimInvulnerableTicks) {
        this.amount = newAmount;
        this.cooldownTicks = newCooldownTicks;
        this.remainingTicks = 0;
        this.victimInvulnerableTicks = newVictimInvulnerableTicks;
        this.lockTicks = 0;
        this.locksCommittedOnly = false;
        this.clinchCarrySpeed = 0f;
        this.clinchCarryDecay = 0f;
        this.knockbackSpeed = 0f;
        this.knockbackDecay = 0f;
        this.fatal = true;
        this.arcCosine = -1f;
        this.slowFactor = 1f;
        this.slowTicks = 0;
        this.bloat = 0f;
        return this;
    }

    /**
     * Makes this a blow that robs what it lands on of speed. Chained after {@link #set}.
     */
    public ContactDamage slowing(float newSlowFactor, int newSlowTicks) {
        this.slowFactor = newSlowFactor;
        this.slowTicks = newSlowTicks;
        return this;
    }

    /**
     * Makes this a blow that pumps a body up. Chained after {@link #set}.
     */
    public ContactDamage pumping(float newBloat) {
        this.bloat = newBloat;
        return this;
    }

    /**
     * Narrows this blow to an arc in front of whatever is landing it. Chained after {@link #set}.
     */
    public ContactDamage reachingWithin(float newArcCosine) {
        this.arcCosine = newArcCosine;
        return this;
    }

    /**
     * Makes this a blow that puts a man down rather than killing him. Chained after {@link #set}.
     */
    public ContactDamage wounding() {
        this.fatal = false;
        return this;
    }

    /**
     * Adds what a landed blow does beyond damage. Chained after {@link #set}.
     */
    public ContactDamage setLock(
            int newLockTicks,
            boolean newLocksCommittedOnly,
            float newClinchCarrySpeed,
            float newClinchCarryDecay,
            float newKnockbackSpeed,
            float newKnockbackDecay) {
        this.lockTicks = newLockTicks;
        this.locksCommittedOnly = newLocksCommittedOnly;
        this.clinchCarrySpeed = newClinchCarrySpeed;
        this.clinchCarryDecay = newClinchCarryDecay;
        this.knockbackSpeed = newKnockbackSpeed;
        this.knockbackDecay = newKnockbackDecay;
        return this;
    }

    public boolean ready() {
        return remainingTicks <= 0;
    }

    /** True if a landed blow holds the attacker and the target still. */
    public boolean locks() {
        return lockTicks > 0;
    }

    @Override
    public void reset() {
        amount = 0f;
        cooldownTicks = 0;
        remainingTicks = 0;
        victimInvulnerableTicks = 0;
        lockTicks = 0;
        locksCommittedOnly = false;
        clinchCarrySpeed = 0f;
        clinchCarryDecay = 0f;
        knockbackSpeed = 0f;
        knockbackDecay = 0f;
        fatal = true;
        arcCosine = -1f;
        slowFactor = 1f;
        slowTicks = 0;
        bloat = 0f;
    }
}
