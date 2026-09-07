package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * A gun: how often it may fire, and what it fires. {@code remainingSeconds} counts down every tick
 * and is the whole of the weapon's state, so a snapshot of it replays exactly.
 */
public final class Weapon implements Component, Pool.Poolable {

    public float cooldownSeconds;
    public float remainingSeconds;
    public float projectileSpeed;
    public float projectileRadius;
    public float damage;

    /** Sets the weapon ready to fire immediately, and returns this component, for fluent spawning. */
    public Weapon set(float newCooldownSeconds, float newProjectileSpeed, float newProjectileRadius, float newDamage) {
        this.cooldownSeconds = newCooldownSeconds;
        this.remainingSeconds = 0f;
        this.projectileSpeed = newProjectileSpeed;
        this.projectileRadius = newProjectileRadius;
        this.damage = newDamage;
        return this;
    }

    public boolean ready() {
        return remainingSeconds <= 0f;
    }

    @Override
    public void reset() {
        cooldownSeconds = 0f;
        remainingSeconds = 0f;
        projectileSpeed = 0f;
        projectileRadius = 0f;
        damage = 0f;
    }
}
