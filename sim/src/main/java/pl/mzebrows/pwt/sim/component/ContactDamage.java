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

    /** How hard a target that survives the blow is thrown, in world units per second. */
    public float knockbackSpeed;

    /** How fast that throw bleeds off, in world units per second per second. */
    public float knockbackDecay;

    /** Sets the attack and leaves it ready to strike, returning this component, for fluent spawning. */
    public ContactDamage set(float newAmount, float newCooldownSeconds, float newVictimInvulnerableSeconds) {
        this.amount = newAmount;
        this.cooldownSeconds = newCooldownSeconds;
        this.remainingSeconds = 0f;
        this.victimInvulnerableSeconds = newVictimInvulnerableSeconds;
        this.lockSeconds = 0f;
        this.knockbackSpeed = 0f;
        this.knockbackDecay = 0f;
        return this;
    }

    /**
     * Adds what a landed blow does beyond damage. Chained after {@link #set}, so an attack that only
     * hurts stays a one-line spawn.
     */
    public ContactDamage setLock(float newLockSeconds, float newKnockbackSpeed, float newKnockbackDecay) {
        this.lockSeconds = newLockSeconds;
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
        knockbackSpeed = 0f;
        knockbackDecay = 0f;
    }
}
