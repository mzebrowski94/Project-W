package pl.mzebrows.pwt.sim;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.ashley.utils.ImmutableArray;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Intent;
import pl.mzebrows.pwt.sim.system.CollisionSystem;
import pl.mzebrows.pwt.sim.system.CommanderControlSystem;
import pl.mzebrows.pwt.sim.system.EnemyChaseSystem;
import pl.mzebrows.pwt.sim.system.MovementSystem;
import pl.mzebrows.pwt.sim.system.PositionHistorySystem;
import pl.mzebrows.pwt.sim.system.ProjectileBoundsSystem;

/**
 * The whole game world and everything that happens in it.
 *
 * <p>Advances only through {@link #step(CommanderInput)}, always by exactly {@link
 * SimConfig#tickSeconds()}. Given the same config and the same sequence of calls it produces
 * byte-identical state on every run, which is what deterministic replay and any future lockstep
 * networking depend on.
 *
 * <p><strong>System order is behaviour.</strong> Positions are recorded for interpolation before
 * anything moves; intent becomes velocity; enemies aim at where the commander currently is;
 * everything moves; the constraint solver decides where things are actually allowed to be; and
 * projectiles that left the arena are cleaned up. Reordering these changes what the game does.
 */
public final class Simulation {

    private static final ComponentMapper<Intent> INTENT = ComponentMapper.getFor(Intent.class);

    private final SimConfig config;
    private final Rng rng;
    private final PooledEngine engine;
    private final Spawner spawner;
    private final ImmutableArray<Entity> commanders;

    private long tick;

    public Simulation(SimConfig config) {
        this.config = config;
        this.rng = new Rng(config.seed());
        this.engine = new PooledEngine();
        this.spawner = new Spawner(engine, config);
        this.commanders =
                engine.getEntitiesFor(Family.all(Commander.class, Intent.class).get());

        engine.addSystem(new PositionHistorySystem());
        engine.addSystem(new CommanderControlSystem(spawner));
        engine.addSystem(new EnemyChaseSystem());
        engine.addSystem(new MovementSystem());
        engine.addSystem(new CollisionSystem(config));
        engine.addSystem(new ProjectileBoundsSystem(config.arena()));
    }

    /** Advances the world by one fixed step with nothing driving the commander. */
    public void step() {
        step(CommanderInput.IDLE);
    }

    /**
     * Advances the world by exactly one fixed step. The input is copied into the commander's intent
     * before any system runs, so systems read a component and never the input object itself.
     */
    public void step(CommanderInput input) {
        for (int i = 0; i < commanders.size(); i++) {
            INTENT.get(commanders.get(i)).set(input.moveX(), input.moveY(), input.aimX(), input.aimY(), input.firing());
        }
        engine.update(config.tickSeconds());
        tick++;
    }

    /** Spawns the player-controlled unit. */
    public Entity spawnCommander(float x, float y) {
        return spawner.spawnCommander(x, y);
    }

    /** Spawns a unit that chases the commander. */
    public Entity spawnEnemy(float x, float y) {
        return spawner.spawnEnemy(x, y);
    }

    /** Spawns a static circle that blocks units and projectiles. */
    public Entity spawnObstacle(float x, float y) {
        return spawner.spawnObstacle(x, y);
    }

    /** Spawns a projectile travelling along the given unit direction, at the configured speed. */
    public Entity spawnProjectile(float x, float y, float directionX, float directionY) {
        SimConfig.WeaponConfig weapon = config.weapon();
        return spawner.spawnProjectile(
                x, y, directionX, directionY, weapon.projectileSpeed(), weapon.projectileRadius());
    }

    /** The entity engine; the client module reads it to draw the world. */
    public PooledEngine engine() {
        return engine;
    }

    /** Number of fixed steps executed so far. */
    public long tick() {
        return tick;
    }

    public Rng rng() {
        return rng;
    }

    public SimConfig config() {
        return config;
    }
}
