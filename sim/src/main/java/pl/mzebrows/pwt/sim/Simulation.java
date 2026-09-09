package pl.mzebrows.pwt.sim;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.ashley.utils.ImmutableArray;
import pl.mzebrows.pwt.map.Maps;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Intent;
import pl.mzebrows.pwt.sim.component.Invulnerable;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.sim.system.AppearanceSystem;
import pl.mzebrows.pwt.sim.system.CollisionSystem;
import pl.mzebrows.pwt.sim.system.CommanderControlSystem;
import pl.mzebrows.pwt.sim.system.CooldownSystem;
import pl.mzebrows.pwt.sim.system.DamageSystem;
import pl.mzebrows.pwt.sim.system.DyingSystem;
import pl.mzebrows.pwt.sim.system.EnemyChaseSystem;
import pl.mzebrows.pwt.sim.system.EnemyLeapSystem;
import pl.mzebrows.pwt.sim.system.EvolutionSystem;
import pl.mzebrows.pwt.sim.system.MeleeSystem;
import pl.mzebrows.pwt.sim.system.MovementSystem;
import pl.mzebrows.pwt.sim.system.PickupSystem;
import pl.mzebrows.pwt.sim.system.PositionHistorySystem;
import pl.mzebrows.pwt.sim.system.ProjectileBoundsSystem;
import pl.mzebrows.pwt.sim.system.ReaperSystem;
import pl.mzebrows.pwt.sim.system.RespawnSystem;
import pl.mzebrows.pwt.sim.system.StrikeSystem;
import pl.mzebrows.pwt.sim.system.StunSystem;

/**
 * The whole game world and everything that happens in it.
 *
 * <p>Advances only through {@link #step(CommanderInput)}, always by exactly {@link
 * SimConfig#tickSeconds()}. Given the same config and the same sequence of calls it produces
 * byte-identical state on every run, which is what deterministic replay and any future lockstep
 * networking depend on.
 *
 * <p><strong>System order is behaviour.</strong> Positions are recorded for interpolation before
 * anything moves; timers count down; intent becomes velocity and gunfire, then a swing if one was
 * asked for; enemies aim at where the commander currently is, and any of them mid-leap overwrite that
 * with where they already committed to going; whatever is held still bleeds off the blow that held
 * it; everything moves; swings travel their arc; the constraint solver decides where things may be
 * and what touched what; damage owed becomes damage taken, and anything out of health either dies now
 * or starts dying; bodies that have lain long enough are marked; reactions run while the dead are
 * still there to react to; everything publishes how it should be drawn; and only then are the dead
 * removed. Reordering these changes what the game does.
 */
public final class Simulation {

    private static final ComponentMapper<Intent> INTENT = ComponentMapper.getFor(Intent.class);
    private static final ComponentMapper<Invulnerable> INVULNERABLE = ComponentMapper.getFor(Invulnerable.class);

    private final SimConfig config;
    private final TileMap map;
    private final Rng rng;
    private final PooledEngine engine;
    private final Spawner spawner;
    private final ImmutableArray<Entity> commanders;

    private long tick;
    private boolean commanderInvulnerable;

    /**
     * A world on plain open ground the size the config asks for. What tests and any caller that does
     * not care about terrain want; a scenario hands in a real map instead.
     */
    public Simulation(SimConfig config) {
        this(config, plainGround(config));
    }

