package pl.mzebrows.pwt.sim.system.combat;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.Life;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.BlastsOnDeath;
import pl.mzebrows.pwt.sim.component.Bloat;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Dragged;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Handle;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Invulnerable;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Shield;
import pl.mzebrows.pwt.sim.component.Shock;
import pl.mzebrows.pwt.sim.component.Slowed;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.component.Tally;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.Ticks;
import pl.mzebrows.pwt.sim.system.death.Deaths;
import pl.mzebrows.pwt.sim.upgrade.Modifiers;

/**
 * Turns the damage owed this tick into what it costs: lost health for anything that has health, and
 * for a man - who has none - whatever the tick's worst harm does to him (G0053).
 *
 * <p>The only system that reduces health, wounds a man, puts him down or decides something has died.
 * What it settles is whatever <em>landed</em>, owing anything or not (G0057), and the tick's blows are
 * always cleared, invulnerable or not. <strong>Only a survivor is thrown</strong>; a
 * killing blow stops the body where it was struck. What a death does is {@link Deaths}, shared with
 * the bleed-out.
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
    private static final ComponentMapper<Vitality> VITALITY = ComponentMapper.getFor(Vitality.class);
    private static final ComponentMapper<Shield> SHIELD = ComponentMapper.getFor(Shield.class);
    private static final ComponentMapper<Dragged> DRAGGED = ComponentMapper.getFor(Dragged.class);
    private static final ComponentMapper<Slowed> SLOWED = ComponentMapper.getFor(Slowed.class);
    private static final ComponentMapper<Bloat> BLOAT = ComponentMapper.getFor(Bloat.class);
    private static final ComponentMapper<Shock> SHOCK = ComponentMapper.getFor(Shock.class);
    private static final ComponentMapper<BlastsOnDeath> REACTION = ComponentMapper.getFor(BlastsOnDeath.class);
    private static final ComponentMapper<Faction> FACTION = ComponentMapper.getFor(Faction.class);
    private static final ComponentMapper<Tally> TALLY = ComponentMapper.getFor(Tally.class);

    private static final float EPSILON = 1e-4f;

    private final Spawner spawner;
    private final Modifiers modifiers;
    private final SimConfig.BloatConfig bloatConfig;
    private final SimConfig.ShockConfig shockConfig;
    private final int alertTicks;

    /** Every entity with a handle, for finding one named by it. */
    private ImmutableArray<Entity> handled = new ImmutableArray<>(new Array<>());

    /** Every commander, for crediting a kill to the one in a slot. */
    private ImmutableArray<Entity> commanders = new ImmutableArray<>(new Array<>());

    public DamageSystem(SimConfig config, Spawner spawner, Modifiers modifiers) {
        super(Family.all(Damage.class).one(Health.class, Vitality.class).get());
        this.spawner = spawner;
        this.modifiers = modifiers;
        this.bloatConfig = config.bloat();
        this.shockConfig = config.shock();
        this.alertTicks = Ticks.of(config.trooper().alertSeconds(), config.tickSeconds());
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        handled = engine.getEntitiesFor(Family.all(Handle.class).get());
        commanders =
                engine.getEntitiesFor(Family.all(Commander.class, Tally.class).get());
    }

    /**
     * How many wounds a man carries on his feet: his maximum folded through {@code MAX_WOUNDS} - the one
     * place it is read (ADR 0017, G0053), by the hit weighed against it and by the panel that shows it.
     */
    public static int carries(Modifiers modifiers, Entity man) {
        return modifiers.maxWounds(man, VITALITY.get(man).maxWounds);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Damage damage = DAMAGE.get(entity);
        if (damage.landed) {
            settle(entity, damage);
        }
        // The one place a tick's blows are cleared - landed or not, protected or not, so nothing a blow
        // recorded outlives the tick it landed on.
        damage.reset();
    }

    /** What this tick's blows cost the entity they landed on. */
    private void settle(Entity entity, Damage damage) {
        if (INVULNERABLE.has(entity) && INVULNERABLE.get(entity).active()) {
            return;
        }
        if (damage.hasSource && PERCEPTION.has(entity)) {
            // Being hurt provokes it towards where the hit came from (G0016) - only a hit that landed, and
            // any hit that landed, harmless or not (G0057).
            PERCEPTION.get(entity).provokeAt(damage.sourceX, damage.sourceY);
        }
        if (damage.hasSource && TROOPER.has(entity)) {
            // The same place, for a man: he turns round to look for what did it.
            TROOPER.get(entity).hitFrom(damage.sourceX, damage.sourceY, alertTicks);
        }
        if (damage.bloat > 0f && BLOAT.has(entity)) {
            // Builds on the living too, scaled by body size and livingFactor. BloatSystem owns the rest.
            boolean down = Life.dying(entity);
            BLOAT.get(entity).feed(bloatConfig.perShotAgainst(BODY.get(entity).radius, damage.bloat, down));
        }
        if (SHIELD.has(entity) && SHIELD.get(entity).up) {
            // It takes the hit - the harm, the stun and the slow - and breaks, whatever the harm, a harmless
            // one and a grab included; the shove still lands (G0053, G0057).
            SHIELD.get(entity).shatter();
            throwClear(entity, damage);
            return;
        }
        if (VITALITY.has(entity)) {
            hurt(entity, damage);
            if (!Life.down(entity)) {
                // A man left on his feet - wounded, spared by his grace, or hit by something harmless - is
                // still slowed, thrown and stunned.
                slow(entity, damage);
                throwClear(entity, damage);
                daze(entity, damage.stunTicks);
                shock(entity, damage);
            }
            return;
        }
        slow(entity, damage);
        Health health = HEALTH.get(entity);
        health.current -= damage.amount;
        if (INVULNERABLE.has(entity)) {
            INVULNERABLE.get(entity).grant(damage.recoveryTicks);
        }
        if (health.current <= 0) {
            die(entity, damage);
            return;
        }
        throwClear(entity, damage);
        daze(entity, damage.stunTicks);
        shock(entity, damage);
    }

    /**
     * A stun a source declared, on whatever it left standing: how long is the source's, scaled by the one
     * place {@code STUN_DURATION} is read, for whoever takes it.
     */
    private void daze(Entity entity, int stunTicks) {
        if (stunTicks > 0 && STUNNED.has(entity)) {
            STUNNED.get(entity).daze(modifiers.stunTicks(entity, stunTicks));
        }
    }

    /**
     * Builds the shock this tick's hits carried on whatever they left standing, scaled by its body, and
     * stuns it once that fills (G0059). Nothing builds while a declared stun runs - the one it filled to
     * included - so a held trigger is not a stun-lock.
     */
    private void shock(Entity entity, Damage damage) {
        if (damage.shock <= 0f || !SHOCK.has(entity)) {
            return;
        }
        if (STUNNED.has(entity)
                && STUNNED.get(entity).dazed
                && STUNNED.get(entity).active()) {
            return;
        }
        float worth = shockConfig.perHitAgainst(BODY.get(entity).radius, damage.shock);
        if (SHOCK.get(entity).build(worth)) {
            daze(entity, damage.shockStunTicks);
        }
    }

    /**
     * What a tick's worst harm does to a man, through the one table there is ({@link Vitality#take},
     * G0053). His amount is never read: he has no health. Nothing grants him the source's recovery
     * window either - his protection after a wound is his own grace.
     */
    private void hurt(Entity entity, Damage damage) {
        if (Life.dying(entity)) {
            return;
        }
        switch (VITALITY.get(entity).take(damage.harm, carries(modifiers, entity))) {
            case KILLED -> die(entity, damage);
            case DOWNED -> fall(entity);
            case WOUNDED, NOTHING -> {
                // Still where he was: on his feet, or on the ground and spared a wound.
            }
        }
    }

    /** One death path for anything, man or alien, and the clinch a killing blow may carry. */
    private void die(Entity entity, Damage damage) {
        float travelX = VELOCITY.has(entity) ? VELOCITY.get(entity).x : 0f;
        float travelY = VELOCITY.has(entity) ? VELOCITY.get(entity).y : 0f;
        if (Deaths.kill(spawner, entity)) {
            credit(entity, damage.credit);
        }
        clinch(
                entity,
                Handles.resolve(handled, damage.locksOnDeath),
                damage.deathLockTicks,
                travelX,
                travelY,
                damage.clinchCarrySpeed,
                damage.clinchCarryDecay);
    }

    /** Only a hit that landed slows; recorded whether or not the hit did anything else. */
    private static void slow(Entity entity, Damage damage) {
        if (damage.slowed() && SLOWED.has(entity)) {
            SLOWED.get(entity).apply(damage.slowFactor, damage.slowTicks);
        }
    }

    /**
     * Throws a survivor away from the blow. A throw that held nobody - ground pushing a man off itself -
     * also takes his control for as long as it lasts, or his own control would spend it on the next tick.
     */
    private static void throwClear(Entity entity, Damage damage) {
        if (!damage.pushed() || !VELOCITY.has(entity)) {
            return;
        }
        float shove = damage.knockbackSpeed;
        VELOCITY.get(entity).set(damage.knockbackX * shove, damage.knockbackY * shove);
        if (damage.shoveTicks > 0 && STUNNED.has(entity)) {
            STUNNED.get(entity).apply(damage.shoveTicks, damage.shoveDecay);
        }
    }

    /**
     * Whose kill this was (G0044). An enemy's death counts on the tally of the player whose blow it was;
     * friendly fire and anything nobody's counts for nobody. Whatever the body sets off when it goes -
     * a barrel, a charged corpse - carries the same credit, so a blast is credited to whoever's blow
     * set it off.
     */
    private void credit(Entity entity, int credit) {
        if (REACTION.has(entity)) {
            REACTION.get(entity).credit = credit;
        }
        if (credit == Tally.NOBODY || !FACTION.has(entity) || FACTION.get(entity).side != Side.ALIEN) {
            return;
        }
        Entity killer = Players.inSlot(commanders, credit);
        if (killer != null) {
            TALLY.get(killer).kills++;
        }
    }

    /**
     * Puts a man on the ground instead of in the grave. His velocity is zeroed: nothing drives a
     * casualty.
     */
    private static void fall(Entity entity) {
        if (DRAGGED.has(entity)) {
            // A fresh time on the ground is a fresh journey.
            DRAGGED.get(entity).hauled = false;
        }
        if (VELOCITY.has(entity)) {
            VELOCITY.get(entity).set(0f, 0f);
        }
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
