package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Collider;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Invulnerable;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.component.Velocity;

/**
 * Turns the damage owed this tick into lost health, and health at zero into a death.
 *
 * <p>The only system that reduces health, and the only one that decides something has died. Contact
 * resolution adds to a {@link Damage} component and knows nothing about health, invulnerability or
 * what dying involves - which is what keeps a new damage source from having to learn all three.
 *
 * <p>Damage owed is always cleared, invulnerable or not. Carrying it over would mean a unit that
 * survived a hit behind protection dies the moment the protection lapses, from a blow it already
 * shrugged off.
 *
 * <p><strong>Only a survivor is thrown.</strong> This is the one place that knows whether a blow was
 * fatal, so it is the only place that can decide. A unit that lives takes the shove contact recorded
 * for it; a unit that dies stops dead where it was struck, and the body and its killer end up
 * touching. That is the whole of what makes a lethal answer to a leap read as a clinch rather than as
 * a punt.
 *
 * <p>What death means depends on whether the unit carries {@link Dying}. Without it, death is
 * immediate and the entity is marked dead here as it always was. With it, the body stays on the field
 * for its throes and {@code DyingSystem} marks it dead when they run out. Either way {@code Dead}
 * arrives exactly once and means exactly what it always meant.
 */
public final class DamageSystem extends IteratingSystem {

    private static final ComponentMapper<Health> HEALTH = ComponentMapper.getFor(Health.class);
    private static final ComponentMapper<Damage> DAMAGE = ComponentMapper.getFor(Damage.class);
    private static final ComponentMapper<Invulnerable> INVULNERABLE = ComponentMapper.getFor(Invulnerable.class);
    private static final ComponentMapper<Dying> DYING = ComponentMapper.getFor(Dying.class);
    private static final ComponentMapper<Velocity> VELOCITY = ComponentMapper.getFor(Velocity.class);
    private static final ComponentMapper<Collider> COLLIDER = ComponentMapper.getFor(Collider.class);
    private static final ComponentMapper<Stunned> STUNNED = ComponentMapper.getFor(Stunned.class);

    private final Spawner spawner;

    public DamageSystem(Spawner spawner) {
        super(Family.all(Health.class, Damage.class).get());
        this.spawner = spawner;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Damage damage = DAMAGE.get(entity);
        if (damage.amount <= 0f) {
            return;
        }
        float owed = damage.amount;
        float recovery = damage.recoverySeconds;
        float shoveX = damage.knockbackX;
        float shoveY = damage.knockbackY;
        float shove = damage.knockbackSpeed;
        Entity locksOnDeath = damage.locksOnDeath;
        float deathLock = damage.deathLockSeconds;
        float deathLockDecay = damage.deathLockDecay;
        damage.reset();
        if (INVULNERABLE.has(entity) && INVULNERABLE.get(entity).active()) {
            return;
        }
        Health health = HEALTH.get(entity);
        health.current -= owed;
        if (INVULNERABLE.has(entity)) {
            INVULNERABLE.get(entity).grant(recovery);
        }
        if (health.current <= 0f) {
            kill(entity);
            clinch(entity, locksOnDeath, deathLock, deathLockDecay);
            return;
        }
        if (shove > 0f && VELOCITY.has(entity)) {
            VELOCITY.get(entity).set(shoveX * shove, shoveY * shove);
        }
    }

    /**
     * Ties the two of them together, now that the blow is known to have been fatal.
     *
     * <p>This is the whole difference between a clinch and a shove, and it only exists here because
     * here is the only place that knows which one happened. Nothing is held when the target lives.
     */
    private static void clinch(Entity target, @Nullable Entity attacker, float seconds, float decay) {
        if (seconds <= 0f) {
            return;
        }
        if (attacker != null && STUNNED.has(attacker)) {
            STUNNED.get(attacker).clinch(seconds, decay);
        }
        if (STUNNED.has(target)) {
            STUNNED.get(target).clinch(seconds, decay);
        }
    }

    /**
     * A killing blow: either the entity goes now, or it takes its time about it.
     *
     * <p>A body that lingers becomes scenery - solid, and immovable like an obstacle. Immovable is the
     * half that matters for the clinch: the solver stops trying to shuffle the corpse and pushes the
     * living party out instead, so the two settle exactly touching rather than being prised apart or
     * left overlapping.
     *
     * <p>It does not stun the body. If the killing blow carried a lock, contact resolution has already
     * stunned it and that stun is what makes the pair read as locked together; if it did not - anything
     * shot - the body has nothing to hide and reads as dead at once. What keeps a corpse from still
     * chasing is that the drivers treat dying as inert in its own right, not a stun standing in for it.
     */
    private void kill(Entity entity) {
        if (VELOCITY.has(entity)) {
            VELOCITY.get(entity).set(0f, 0f);
        }
        if (!DYING.has(entity)) {
            spawner.markDead(entity);
            return;
        }
        Dying throes = DYING.get(entity);
        if (throes.begun) {
            return;
        }
        throes.begin();
        if (COLLIDER.has(entity)) {
            COLLIDER.get(entity).set(true, false);
        }
        if (INVULNERABLE.has(entity)) {
            INVULNERABLE.get(entity).grant(throes.throesSeconds);
        }
    }
}