    /**
     * A world laid out on a given map. The map's extent is the arena, so nothing in here needs to be
     * told separately how big the level is.
     */
    public Simulation(SimConfig config, TileMap map) {
        this.config = config;
        this.map = map;
        this.rng = new Rng(config.seed());
        this.engine = new PooledEngine();
        this.spawner = new Spawner(engine, config, map);
        this.commanders =
                engine.getEntitiesFor(Family.all(Commander.class, Intent.class).get());

        engine.addSystem(new PositionHistorySystem());
        engine.addSystem(new CooldownSystem());
        engine.addSystem(new CommanderControlSystem(spawner));
        engine.addSystem(new MeleeSystem(spawner));
        engine.addSystem(new EnemyChaseSystem());
        engine.addSystem(new EnemyLeapSystem(rng));
        engine.addSystem(new StunSystem());
        engine.addSystem(new MovementSystem());
        engine.addSystem(new StrikeSystem(spawner));
        engine.addSystem(new CollisionSystem(config, map, spawner));
        engine.addSystem(new PickupSystem(config, spawner));
        engine.addSystem(new EvolutionSystem(config, spawner));
        engine.addSystem(new DamageSystem(spawner));
        engine.addSystem(new DyingSystem(spawner));
        engine.addSystem(new RespawnSystem(spawner));
        engine.addSystem(new ProjectileBoundsSystem(map, spawner));
        engine.addSystem(new AppearanceSystem(config));
        engine.addSystem(new ReaperSystem());

        spawner.spawnLevel();
    }

    private static TileMap plainGround(SimConfig config) {
        SimConfig.MapConfig shape = config.map();
        return Maps.uniform(shape.cols(), shape.rows(), shape.tileSize(), Terrain.PLAIN);
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
            Entity commander = commanders.get(i);
            INTENT.get(commander)
                    .set(input.moveX(), input.moveY(), input.aimX(), input.aimY(), input.firing(), input.striking());
            if (INVULNERABLE.has(commander)) {
                INVULNERABLE.get(commander).permanent = commanderInvulnerable;
            }
        }
        engine.update(config.tickSeconds());
        tick++;
    }

    /** Spawns the player-controlled unit. */
    public Entity spawnCommander(float x, float y) {
        return spawner.spawnCommander(x, y);
    }

    /** Spawns a chasing unit of the given strain. */
    public Entity spawnUnit(UnitKind kind, float x, float y) {
        return spawner.spawnUnit(kind, x, y);
    }

    /** Spawns a grunt: the basic unit that chases the commander. */
    public Entity spawnEnemy(float x, float y) {
        return spawner.spawnUnit(UnitKind.GRUNT, x, y);
    }

    /** Spawns a brute: heavy, and it survives a hand strike. */
    public Entity spawnBrute(float x, float y) {
        return spawner.spawnUnit(UnitKind.BRUTE, x, y);
    }

    /** Spawns something lying on the field for the first thing that touches it. */
    public Entity spawnPickup(PickupKind kind, float x, float y) {
        return spawner.spawnPickup(kind, x, y);
    }

    /** Spawns a static circle that blocks units and projectiles. */
    public Entity spawnObstacle(float x, float y) {
        return spawner.spawnObstacle(x, y);
    }

    /** Spawns a projectile on the player's side, travelling along the given unit direction. */
    public Entity spawnProjectile(float x, float y, float directionX, float directionY) {
        return spawnProjectile(x, y, directionX, directionY, Side.PLAYER);
    }

    /** Spawns a projectile on a given side, so a test can fire something that is not the player's. */
    public Entity spawnProjectile(float x, float y, float directionX, float directionY, Side side) {
        return spawner.spawnProjectile(x, y, directionX, directionY, config.weapon(WeaponKind.SIDEARM), side);
    }

    /**
     * Turns the harness god-mode toggle on or off. Re-applied every tick, so it survives the commander
     * dying and coming back.
     *
     * <p><strong>Harness only.</strong> Pillar 2 says the commander can be killed; this exists to be
     * switched on by hand while testing a system, never by a scenario or a config file.
     * {@code ArchitectureTest} fails the build if a scenario calls it.
     */
    public void setCommanderInvulnerable(boolean invulnerable) {
        this.commanderInvulnerable = invulnerable;
    }

    /** Whether god mode is currently on, for the harness overlay. */
    public boolean commanderInvulnerable() {
        return commanderInvulnerable;
    }

    /**
     * The assembler, for tests that need to build or rebuild an entity directly. Package-private:
     * systems are handed this in their constructor and nothing outside the simulation needs it.
     */
    Spawner spawner() {
        return spawner;
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

    /**
     * The ground this world is played on. Read freely - it is world state of the same kind as the
     * config, immutable for the life of the level, and the client reads it to draw the terrain.
     */
    public TileMap map() {
        return map;
    }
}
