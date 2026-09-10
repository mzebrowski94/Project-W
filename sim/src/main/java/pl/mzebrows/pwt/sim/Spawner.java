package pl.mzebrows.pwt.sim;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.PooledEngine;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Avoidance;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Collider;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.ContactDamage;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Dead;
import pl.mzebrows.pwt.sim.component.Dodge;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Feed;
import pl.mzebrows.pwt.sim.component.Focus;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Intent;
import pl.mzebrows.pwt.sim.component.Invulnerable;
import pl.mzebrows.pwt.sim.component.Leap;
import pl.mzebrows.pwt.sim.component.Level;
import pl.mzebrows.pwt.sim.component.Melee;
import pl.mzebrows.pwt.sim.component.Obstacle;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Pickup;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.PreviousPosition;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Respawning;
import pl.mzebrows.pwt.sim.component.RespawnsOnDeath;
import pl.mzebrows.pwt.sim.component.Speed;
import pl.mzebrows.pwt.sim.component.Steering;
import pl.mzebrows.pwt.sim.component.Strain;
import pl.mzebrows.pwt.sim.component.Strike;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.component.Taking;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.component.Wander;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.def.PickupDef;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.UnitDef;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WeaponDef;
import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * The one place that builds entity state, so no system has to remember which components a commander
 * or a projectile is made of - or reach for the engine to create one.
 *
 * <p>Systems that spawn take one of these rather than a reference back to {@link Simulation}, which
 * keeps the dependency pointing one way and lets a system be tested without a whole world. Marking an
 * entity dead lives here for the same reason: it needs a pooled component, and component creation is
 * this class's job.
 *
 * <p>Every spawn seeds {@code PreviousPosition} at the spawn point. Leaving it at the origin would
 * make a new entity streak in from the corner of the arena on its first drawn frame, and that is the
 * kind of bug no test catches.
 */
public final class Spawner {

    /**
     * What a commander is armed with when it spawns and when it comes back. A better gun is something
     * found on the field and lost on death, never something owned (pillar 3).
     */
    private static final WeaponKind STARTING_WEAPON = WeaponKind.SIDEARM;

    private final PooledEngine engine;
    private final SimConfig config;
    private final TileMap map;

    Spawner(PooledEngine engine, SimConfig config, TileMap map) {
        this.engine = engine;
        this.config = config;
        this.map = map;
    }

