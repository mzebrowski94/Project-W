package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Bloat;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Casualty;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Dragged;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Handle;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Invulnerable;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Slowed;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.def.Ticks;

/**
 * Turns the damage owed this tick into lost health, and health at zero into a death.
 *
 * <p>The only system that reduces health or decides something has died. Damage owed is always
 * cleared, invulnerable or not. <strong>Only a survivor is thrown</strong>; a killing blow stops the
 * body where it was struck. What a death does is {@link Deaths}, shared with the bleed-out.
 *
 * <p><strong>Not every blow that empties a health bar kills</strong>: a wounding one leaves a
 * casualty, if the unit carries a {@link Casualty} at all.
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
    private final int alertTicks;

    /** Every entity with a handle, for finding one named by it. */
    private ImmutableArray<Entity> handled = new ImmutableArray<>(new Array<>());

    public DamageSystem(SimConfig config, Spawner spawner) {
        super(Family.all(Health.class, Damage.class).get());
        this.spawner = spawner;
        this.bloatConfig = config.bloat();
        this.alertTicks = Ticks.of(config.trooper().alertSeconds(), config.tickSeconds());
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        handled = engine.getEntitiesFor(Family.all(Handle.class).get());
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
        long locksOnDeath = damage.locksOnDeath;
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
            // Being hurt provokes it towards where the hit came from (G0016) - only a hit that landed.
            PERCEPTION.get(entity).provokeAt(sourceX, sourceY);
        }
        if (hasSource && TROOPER.has(entity)) {
            // The same place, for a man: he turns round to look for what did it.
            TROOPER.get(entity).hitFrom(sourceX, sourceY, alertTicks);
        }
        if (bloat > 0f && BLOAT.has(entity)) {
            // Builds on the living too, scaled by body size and livingFactor. BloatSystem owns the rest.
            boolean down = DYING.has(entity) && DYING.get(entity).begun;
            BLOAT.get(entity).feed(bloatConfig.perShotAgainst(BODY.get(entity).radius, bloat, down));
        }
        if (slowed && SLOWED.has(entity)) {
            // Only a hit that landed slows; before the health, so a survivor is left wading.
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
            clinch(entity, Handles.resolve(handled, locksOnDeath), deathLock, travelX, travelY, carrySpeed, carryDecay);
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
     * <p>The blow wounds, the unit can be a casualty, and he is not already down - being shot while
     * down is fatal. His velocity is zeroed: nothing drives a casualty.
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
            // A fresh time on the ground is a fresh journey.
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
     * <p>The difference between a clinch and a shove. Both stop dead: a killing blow throws nothing.
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
     * <p>A leap answered at full stretch dies a stride short of him. The body carries on along its line,
     * decaying, and the solver lets it shove him back. Its heading is its own travel unless that points
     * away from him. Nothing is carried when the two already touch.
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
            // The body is the half still moving; this brings it to rest against him.
            STUNNED.get(body).knockbackDecay = decay;
        }
    }
}
