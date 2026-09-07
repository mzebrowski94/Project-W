package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * What an entity does to something it touches on the other side.
 *
 * <p>Both an enemy's claws and a projectile's impact are this component - one with a cooldown and a
 * long recovery window for its victim, one with neither. Keeping the difference in the numbers
 * rather than in a branch is what lets the collision system have a single rule for "attacker touched
 * target".
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

    /** Sets the attack and leaves it ready to strike, returning this component, for fluent spawning. */
    public ContactDamage set(float newAmount, float newCooldownSeconds, float newVictimInvulnerableSeconds) {
        this.amount = newAmount;
        this.cooldownSeconds = newCooldownSeconds;
        this.remainingSeconds = 0f;
        this.victimInvulnerableSeconds = newVictimInvulnerableSeconds;
        return this;
    }

    public boolean ready() {
        return remainingSeconds <= 0f;
    }

    @Override
    public void reset() {
        amount = 0f;
        cooldownSeconds = 0f;
        remainingSeconds = 0f;
        victimInvulnerableSeconds = 0f;
    }
}
