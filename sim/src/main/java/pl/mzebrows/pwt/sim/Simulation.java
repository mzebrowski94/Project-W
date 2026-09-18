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
import pl.mzebrows.pwt.sim.component.Level;
import pl.mzebrows.pwt.sim.component.Waves;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.sim.system.ActionSystem;
import pl.mzebrows.pwt.sim.system.AidSystem;
import pl.mzebrows.pwt.sim.system.AppearanceSystem;
import pl.mzebrows.pwt.sim.system.AvoidanceSystem;
import pl.mzebrows.pwt.sim.system.BlastSystem;
import pl.mzebrows.pwt.sim.system.CasualtySystem;
import pl.mzebrows.pwt.sim.system.CollisionSystem;
import pl.mzebrows.pwt.sim.system.CommanderControlSystem;
import pl.mzebrows.pwt.sim.system.ControlPointSystem;
import pl.mzebrows.pwt.sim.system.CooldownSystem;
import pl.mzebrows.pwt.sim.system.DamageSystem;
import pl.mzebrows.pwt.sim.system.DodgeSystem;
import pl.mzebrows.pwt.sim.system.DragSystem;
import pl.mzebrows.pwt.sim.system.DriveSystem;
import pl.mzebrows.pwt.sim.system.DyingSystem;
import pl.mzebrows.pwt.sim.system.EnemyLeapSystem;
import pl.mzebrows.pwt.sim.system.EnemyRangedSystem;
import pl.mzebrows.pwt.sim.system.EvolutionSystem;
import pl.mzebrows.pwt.sim.system.GrenadeSystem;
import pl.mzebrows.pwt.sim.system.MeleeSystem;
import pl.mzebrows.pwt.sim.system.MovementSystem;
import pl.mzebrows.pwt.sim.system.NavigationSystem;
import pl.mzebrows.pwt.sim.system.ObjectiveSystem;
import pl.mzebrows.pwt.sim.system.PerceptionSystem;
import pl.mzebrows.pwt.sim.system.PickupSystem;
import pl.mzebrows.pwt.sim.system.PositionHistorySystem;
import pl.mzebrows.pwt.sim.system.ProjectileBoundsSystem;
import pl.mzebrows.pwt.sim.system.ReaperSystem;
import pl.mzebrows.pwt.sim.system.RespawnSystem;
import pl.mzebrows.pwt.sim.system.SquadOrderSystem;
import pl.mzebrows.pwt.sim.system.StrikeSystem;
import pl.mzebrows.pwt.sim.system.StunSystem;
import pl.mzebrows.pwt.sim.system.TrooperCombatSystem;
import pl.mzebrows.pwt.sim.system.TrooperMindSystem;
import pl.mzebrows.pwt.sim.system.TrooperMoveSystem;
import pl.mzebrows.pwt.sim.system.TurretSystem;
import pl.mzebrows.pwt.sim.system.WanderSystem;
import pl.mzebrows.pwt.sim.system.WaveSystem;
import pl.mzebrows.pwt.sim.system.WeaveSystem;

/**
 * The whole game world and everything that happens in it.
 *
 * <p>Advances only through {@link #step(CommanderInput)}, always by exactly {@link
 * SimConfig#tickSeconds()}. Given the same config and the same sequence of calls it produces
 * byte-identical state on every run, which is what deterministic replay and any future lockstep
 * networking depend on.
 *
 * <p><strong>System order is behaviour.</strong> Positions are recorded for interpolation before
 * anything moves; timers count down; whatever he has hold of is pulled along behind him; intent
 * becomes velocity - constrained to backing away, if his hands are full - and gunfire, then a swing
 * if one was asked for; every unit that can notice the commander decides whether it has; enemies that have
 * decide where they want to be going and the ones that have not amble around their patch instead,
 * and either way that course is bent around whatever it was about to walk into, and anything with a
 * shot coming at it breaks aside whatever it had been doing, and then all of that becomes a velocity, and any of them
 * mid-leap overwrite it with where they already committed to going; whatever is held still bleeds off
 * the blow that held it; everything moves; swings travel their arc; the constraint solver decides where things may be
 * and what touched what; damage owed becomes damage taken, and anything out of health either dies
 * now, starts dying, or goes down on the ground with a clock running; whatever is within the
 * commander's reach decides what his hands are offered and he gets on with it; a clock that runs out
 * is a death like any other; bodies that have lain long enough are marked; reactions run while the dead are
 * still there to react to; everything publishes how it should be drawn; and only then are the dead
 * removed. Reordering these changes what the game does.
 */
