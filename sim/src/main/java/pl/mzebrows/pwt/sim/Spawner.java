package pl.mzebrows.pwt.sim;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.PooledEngine;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Brute;
import pl.mzebrows.pwt.sim.component.Collider;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.ContactDamage;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Dead;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Intent;
import pl.mzebrows.pwt.sim.component.Invulnerable;
import pl.mzebrows.pwt.sim.component.Leap;
import pl.mzebrows.pwt.sim.component.Melee;
import pl.mzebrows.pwt.sim.component.Obstacle;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.PreviousPosition;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Respawning;
import pl.mzebrows.pwt.sim.component.RespawnsOnDeath;
import pl.mzebrows.pwt.sim.component.Speed;
import pl.mzebrows.pwt.sim.component.Strike;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.component.Weapon;

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

    private final PooledEngine engine;
    private final SimConfig config;

    Spawner(PooledEngine engine, SimConfig config) {
        this.engine = engine;
        this.config = config;
    }

    /** Spawns the player-controlled unit, armed, at full health, and ready to fire. */
    public Entity spawnCommander(float x, float y) {
        SimConfig.CommanderConfig commander = config.commander();
        SimConfig.WeaponConfig weapon = config.weapon();
        SimConfig.MeleeConfig melee = config.melee();
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Commander.class));
        entity.add(engine.createComponent(Faction.class).set(Faction.Side.PLAYER));
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
                        config.arena().height() * 0.5f,
                        commander.respawnInvulnerableSeconds()));
        entity.add(engine.createComponent(Weapon.class)
                .set(weapon.cooldownSeconds(), weapon.projectileSpeed(), weapon.projectileRadius(), weapon.damage()));
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
                        melee.knockbackSpeed(),
                        melee.knockbackDecay()));
        entity.add(engine.createComponent(Appearance.class).set(Appearance.Look.COMMANDER));
        engine.addEntity(entity);
        return entity;
    }

    /** Spawns a unit that chases the commander and hurts whatever it touches on the other side. */
    public Entity spawnEnemy(float x, float y) {
        SimConfig.EnemyConfig enemy = config.enemy();
        Entity entity = chaser(
                x,
                y,
                enemy.radius(),
                enemy.speed(),
                enemy.health(),
                enemy.contactDamage(),
                enemy.attackCooldownSeconds(),
                enemy.victimInvulnerableSeconds(),
                enemy.lockSeconds(),
                enemy.deathThroesSeconds(),
                enemy.leap(),
                Appearance.Look.ENEMY);
        engine.addEntity(entity);
        return entity;
    }

    /** Spawns a heavy unit: the same thing as an enemy, with more health and a slower, bigger leap. */
    public Entity spawnBrute(float x, float y) {
        SimConfig.BruteConfig brute = config.brute();
        Entity entity = chaser(
                x,
                y,
                brute.radius(),
                brute.speed(),
                brute.health(),
                brute.contactDamage(),
                brute.attackCooldownSeconds(),
                brute.victimInvulnerableSeconds(),
                brute.lockSeconds(),
                brute.deathThroesSeconds(),
                brute.leap(),
                Appearance.Look.BRUTE);
        entity.add(engine.createComponent(Brute.class));
        engine.addEntity(entity);
        return entity;
    }

    /**
     * The parts every chasing unit is made of. A brute is an enemy with different numbers and one
     * extra marker, so it is built here rather than in a second near-identical method.
     */
    private Entity chaser(
            float x,
            float y,
            float radius,
            float speed,
            float health,
            float contactDamage,
            float attackCooldownSeconds,
            float victimInvulnerableSeconds,
            float lockSeconds,
            float deathThroesSeconds,
            SimConfig.LeapConfig leap,
            Appearance.Look look) {
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Enemy.class));
        entity.add(engine.createComponent(Faction.class).set(Faction.Side.ALIEN));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Velocity.class).set(0f, 0f));
        entity.add(engine.createComponent(Body.class).set(radius));
        entity.add(engine.createComponent(Speed.class).set(speed));
        entity.add(engine.createComponent(Collider.class).set(true, true));
        entity.add(engine.createComponent(Health.class).set(health));
        entity.add(engine.createComponent(Damage.class));
        entity.add(engine.createComponent(Invulnerable.class));
        entity.add(engine.createComponent(Stunned.class));
        entity.add(engine.createComponent(Dying.class).set(deathThroesSeconds));
        entity.add(engine.createComponent(ContactDamage.class)
                .set(contactDamage, attackCooldownSeconds, victimInvulnerableSeconds)
                .setLock(lockSeconds, 0f, 0f));
        entity.add(engine.createComponent(Leap.class)
                .set(
                        leap.enabled(),
                        leap.triggerRange(),
                        leap.speed(),
                        leap.airborneSeconds(),
                        leap.sequenceLength(),
                        leap.betweenSeconds(),
                        leap.cooldownSeconds(),
                        leap.hesitationMinSeconds(),
                        leap.hesitationMaxSeconds()));
        entity.add(engine.createComponent(Appearance.class).set(look));
        return entity;
    }

    /** Spawns a static circle that blocks units and projectiles. On nobody's side, and harmless. */
    public Entity spawnObstacle(float x, float y) {
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Obstacle.class));
        entity.add(engine.createComponent(Faction.class).set(Faction.Side.NEUTRAL));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Body.class).set(config.obstacle().radius()));
        entity.add(engine.createComponent(Collider.class).set(true, false));
        entity.add(engine.createComponent(Appearance.class).set(Appearance.Look.OBSTACLE));
        engine.addEntity(entity);
        return entity;
    }

    /**
     * Spawns a projectile travelling along the given unit direction, on the firer's side so it cannot
     * hit them or anything else friendly. The caller supplies the direction already normalised - it
     * has usually just computed it to place the spawn point.
     */
    public Entity spawnProjectile(
            float x,
            float y,
            float directionX,
            float directionY,
            float speed,
            float radius,
            float damage,
            Faction.Side side) {
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Projectile.class));
        entity.add(engine.createComponent(Faction.class).set(side));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Velocity.class).set(directionX * speed, directionY * speed));
        entity.add(engine.createComponent(Body.class).set(radius));
        entity.add(engine.createComponent(Collider.class).set(false, true));
        entity.add(engine.createComponent(ContactDamage.class).set(damage, 0f, 0f));
        entity.add(engine.createComponent(Appearance.class).set(Appearance.Look.PROJECTILE));
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
            Faction.Side side) {
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
                .setLock(melee.lockSeconds, melee.knockbackSpeed, melee.knockbackDecay));
        entity.add(engine.createComponent(Appearance.class).set(Appearance.Look.STRIKE));
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
