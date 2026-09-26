package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Bloat;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Casualty;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Dragged;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Invulnerable;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Slowed;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.component.Trooper;
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
 * <p>What a killing blow does to a body is {@link Deaths}, shared with the bleed-out so that a man
 * nobody reached leaves exactly the corpse a bullet would have.
 *
 * <p><strong>Not every blow that empties a health bar kills.</strong> Gunfire and blasts leave a
 * casualty on the ground with a clock running; claws, a sweep and a leap finish the job. Which one
 * a blow is travels on the {@link Damage} owed, so this is the only place that has to know the
 * difference - and a unit nothing can rescue simply does not carry a {@link Casualty} and dies as it
 * always did.
 */
public final class DamageSystem extends IteratingSystem {

    private static final ComponentMapper<Health> HEALTH = ComponentMapper.getFor(Health.class);
    private static final ComponentMapper<Damage> DAMAGE = ComponentMapper.getFor(Damage.class);
    private static final ComponentMapper<Invulnerable> INVULNERABLE = ComponentMapper.getFor(Invulnerable.class);
    private static final ComponentMapper<Velocity> VELOCITY = ComponentMapper.getFor(Velocity.class);
    private static final ComponentMapper<Stunned> STUNNED = ComponentMapper.getFor(Stunned.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Perception> PERCEPTION = ComponentMapper.getFor(Perception.class);
    private static final ComponentMapper<Trooper> TROOPER = ComponentMapper.getFor(Trooper.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Casualty> CASUALTY = ComponentMapper.getFor(Casualty.class);
    private static final ComponentMapper<Dragged> DRAGGED = ComponentMapper.getFor(Dragged.class);
    private static final ComponentMapper<Slowed> SLOWED = ComponentMapper.getFor(Slowed.class);
    private static final ComponentMapper<Bloat> BLOAT = ComponentMapper.getFor(Bloat.class);
    private static final ComponentMapper<Dying> DYING = ComponentMapper.getFor(Dying.class);

    private static final float EPSILON = 1e-4f;

    private final Spawner spawner;
    private final SimConfig.BloatConfig bloatConfig;
    private final float alertSeconds;

    public DamageSystem(SimConfig config, Spawner spawner) {
        super(Family.all(Health.class, Damage.class).get());
        this.spawner = spawner;
        this.bloatConfig = config.bloat();
        this.alertSeconds = config.trooper().alertSeconds();
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Damage damage = DAMAGE.get(entity);
        if (damage.amount <= 0f) {
            return;
        }
        float owed = damage.amount;
        int recovery = damage.recoveryTicks;
        float shoveX = damage.knockbackX;
        float shoveY = damage.knockbackY;
        float shove = damage.knockbackSpeed;
        Entity locksOnDeath = damage.locksOnDeath;
        int deathLock = damage.deathLockTicks;
        float carrySpeed = damage.clinchCarrySpeed;
        float carryDecay = damage.clinchCarryDecay;
        boolean hasSource = damage.hasSource;
        boolean fatal = damage.fatal;
        float sourceX = damage.sourceX;
        float sourceY = damage.sourceY;
        boolean slowed = damage.slowed();
        float slowFactor = damage.slowFactor;
        int slowTicks = damage.slowTicks;
        float bloat = damage.bloat;
        damage.reset();
        if (INVULNERABLE.has(entity) && INVULNERABLE.get(entity).active()) {
            return;
        }
        if (hasSource && PERCEPTION.has(entity)) {
            // Being hurt sends it to deal with whatever is where the hit came from (G0016). Latched
            // here rather than in the collision path because only this system knows the hit actually
            // landed - an invulnerable target has already returned above, and being missed provokes
            // nobody.
            PERCEPTION.get(entity).provokeAt(sourceX, sourceY);
        }
        if (hasSource && TROOPER.has(entity)) {
            // The same place, for a man: being hit turns him round to look for what did it. Below the
            // invulnerability check for the same reason - a hit that did not land alarms nobody.
            TROOPER.get(entity).hitFrom(sourceX, sourceY, alertSeconds);
        }
        if (bloat > 0f && BLOAT.has(entity)) {
            // It builds on the living as readily as on the dead, which is the whole of why holding
            // the beam THROUGH a kill is worth more than starting again on the body - and what one
            // shot is worth is divided by how big the thing is, so a brute is a decision and a grunt
            // is an afterthought - and something still on its feet banks only a fraction, which is
            // what leaves a body PARTLY charged at the moment it dies and a decision still to make
            // about it. BloatSystem owns everything after this.
            boolean down = DYING.has(entity) && DYING.get(entity).begun;
            BLOAT.get(entity).feed(bloatConfig.perShotAgainst(BODY.get(entity).radius, bloat, down));
        }
        if (slowed && SLOWED.has(entity)) {
            // Below the invulnerability check for the same reason the provocation is: a hit that did
            // not land slows nobody. Above the health, because a blow that kills makes this moot and
            // a blow that does not has to leave it wading whatever else happens to it this tick.
            SLOWED.get(entity).apply(slowFactor, slowTicks);
        }
        Health health = HEALTH.get(entity);
        health.current -= owed;
        if (INVULNERABLE.has(entity)) {
            INVULNERABLE.get(entity).grant(recovery);
        }
        if (health.current <= 0f) {
            if (fall(entity, fatal)) {
                return;
            }
            float travelX = VELOCITY.has(entity) ? VELOCITY.get(entity).x : 0f;
            float travelY = VELOCITY.has(entity) ? VELOCITY.get(entity).y : 0f;
            Deaths.kill(spawner, entity);
            clinch(entity, locksOnDeath, deathLock, travelX, travelY, carrySpeed, carryDecay);
            return;
        }
        if (shove > 0f && VELOCITY.has(entity)) {
            VELOCITY.get(entity).set(shoveX * shove, shoveY * shove);
        }
    }

    /**
     * Puts a man on the ground instead of in the grave, if this was the kind of blow that does that
     * and he is the kind of unit it can be done to. Returns whether he went down.
     *
     * <p>Three things have to be true: the blow left a casualty rather than killing, the unit can be
     * one at all - no alien can, because nothing on that side would come and get him - and he is not
     * already down. That last one is what makes being shot while you lie there fatal, and it costs
     * no branch anywhere: he is already at zero health, so the hit takes him below it and the
     * ordinary death path runs.
     *
     * <p>His velocity goes with him. Nothing drives a casualty - {@link Drivers} treats him as held
     * - so whatever he was doing when he was hit would otherwise have carried him across the field
     * on the ground.
     */
    private static boolean fall(Entity entity, boolean fatal) {
        if (fatal || !CASUALTY.has(entity)) {
            return false;
        }
        Casualty casualty = CASUALTY.get(entity);
        if (casualty.begun) {
            return false;
        }
        casualty.begin();
        if (DRAGGED.has(entity)) {
            // A fresh time on the ground is a fresh journey. What being hauled costs is the work at
            // the far end, and charging a man for a drag two firefights ago is charging him twice.
            DRAGGED.get(entity).hauled = false;
        }
        if (VELOCITY.has(entity)) {
            VELOCITY.get(entity).set(0f, 0f);
        }
        return true;
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
            int ticks,
            float travelX,
            float travelY,
            float carrySpeed,
            float carryDecay) {
        if (ticks <= 0) {
            return;
        }
        if (attacker != null && STUNNED.has(attacker)) {
            STUNNED.get(attacker).clinch(ticks);
        }
        if (STUNNED.has(target)) {
            STUNNED.get(target).clinch(ticks);
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
}