public final class Simulation {

    private static final ComponentMapper<Level> LEVEL = ComponentMapper.getFor(Level.class);
    private static final ComponentMapper<Waves> WAVES = ComponentMapper.getFor(Waves.class);
    private static final ComponentMapper<Intent> INTENT = ComponentMapper.getFor(Intent.class);
    private static final ComponentMapper<Invulnerable> INVULNERABLE = ComponentMapper.getFor(Invulnerable.class);

    private final SimConfig config;
    private final TileMap map;
    private final Rng rng;
    private final PooledEngine engine;
    private final Spawner spawner;
    private final ImmutableArray<Entity> commanders;
    private final ImmutableArray<Entity> levels;

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
        this.levels = engine.getEntitiesFor(Family.all(Level.class).get());

        engine.addSystem(new PositionHistorySystem());
        engine.addSystem(new CooldownSystem());
        engine.addSystem(new DragSystem());
        engine.addSystem(new CommanderControlSystem(spawner));
        engine.addSystem(new MeleeSystem(spawner));
        // Immediately after the commander's own hands, and before anything looks around: a shot
        // fired from a turret this tick is in the world before the enemies decide what they can see,
        // exactly as a melee sweep is.
        engine.addSystem(new TurretSystem(map, spawner));
        engine.addSystem(new PerceptionSystem(config, map));
        engine.addSystem(new NavigationSystem(config, map));
        // Immediately after the course it bends, and well before avoidance: a weave that could steer
        // something into rock would stop being a weave the moment a level had walls in it.
        engine.addSystem(new WeaveSystem());
        // One stream for the squad, shared by the two systems that drive it: roaming and
        // sweeping are the same concern, and separating them would only mean two salts to
        // keep apart for no benefit.
        Rng squad = Rng.stream(config.seed(), RngStream.TROOPER);
        // Everything the AI leaves to the dice draws from one stream of its own, so re-tuning how
        // often a trooper swings never reshuffles where the squad roams (see AiChance).
        Rng nerve = Rng.stream(config.seed(), RngStream.NERVE);
        engine.addSystem(new SquadOrderSystem(config));
        engine.addSystem(new TrooperMindSystem(config, map, squad, nerve));
        engine.addSystem(new TrooperMoveSystem(config, map, squad));
        engine.addSystem(new TrooperCombatSystem(config, spawner, nerve));
        engine.addSystem(new WanderSystem(Rng.stream(config.seed(), RngStream.WANDER), map));
        engine.addSystem(new AvoidanceSystem());
        engine.addSystem(new EnemyRangedSystem(map, spawner, Rng.stream(config.seed(), RngStream.SPIT)));
        engine.addSystem(new DodgeSystem());
        engine.addSystem(new DriveSystem());
        engine.addSystem(new EnemyLeapSystem(Rng.stream(config.seed(), RngStream.LEAP)));
        engine.addSystem(new StunSystem());
        engine.addSystem(new MovementSystem());
        engine.addSystem(new StrikeSystem(spawner));
        engine.addSystem(new CollisionSystem(config, map, spawner, nerve));
        engine.addSystem(new GrenadeSystem(map, spawner));
        engine.addSystem(new PickupSystem(config, spawner));
        engine.addSystem(new EvolutionSystem(config, spawner));
        engine.addSystem(new DamageSystem(spawner));
        engine.addSystem(new ActionSystem(config));
        engine.addSystem(new AidSystem(config));
        engine.addSystem(new CasualtySystem(config, spawner));
        engine.addSystem(new DyingSystem(spawner));
        engine.addSystem(new RespawnSystem(spawner));
        engine.addSystem(new BlastSystem(map, spawner));
        engine.addSystem(new ProjectileBoundsSystem(map, spawner));
        // Before the objectives, and at the end of the tick with the other systems that are about the
        // level rather than about anything standing in it: a level whose last wave lands this tick
        // must not read as complete for the tick before the aliens appear.
        engine.addSystem(new WaveSystem(config, map, spawner, Rng.stream(config.seed(), RngStream.WAVE)));
        engine.addSystem(new ControlPointSystem());
        engine.addSystem(new ObjectiveSystem());
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
                    .set(
                            input.moveX(),
                            input.moveY(),
                            input.aimX(),
                            input.aimY(),
                            input.firing(),
                            input.striking(),
                            input.throwing(),
                            input.acting(),
                            input.dragging(),
                            input.order());
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

