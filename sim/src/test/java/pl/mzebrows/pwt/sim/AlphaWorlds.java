package pl.mzebrows.pwt.sim;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.core.PooledEngine;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Velocity;

/**
 * Shared fixture for the alpha-phase system tests: one simulation config with numbers chosen to make
 * arithmetic obvious rather than to play well.
 *
 * <p>At 60 ticks per second a speed of 60 is exactly one world unit per tick, which is why the
 * speeds here are multiples of 60 - an assertion can then be read without a calculator.
 *
 * <p>New system tests should build worlds from here rather than assembling a {@link SimConfig} by
 * hand; a config literal in a test rots the moment the record gains a field.
 */
public final class AlphaWorlds {

    public static final float TICK = 1f / 60f;
    public static final float ARENA = 100f;
    public static final float COMMANDER_RADIUS = 1f;
    public static final float COMMANDER_SPEED = 60f;
    public static final float ENEMY_RADIUS = 1f;
    public static final float ENEMY_SPEED = 30f;
    public static final float COOLDOWN = 0.5f;

    /**
     * One world unit per tick. Slow enough that a projectile fired in the middle of the arena is
     * still inside it many ticks later, so a test can count shots by counting live projectiles.
     */
    public static final float PROJECTILE_SPEED = 60f;

    public static final float PROJECTILE_RADIUS = 0.5f;
    public static final float OBSTACLE_RADIUS = 2f;

    private AlphaWorlds() {}

    public static SimConfig config() {
        return new SimConfig(
                1L,
                TICK,
                5,
                new SimConfig.ArenaConfig(ARENA, ARENA),
                new SimConfig.CommanderConfig(COMMANDER_RADIUS, COMMANDER_SPEED),
                new SimConfig.EnemyConfig(ENEMY_RADIUS, ENEMY_SPEED, 0),
                new SimConfig.WeaponConfig(COOLDOWN, PROJECTILE_SPEED, PROJECTILE_RADIUS),
                new SimConfig.ObstacleConfig(OBSTACLE_RADIUS, 0),
                new SimConfig.CollisionConfig(3));
    }

    public static Simulation simulation() {
        return new Simulation(config());
    }

    public static int count(Simulation simulation, Class<? extends Component> marker) {
        return simulation.engine().getEntitiesFor(Family.all(marker).get()).size();
    }

    /**
     * A bare moving body - position, velocity and extent, and no role at all. Nothing in the game
     * spawns one; it lets movement and determinism be tested without dragging a commander, a weapon
     * and an arena into the fixture.
     *
     * <p>It has no {@code Collider}, so the constraint solver ignores it entirely. That is the point:
     * a movement test should not have to reason about collision.
     */
    public static Entity spawnBody(
            Simulation simulation, float x, float y, float velocityX, float velocityY, float radius) {
        PooledEngine engine = simulation.engine();
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(Velocity.class).set(velocityX, velocityY));
        entity.add(engine.createComponent(Body.class).set(radius));
        engine.addEntity(entity);
        return entity;
    }
}
