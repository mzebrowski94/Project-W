package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Collider;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Invulnerable;
import pl.mzebrows.pwt.sim.component.Position;
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
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);

    private static final float EPSILON = 1e-4f;

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
        float carrySpeed = damage.clinchCarrySpeed;
        float carryDecay = damage.clinchCarryDecay;
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
            float travelX = VELOCITY.has(entity) ? VELOCITY.get(entity).x : 0f;
            float travelY = VELOCITY.has(entity) ? VELOCITY.get(entity).y : 0f;
            kill(entity);
            clinch(entity, locksOnDeath, deathLock, travelX, travelY, carrySpeed, carryDecay);
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
     *
     * <p>Both of them stop dead. A clinch carries no knockback decay because a killing blow throws
     * nothing - and without that the survivor coasted on whatever he was doing when he swung, which
     * is how a commander backing away from a leap ended up frozen a stride clear of the body.
     */
    private static void clinch(
            Entity target,
            @Nullable Entity attacker,
            float seconds,
            float travelX,
            float travelY,
            float carrySpeed,
            float carryDecay) {
        if (seconds <= 0f) {
            return;
        }
        if (attacker != null && STUNNED.has(attacker)) {
            STUNNED.get(attacker).clinch(seconds);
        }
        if (STUNNED.has(target)) {
            STUNNED.get(target).clinch(seconds);
        }
        carry(target, attacker, travelX, travelY, carrySpeed, carryDecay);
    }

    /**
     * Lets a body finish the lunge it died on, so the two of them end up connected.
     *
     * <p>A sweep reaches further than the commander's own body, so a leap answered at full stretch
     * dies a stride short of him - and a killing blow throws nothing and a corpse is immovable, so
     * nothing would close that stride. It read as him flinching at thin air.
     *
     * <p>Placing the body against him instead worked and looked like what it was: a teleport. So the
     * body keeps going. It carries on along the line it was travelling, decaying, and the constraint
     * solver does the rest - the corpse is immovable and the commander is not, so the body arriving
     * <em>shoves him back</em> and the two travel the last of it together. Nothing here says any of
     * that; it falls out of an immovable body with a velocity meeting a movable one.
     *
     * <p>The heading is the body's own travel unless that points away from him, in which case it is
     * the line between them - a leap is aimed at where he was, and he may have moved since.
     *
     * <p>Nothing is carried when the two are already touching, which is every exchange decided by
     * contact rather than by reach. A commander killed by claws is at arm's length by definition, and
     * his body is left exactly where it fell.
     */
    private static void carry(
            Entity body, @Nullable Entity survivor, float travelX, float travelY, float speed, float decay) {
        if (survivor == null || speed <= 0f || !VELOCITY.has(body)) {
            return;
        }
        if (!POSITION.has(body) || !POSITION.has(survivor) || !BODY.has(body) || !BODY.has(survivor)) {
            return;
        }
        Position bodyAt = POSITION.get(body);
        Position survivorAt = POSITION.get(survivor);
        float towardsX = survivorAt.x - bodyAt.x;
        float towardsY = survivorAt.y - bodyAt.y;
        float apart = (float) Math.sqrt(towardsX * towardsX + towardsY * towardsY);
        if (apart <= BODY.get(body).radius + BODY.get(survivor).radius || apart <= EPSILON) {
            return;
        }
        float headingX = travelX;
        float headingY = travelY;
        if (headingX * headingX + headingY * headingY <= EPSILON || headingX * towardsX + headingY * towardsY <= 0f) {
            headingX = towardsX;
            headingY = towardsY;
        }
        float length = (float) Math.sqrt(headingX * headingX + headingY * headingY);
        if (length <= EPSILON) {
            return;
        }
        VELOCITY.get(body).set(headingX / length * speed, headingY / length * speed);
        if (STUNNED.has(body)) {
            // The clinch cleared this so the survivor would stop dead; the body is the half still
            // moving, and this is what brings it to rest against him rather than through him.
            STUNNED.get(body).knockbackDecay = decay;
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