    /**
     * The level entity's state, for a scenario naming what it wants done (G0007).
     *
     * <p>There is always exactly one: it is spawned in this constructor, before any scenario runs, so
     * nothing here has a null case to consider.
     */
    public Level level() {
        return LEVEL.get(levels.first());
    }

    /**
     * What the level still has to throw at you, for a scenario describing its waves (G0007).
     *
     * <p>There is always exactly one, spawned with the world before any scenario runs, so nothing
     * here has a null case to consider - exactly as {@link #level()} does not.
     */
    public Waves waves() {
        return WAVES.get(levels.first());
    }

    /** Spawns the post: ground the level says you must hold, and the other side comes for. */
    public Entity spawnPost(float x, float y) {
        return spawner.spawnPost(x, y);
    }

    /** Spawns ground a squad takes by standing on it. Spawn these before whatever stands on them. */
    public Entity spawnControlPoint(float x, float y) {
        return spawner.spawnControlPoint(x, y);
    }

    /** Spawns a turret: a gun on a mount that the commander or a trooper can get on. */
    public Entity spawnTurret(float x, float y) {
        return spawner.spawnTurret(x, y);
    }

    /** Spawns one member of the squad, on the commander's side. */
    public Entity spawnTrooper(float x, float y) {
        return spawner.spawnTrooper(x, y);
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

    /** Spawns a rabbit: alien food that wanders the field and steps aside from a shot. */
    public Entity spawnRabbit(float x, float y) {
        return spawner.spawnRabbit(x, y);
    }

    /** Spawns an explosive barrel: gunfire or something hungry sets it off. */
    public Entity spawnBarrel(float x, float y) {
        return spawner.spawnBarrel(x, y);
    }

    /** Spawns something lying on the field for the first thing that touches it. */
    public Entity spawnPickup(PickupKind kind, float x, float y) {
        return spawner.spawnPickup(kind, x, y);
    }

    /** Spawns a static circle that blocks units and projectiles. */
    public Entity spawnObstacle(float x, float y) {
        return spawner.spawnObstacle(x, y);
    }

    /** Spawns a grenade travelling along the given unit direction, for the harness and for tests. */
    public Entity spawnGrenade(float x, float y, float directionX, float directionY) {
        return spawner.spawnGrenade(x, y, directionX, directionY);
    }

    /**
     * Spawns a blast: a circle of ground lethal for a moment, on nobody's side.
     *
     * <p>For the harness and for tests that want one in the world without something dying to make it.
     * In play a blast is a death reaction, so nothing needs to reach for this.
     */
    public Entity spawnBlast(float x, float y, float radius) {
        return spawner.spawnBlast(x, y, radius);
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
