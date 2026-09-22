package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * What an entity does to something it touches on the other side.
 *
 * <p>An enemy's claws, a projectile's impact and a melee sweep are all this component - one with a
 * cooldown and a long recovery window for its victim, one with neither, one that holds both parties
 * still and throws the survivor. Keeping the difference in the numbers rather than in a branch is
 * what lets the collision system have a single rule for "attacker touched target".
 */
public final class ContactDamage implements Component, Pool.Poolable {

    public float amount;

    /** How long after a hit before this can strike again. Zero for a projectile, which only hits once. */
    public float cooldownSeconds;

    public float remainingSeconds;

    /**
     * How long the victim is invulnerable after being hit by this. Stops a crowd deleting a unit in
     * one tick; zero for projectiles, so rapid fire is not wasted on a target that just took a shot.
     */
    public float victimInvulnerableSeconds;

    /**
     * How long a landed blow holds <em>both</em> parties still. Zero for a projectile - shooting
     * something does not commit the shooter to anything.
     */
    public float lockSeconds;

    /**
     * Whether the lock is only spent on a target that had already committed to attacking.
     *
     * <p>True for a melee weapon and false for everything else, and the difference is the mechanic. A
     * sweep that answers something already in the air is a clinch - the two of them are tangled and
     * neither is going anywhere for a beat, and that is what the timing was worth. The same sweep on
     * something merely walking towards you is a swat, and a swat that rooted you to the spot would
     * make killing the harmless thing more expensive than killing the dangerous one.
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
     * <p>True for everything by default, and false for exactly two things: gunfire and blasts. The
     * asymmetry is the mechanic. Being shot from across the field is survivable if somebody comes
     * for you; something reaching you with its claws is not, and neither is being caught by a sweep
     * or landed on. That is what makes distance worth keeping and what makes a man who goes down in
     * the open worth going to get.
     */
    public boolean fatal = true;

    /**
     * The cosine of half the arc this blow can reach into, measured from the way the attacker is
     * going. {@code -1} reaches all round, which is what everything declared before there was an arc.
     *
     * <p><strong>A creature has to be facing you to bite you.</strong> Without it, walking into the
     * back of something that had not noticed you killed you outright - which reads as the game
     * cheating rather than as a mistake you made, because there was nothing on screen to warn you.
     * The arc is deliberately generous: a lunging creature is not precise, and a blind spot wide
     * enough to exploit reliably would turn every fight into a dance round behind things.
     *
     * <p>It is a number on the blow rather than a question about what is attacking, so the collision
     * system keeps its single rule for "attacker touched target" - a projectile and a melee sweep
     * declare all round and pay one comparison for it.
     */
    public float arcCosine = -1f;

    /**
     * How much of its speed a unit this blow lands on keeps, and for how long.
     *
     * <p>One and zero for everything but the beam, which is this component's way of saying "not
     * mine" for a number whose neutral value is one. Recorded on the blow rather than granted at the
     * moment of contact for the same reason the knockback is: nothing in the collision path decides
     * what a hit costs.
     */
    public float slowFactor = 1f;

    public float slowSeconds;

    /** How much of a body this blow pumps up. Zero for everything but the beam. */
    public float bloat;

    /** Sets the attack and leaves it ready to strike, returning this component, for fluent spawning. */
    public ContactDamage set(float newAmount, float newCooldownSeconds, float newVictimInvulnerableSeconds) {
        this.amount = newAmount;
        this.cooldownSeconds = newCooldownSeconds;
        this.remainingSeconds = 0f;
        this.victimInvulnerableSeconds = newVictimInvulnerableSeconds;
        this.lockSeconds = 0f;
        this.locksCommittedOnly = false;
        this.clinchCarrySpeed = 0f;
        this.clinchCarryDecay = 0f;
        this.knockbackSpeed = 0f;
        this.knockbackDecay = 0f;
        this.fatal = true;
        this.arcCosine = -1f;
        this.slowFactor = 1f;
        this.slowSeconds = 0f;
        this.bloat = 0f;
        return this;
    }

    /**
     * Makes this a blow that robs what it lands on of speed, and returns this component, for fluent
     * spawning. Chained after {@link #set}, so a blow that only hurts stays a one-line spawn.
     */
    public ContactDamage slowing(float newSlowFactor, float newSlowSeconds) {
        this.slowFactor = newSlowFactor;
        this.slowSeconds = newSlowSeconds;
        return this;
    }

    /**
     * Makes this a blow that pumps a body up, and returns this component, for fluent spawning.
     * Chained after {@link #set}, like every other thing a blow does besides damage.
     */
    public ContactDamage pumping(float newBloat) {
        this.bloat = newBloat;
        return this;
    }

    /**
     * Narrows this blow to an arc in front of whatever is landing it, and returns this component,
     * for fluent spawning. Chained after {@link #set}, so a blow that reaches all round stays a
     * one-line spawn.
     */
    public ContactDamage reachingWithin(float newArcCosine) {
        this.arcCosine = newArcCosine;
        return this;
    }

    /**
     * Makes this a blow that puts a man down rather than killing him, and returns this component,
     * for fluent spawning. Chained after {@link #set}, so the killing case stays a one-line spawn.
     */
    public ContactDamage wounding() {
        this.fatal = false;
        return this;
    }

    /**
     * Adds what a landed blow does beyond damage. Chained after {@link #set}, so an attack that only
     * hurts stays a one-line spawn.
     */
    public ContactDamage setLock(
            float newLockSeconds,
            boolean newLocksCommittedOnly,
            float newClinchCarrySpeed,
            float newClinchCarryDecay,
            float newKnockbackSpeed,
            float newKnockbackDecay) {
        this.lockSeconds = newLockSeconds;
        this.locksCommittedOnly = newLocksCommittedOnly;
        this.clinchCarrySpeed = newClinchCarrySpeed;
        this.clinchCarryDecay = newClinchCarryDecay;
        this.knockbackSpeed = newKnockbackSpeed;
        this.knockbackDecay = newKnockbackDecay;
        return this;
    }

    public boolean ready() {
        return remainingSeconds <= 0f;
    }

    /** True if a landed blow holds the attacker and the target still. */
    public boolean locks() {
        return lockSeconds > 0f;
    }

    @Override
    public void reset() {
        amount = 0f;
        cooldownSeconds = 0f;
        remainingSeconds = 0f;
        victimInvulnerableSeconds = 0f;
        lockSeconds = 0f;
        locksCommittedOnly = false;
        clinchCarrySpeed = 0f;
        clinchCarryDecay = 0f;
        knockbackSpeed = 0f;
        knockbackDecay = 0f;
        fatal = true;
        arcCosine = -1f;
        slowFactor = 1f;
        slowSeconds = 0f;
        bloat = 0f;
    }
}
