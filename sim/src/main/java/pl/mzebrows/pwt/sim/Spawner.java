package pl.mzebrows.pwt.sim;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.PooledEngine;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Collider;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Intent;
import pl.mzebrows.pwt.sim.component.Obstacle;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.PreviousPosition;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Speed;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.component.Weapon;

/**
 * Builds the entity kinds the game knows, so no system has to remember which components a commander
 * or a projectile is made of.
 *
 * <p>Systems that spawn take one of these rather than a reference back to {@link Simulation}, which
 * keeps the dependency pointing one way and lets a system be tested without a whole world.
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

    /** Spawns the player-controlled unit, armed and ready to fire. */
    public Entity spawnCommander(float x, float y) {
        SimConfig.CommanderConfig commander = config.commander();
        SimConfig.WeaponConfig weapon = config.weapon();
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Commander.class));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Velocity.class).set(0f, 0f));
        entity.add(engine.createComponent(Body.class).set(commander.radius()));
        entity.add(engine.createComponent(Collider.class).set(true, true));
        entity.add(engine.createComponent(Speed.class).set(commander.speed()));
        entity.add(engine.createComponent(Intent.class));
        entity.add(engine.createComponent(Weapon.class)
                .set(weapon.cooldownSeconds(), weapon.projectileSpeed(), weapon.projectileRadius()));
        entity.add(engine.createComponent(Appearance.class).set(Appearance.Look.COMMANDER));
        engine.addEntity(entity);
        return entity;
    }

    /** Spawns a unit that will chase the commander. */
    public Entity spawnEnemy(float x, float y) {
        SimConfig.EnemyConfig enemy = config.enemy();
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Enemy.class));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Velocity.class).set(0f, 0f));
        entity.add(engine.createComponent(Body.class).set(enemy.radius()));
        entity.add(engine.createComponent(Collider.class).set(true, true));
        entity.add(engine.createComponent(Speed.class).set(enemy.speed()));
        entity.add(engine.createComponent(Appearance.class).set(Appearance.Look.ENEMY));
        engine.addEntity(entity);
        return entity;
    }

    /** Spawns a static circle that blocks units and projectiles. */
    public Entity spawnObstacle(float x, float y) {
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Obstacle.class));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Body.class).set(config.obstacle().radius()));
        entity.add(engine.createComponent(Collider.class).set(true, false));
        entity.add(engine.createComponent(Appearance.class).set(Appearance.Look.OBSTACLE));
        engine.addEntity(entity);
        return entity;
    }

    /**
     * Spawns a projectile travelling along the given unit direction. The caller supplies the
     * direction already normalised - it has usually just computed it to place the spawn point.
     */
    public Entity spawnProjectile(float x, float y, float directionX, float directionY, float speed, float radius) {
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Projectile.class));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Velocity.class).set(directionX * speed, directionY * speed));
        entity.add(engine.createComponent(Body.class).set(radius));
        entity.add(engine.createComponent(Collider.class).set(false, true));
        entity.add(engine.createComponent(Appearance.class).set(Appearance.Look.PROJECTILE));
        engine.addEntity(entity);
        return entity;
    }
}