    /** Spawns the player-controlled unit, armed, at full health, and ready to fire. */
    public Entity spawnCommander(float x, float y) {
        SimConfig.CommanderConfig commander = config.commander();
        SimConfig.MeleeConfig melee = config.melee();
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Commander.class));
        entity.add(engine.createComponent(Focus.class));
        entity.add(engine.createComponent(Faction.class).set(Side.PLAYER));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Velocity.class).set(0f, 0f));
        entity.add(engine.createComponent(Body.class).set(commander.radius()));
        entity.add(engine.createComponent(Speed.class).set(commander.speed()));
        entity.add(engine.createComponent(Collider.class).set(true, true));
        entity.add(engine.createComponent(Intent.class));
        entity.add(engine.createComponent(Health.class).set(commander.health()));
        entity.add(engine.createComponent(Damage.class));
        entity.add(engine.createComponent(Invulnerable.class));
        entity.add(engine.createComponent(Stunned.class));
        entity.add(engine.createComponent(Dying.class).set(commander.deathThroesSeconds()));
        entity.add(engine.createComponent(RespawnsOnDeath.class)
                .set(
                        commander.respawnDelaySeconds(),
                        commander.respawnMargin(),
                        map.worldHeight() * 0.5f,
                        commander.respawnInvulnerableSeconds()));
        entity.add(engine.createComponent(Weapon.class).set(STARTING_WEAPON, config.weapon(STARTING_WEAPON)));
        entity.add(engine.createComponent(Melee.class)
                .set(
                        melee.cooldownSeconds(),
                        melee.windupSeconds(),
                        melee.activeSeconds(),
                        melee.reach(),
                        melee.radius(),
                        melee.damage(),
                        melee.arcDegrees(),
                        melee.lockSeconds(),
                        melee.locksCommittedOnly(),
                        melee.clinchCarrySpeed(),
                        melee.clinchCarryDecay(),
                        melee.knockbackSpeed(),
                        melee.knockbackDecay()));
        entity.add(engine.createComponent(Taking.class));
        entity.add(engine.createComponent(Appearance.class).set(Look.COMMANDER).face(1f, 0f));
        engine.addEntity(entity);
        return entity;
    }

    /**
     * Spawns one trooper: a member of the squad, on the commander's side.
     *
     * <p>It has a body, a gun and a life, and nothing drives it yet. That is the stage boundary
     * rather than an oversight - a trooper that can be seen, shoved and killed is worth putting on
     * the field before anything decides where it walks, because everything after this is judged by
     * watching it move.
     */
    public Entity spawnTrooper(float x, float y) {
        SimConfig.TrooperConfig trooper = config.trooper();
        SimConfig.MeleeConfig melee = config.melee();
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Trooper.class)
                .sees(
                        trooper.perception().detectionRange(),
                        trooper.perception().loseRange(),
                        trooper.perception().arcCosine(),
                        trooper.perception().requiresClearLine())
                .look(1f, 0f));
        entity.add(engine.createComponent(Faction.class).set(Side.PLAYER));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Velocity.class).set(0f, 0f));
        entity.add(engine.createComponent(Body.class).set(trooper.radius()));
        entity.add(engine.createComponent(Speed.class).set(trooper.speed()));
        entity.add(engine.createComponent(Steering.class));
        entity.add(engine.createComponent(Collider.class).set(true, true));
        entity.add(engine.createComponent(Health.class).set(trooper.health()));
        entity.add(engine.createComponent(Damage.class));
        entity.add(engine.createComponent(Invulnerable.class));
        entity.add(engine.createComponent(Stunned.class));
        entity.add(engine.createComponent(Dying.class).set(trooper.deathThroesSeconds()));
        entity.add(engine.createComponent(Avoidance.class)
                .set(
                        trooper.avoidance().enabled(),
                        trooper.avoidance().scanRange(),
                        trooper.avoidance().lookaheadSeconds(),
                        trooper.avoidance().weight()));
        entity.add(engine.createComponent(Weapon.class).set(trooper.weapon(), config.weapon(trooper.weapon())));
        entity.add(engine.createComponent(Melee.class)
                .set(
                        melee.cooldownSeconds(),
                        melee.windupSeconds(),
                        melee.activeSeconds(),
                        melee.reach(),
                        melee.radius(),
                        melee.damage(),
                        melee.arcDegrees(),
                        melee.lockSeconds(),
                        melee.locksCommittedOnly(),
                        melee.clinchCarrySpeed(),
                        melee.clinchCarryDecay(),
                        melee.knockbackSpeed(),
                        melee.knockbackDecay()));
        entity.add(engine.createComponent(Taking.class));
        entity.add(engine.createComponent(Appearance.class).set(Look.TROOPER).face(1f, 0f));
        engine.addEntity(entity);
        return entity;
    }

    /**
     * Spawns a chasing unit of the given strain, built entirely from that strain's definition.
     *
     * <p>There is one method rather than one per strain because a strain <em>is</em> its numbers. A
     * grunt and a brute differ in a {@code UnitDef} and in nothing else, so adding a strain is a
     * definition and no code at all.
     */
    public Entity spawnUnit(UnitKind kind, float x, float y) {
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Enemy.class));
        entity.add(engine.createComponent(Strain.class));
        entity.add(engine.createComponent(Faction.class).set(Side.ALIEN));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Velocity.class).set(0f, 0f));
        entity.add(engine.createComponent(Body.class));
        entity.add(engine.createComponent(Speed.class));
        entity.add(engine.createComponent(Steering.class));
        entity.add(engine.createComponent(Collider.class).set(true, true));
        entity.add(engine.createComponent(Health.class));
        entity.add(engine.createComponent(Damage.class));
        entity.add(engine.createComponent(Invulnerable.class));
        entity.add(engine.createComponent(Stunned.class));
        entity.add(engine.createComponent(Dying.class));
        entity.add(engine.createComponent(ContactDamage.class));
        entity.add(engine.createComponent(Perception.class));
        entity.add(engine.createComponent(Wander.class).anchor(x, y));
        entity.add(engine.createComponent(Avoidance.class));
        entity.add(engine.createComponent(Leap.class));
        entity.add(engine.createComponent(Taking.class));
        entity.add(engine.createComponent(Feed.class));
        entity.add(engine.createComponent(Appearance.class).face(1f, 0f));
        applyStrain(entity, kind);
        engine.addEntity(entity);
        return entity;
    }

    /**
     * Writes a strain's definition into components the entity already has.
     *
     * <p><strong>The single writer.</strong> Spawning calls it and so does evolving, which is what
     * guarantees an evolved unit is indistinguishable from one spawned in that strain - no field can
     * be updated in one path and forgotten in the other, because there is only one path.
     *
     * <p>It changes no component <em>set</em>, only field values, so an entity may be run through it
     * mid-tick without disturbing any family being iterated. That is the whole reason a strain is an
     * enum on a component rather than a marker.
     *
     * <p>Where a unit lives is the one thing it does not rewrite. {@code Wander.anchor} is set at the
     * spawn and never again, so a grunt that becomes a brute keeps the patch the grunt was put in -
     * where something lives is a fact about that individual, not about what it has turned into.
     *
     * <p>Health is set to the new strain's maximum rather than carried over as a fraction. A unit
     * that has just become something else arrives whole; a grunt shot down to a sliver and then fed
     * gets up as a full brute, and that reversal is the drama the mechanic is for.
     */
    public void applyStrain(Entity entity, UnitKind kind) {
        UnitDef def = config.unit(kind);
        entity.getComponent(Strain.class).set(kind);
        entity.getComponent(Body.class).set(def.radius());
        entity.getComponent(Speed.class).set(def.speed());
        entity.getComponent(Health.class).set(def.health());
        entity.getComponent(Dying.class).set(def.deathThroesSeconds());
        entity.getComponent(ContactDamage.class)
                .set(def.contactDamage(), def.attackCooldownSeconds(), def.victimInvulnerableSeconds())
                .setLock(def.lockSeconds(), false, 0f, 0f, 0f, 0f);
        entity.getComponent(Perception.class)
                .set(
                        def.perception().alwaysAggressive(),
                        def.perception().losesInterest(),
                        def.perception().detectionRange(),
                        def.perception().loseRange(),
                        def.perception().secondaryRange(),
                        def.perception().arcCosine(),
                        def.perception().requiresClearLine());
        entity.getComponent(Wander.class)
                .set(
                        def.wander().enabled(),
                        def.wander().radius(),
                        def.wander().speedFactor(),
                        def.wander().arriveRadius(),
                        def.wander().pauseMinSeconds(),
                        def.wander().pauseMaxSeconds(),
                        def.wander().waypointTimeoutSeconds());
        entity.getComponent(Avoidance.class)
                .set(
                        def.avoidance().enabled(),
                        def.avoidance().scanRange(),
                        def.avoidance().lookaheadSeconds(),
                        def.avoidance().weight());
        entity.getComponent(Leap.class)
                .set(
                        def.leap().enabled(),
                        def.leap().triggerRange(),
                        def.leap().speed(),
                        def.leap().airborneSeconds(),
                        def.leap().sequenceLength(),
                        def.leap().betweenSeconds(),
                        def.leap().cooldownSeconds(),
                        def.leap().hesitationMinSeconds(),
                        def.leap().hesitationMaxSeconds());
        entity.getComponent(Appearance.class).set(def.look());
    }

    /**
     * Puts a gun in a unit's hands, replacing whatever it was carrying.
     *
     * <p>The single writer for weapons, as {@code applyStrain} is for strains: spawning and picking
     * one up both come through here, so a gun found on the field behaves exactly like one spawned
     * with. It changes no component set - only the fields of the {@code Weapon} the unit already has.
     */
    public void applyWeapon(Entity entity, WeaponKind kind) {
        Weapon weapon = entity.getComponent(Weapon.class);
        if (weapon != null) {
            weapon.set(kind, config.weapon(kind));
        }
    }

    /**
     * Spawns something lying on the field for the first thing that touches it.
     *
     * <p>Non-solid, so units walk over it rather than round it, and immovable, so a crowd cannot
     * shove it out of the way. Those two flags are the whole of what makes it a pickup as far as the
     * constraint solver is concerned - it reports the contact and asks nothing further, exactly as it
     * does for a projectile or a melee sweep.
     *
     * <p>Neutral, because a pickup is on nobody's side. What each side gets out of it is in the
     * definition, not in a faction.
     */
    public Entity spawnPickup(PickupKind kind, float x, float y) {
        PickupDef def = config.pickup(kind);
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Pickup.class).set(kind));
        entity.add(engine.createComponent(Faction.class).set(Side.NEUTRAL));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Body.class).set(def.radius()));
        entity.add(engine.createComponent(Collider.class).set(false, false));
        entity.add(engine.createComponent(Appearance.class).set(def.look()));
        engine.addEntity(entity);
        return entity;
    }

    /**
     * Spawns a rabbit: alien food that walks, wanders, and steps aside from a shot it sees coming.
     *
     * <p>A pickup in every way that matters - it is claimed on contact and its grants come from the
     * same table - but solid and movable rather than the usual non-solid and immovable, because those
     * two flags are what keep a body out of rock and inside the arena and this one moves under its own
     * power. Avoidance is told to ignore pickups outright, so nothing steers around it.
     *
     * <p>It carries {@code Health} and {@code Damage} because it can be shot, and a shot body cannot
     * be claimed - which is what makes killing one denial rather than a waste of a bullet.
     */
    public Entity spawnRabbit(float x, float y) {
        PickupDef def = config.pickup(PickupKind.RABBIT);
        SimConfig.RabbitConfig rabbit = config.rabbit();
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Pickup.class).set(PickupKind.RABBIT));
        entity.add(engine.createComponent(Faction.class).set(Side.NEUTRAL));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Velocity.class).set(0f, 0f));
        entity.add(engine.createComponent(Body.class).set(def.radius()));
        entity.add(engine.createComponent(Speed.class).set(rabbit.speed()));
        entity.add(engine.createComponent(Collider.class).set(true, true));
        entity.add(engine.createComponent(Health.class).set(rabbit.health()));
        entity.add(engine.createComponent(Damage.class));
        entity.add(engine.createComponent(Dying.class).set(rabbit.deathThroesSeconds()));
        entity.add(engine.createComponent(Steering.class));
        entity.add(engine.createComponent(Wander.class)
                .set(
                        rabbit.wander().enabled(),
                        rabbit.wander().radius(),
                        rabbit.wander().speedFactor(),
                        rabbit.wander().arriveRadius(),
                        rabbit.wander().pauseMinSeconds(),
                        rabbit.wander().pauseMaxSeconds(),
                        rabbit.wander().waypointTimeoutSeconds())
                .anchor(x, y));
        entity.add(engine.createComponent(Dodge.class)
                .set(
                        rabbit.dodge().enabled(),
                        rabbit.dodge().reactionRange(),
                        rabbit.dodge().speed(),
                        rabbit.dodge().durationSeconds(),
                        rabbit.dodge().commitSeconds(),
                        rabbit.dodge().margin()));
        entity.add(engine.createComponent(Appearance.class).set(def.look()).face(1f, 0f));
        engine.addEntity(entity);
        return entity;
    }

    /**
     * Spawns a control point: ground the squad takes by standing on it.
     *
     * <p><strong>No collider.</strong> A point is somewhere you walk, not something you walk into, so
     * it never enters the pairwise path at all - which also means its body can be as wide as the
     * capture radius without inflating the collision grid's cell size.
     *
     * <p>It carries a body anyway, because that is what the renderer measures a circle from, and a
     * point drawn at its capture radius is a point whose edge the player can see. Spawn one before
     * the units that will stand on it: the renderer draws in spawn order, so it wants to be the thing
     * underneath.
     */
    public Entity spawnControlPoint(float x, float y) {
        SimConfig.ControlPointConfig point = config.controlPoint();
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(ControlPoint.class)
                .set(point.radius(), point.captureSeconds(), point.aggroRadius()));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Body.class).set(point.radius()));
        entity.add(engine.createComponent(Appearance.class).set(Look.CONTROL_POINT));
        engine.addEntity(entity);
        return entity;
    }

    /**
     * Spawns the level entity: one entity holding state that belongs to nobody in particular.
     *
     * <p>Spawned with the world and never removed. It has no position, no body and nothing to draw,
     * which is exactly why it is an entity - it means a level-scope reaction is an ordinary system
     * reading an ordinary component, and no part of the simulation needs a message queue to reach it
     * (G0004).
     */
    public Entity spawnLevel() {
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Level.class));
        engine.addEntity(entity);
        return entity;
    }

    /** Spawns a static circle that blocks units and projectiles. On nobody's side, and harmless. */
    public Entity spawnObstacle(float x, float y) {
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Obstacle.class));
        entity.add(engine.createComponent(Faction.class).set(Side.NEUTRAL));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Body.class).set(config.obstacle().radius()));
        entity.add(engine.createComponent(Collider.class).set(true, false));
        entity.add(engine.createComponent(Appearance.class).set(Look.OBSTACLE));
        engine.addEntity(entity);
        return entity;
    }

    /**
     * Spawns a projectile travelling along the given unit direction, on the firer's side so it cannot
     * hit them or anything else friendly. The caller supplies the direction already normalised - it
     * has usually just computed it to place the spawn point.
     *
     * <p>Everything about the shot comes from the gun's definition, its look included, so which gun
     * fired it is visible without anything asking.
     */
    public Entity spawnProjectile(float x, float y, float directionX, float directionY, Weapon weapon, Side side) {
        return projectile(
                x,
                y,
                directionX,
                directionY,
                weapon.projectileSpeed,
                weapon.projectileRadius,
                weapon.damage,
                weapon.projectileLook,
                side);
    }

    /**
     * The same, from a gun's definition rather than from a gun somebody is holding. For the harness
     * and for tests that want a shot in the world without a shooter behind it.
     */
    public Entity spawnProjectile(float x, float y, float directionX, float directionY, WeaponDef gun, Side side) {
        return projectile(
                x,
                y,
                directionX,
                directionY,
                gun.projectileSpeed(),
                gun.projectileRadius(),
                gun.damage(),
                gun.projectileLook(),
                side);
    }

    private Entity projectile(
            float x,
            float y,
            float directionX,
            float directionY,
            float speed,
            float radius,
            float damage,
            Look look,
            Side side) {
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Projectile.class).from(x, y));
        entity.add(engine.createComponent(Faction.class).set(side));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Velocity.class).set(directionX * speed, directionY * speed));
        entity.add(engine.createComponent(Body.class).set(radius));
        entity.add(engine.createComponent(Collider.class).set(false, true));
        entity.add(engine.createComponent(ContactDamage.class).set(damage, 0f, 0f));
        entity.add(engine.createComponent(Appearance.class).set(look));
        engine.addEntity(entity);
        return entity;
    }

    /**
     * Spawns one swing of a melee weapon: a real body in the world that sweeps through an arc and is
     * gone (G0005).
     *
     * <p>It is non-solid, so it reports a contact without shoving what it touches, and immovable, so
     * the solver leaves its position to the system driving the sweep. Its attack cooldown is the
     * whole duration of the swing, which is what makes one swing land at most one blow.
     *
     * <p>It grants its victim no invulnerability, deliberately: a target held still by a connecting
     * sweep is meant to be punished during the opening, not protected by it.
     */
    public Entity spawnStrike(
            @Nullable Entity owner,
            float originX,
            float originY,
            float startAngle,
            float sweepRadians,
            Melee melee,
            Side side) {
        float x = originX + (float) StrictMath.cos(startAngle) * melee.reach;
        float y = originY + (float) StrictMath.sin(startAngle) * melee.reach;
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Strike.class)
                .set(owner, originX, originY, melee.reach, startAngle, sweepRadians, melee.activeSeconds));
        entity.add(engine.createComponent(Faction.class).set(side));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Body.class).set(melee.radius));
        entity.add(engine.createComponent(Collider.class).set(false, false));
        entity.add(engine.createComponent(ContactDamage.class)
                .set(melee.damage, melee.activeSeconds, 0f)
                .setLock(
                        melee.lockSeconds,
                        melee.locksCommittedOnly,
                        melee.clinchCarrySpeed,
                        melee.clinchCarryDecay,
                        melee.knockbackSpeed,
                        melee.knockbackDecay));
        entity.add(engine.createComponent(Appearance.class).set(Look.STRIKE));
        engine.addEntity(entity);
        return entity;
    }

    /**
     * Spawns the pending-respawn ticket for a unit that just died. It has nothing to draw and nothing
     * to collide with; it exists only to hold a countdown that the dead unit can no longer hold.
     */
    public Entity spawnRespawn(float delaySeconds, float x, float y, float invulnerableSeconds) {
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Respawning.class).set(delaySeconds, x, y, invulnerableSeconds));
        engine.addEntity(entity);
        return entity;
    }

    /**
     * Marks an entity for removal at the end of the tick. Nothing removes an entity directly - the
     * gap between being marked and being reaped is where death reactions run (G0004).
     */
    public void markDead(Entity entity) {
        if (entity.getComponent(Dead.class) == null) {
            entity.add(engine.createComponent(Dead.class));
        }
    }
}
