package pl.mzebrows.pwt.sim;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.ashley.utils.ImmutableArray;
import pl.mzebrows.pwt.map.Maps;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Intent;
import pl.mzebrows.pwt.sim.component.Invulnerable;
import pl.mzebrows.pwt.sim.component.Lair;
import pl.mzebrows.pwt.sim.component.Level;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Presence;
import pl.mzebrows.pwt.sim.component.Waves;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.sim.system.ActionSystem;
import pl.mzebrows.pwt.sim.system.AidSystem;
import pl.mzebrows.pwt.sim.system.AppearanceSystem;
import pl.mzebrows.pwt.sim.system.AvoidanceSystem;
import pl.mzebrows.pwt.sim.system.BarrageSystem;
import pl.mzebrows.pwt.sim.system.BlastSystem;
import pl.mzebrows.pwt.sim.system.BloatSystem;
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
import pl.mzebrows.pwt.sim.system.FacingSystem;
import pl.mzebrows.pwt.sim.system.GrenadeSystem;
import pl.mzebrows.pwt.sim.system.GripSystem;
import pl.mzebrows.pwt.sim.system.HopSystem;
import pl.mzebrows.pwt.sim.system.LairSystem;
import pl.mzebrows.pwt.sim.system.MeleeSystem;
import pl.mzebrows.pwt.sim.system.MineSystem;
import pl.mzebrows.pwt.sim.system.MovementSystem;
import pl.mzebrows.pwt.sim.system.NavigationSystem;
import pl.mzebrows.pwt.sim.system.NestSystem;
import pl.mzebrows.pwt.sim.system.ObjectiveSystem;
import pl.mzebrows.pwt.sim.system.PerceptionSystem;
import pl.mzebrows.pwt.sim.system.PickupSystem;
import pl.mzebrows.pwt.sim.system.PositionHistorySystem;
import pl.mzebrows.pwt.sim.system.ProjectileBoundsSystem;
import pl.mzebrows.pwt.sim.system.ReaperSystem;
import pl.mzebrows.pwt.sim.system.ReinforcementSystem;
import pl.mzebrows.pwt.sim.system.RespawnSystem;
import pl.mzebrows.pwt.sim.system.SlickSystem;
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
import pl.mzebrows.pwt.sim.upgrade.Modifiers;
import pl.mzebrows.pwt.sim.upgrade.Picks;
import pl.mzebrows.pwt.sim.upgrade.UpgradeSystem;
import pl.mzebrows.pwt.sim.upgrade.UpgradeView;

/**
 * The whole game world and everything that happens in it.
 *
 * <p>Advances only through {@link #step(CommanderInput)}, always by exactly {@link
 * SimConfig#tickSeconds()}. Given the same config and the same sequence of calls it produces
 * byte-identical state on every run, which is what deterministic replay and any future lockstep
 * networking depend on.
 *
 * <p><strong>System order is behaviour.</strong> The order and the constraints that carry meaning are
 * {@code docs/simulation-order.md}; {@code SystemOrderTest} holds both to the constructor below.
 */
public final class Simulation {

    private static final ComponentMapper<Level> LEVEL = ComponentMapper.getFor(Level.class);
    private static final ComponentMapper<Waves> WAVES = ComponentMapper.getFor(Waves.class);
    private static final ComponentMapper<Lair> LAIR = ComponentMapper.getFor(Lair.class);
    private static final ComponentMapper<Intent> INTENT = ComponentMapper.getFor(Intent.class);
    private static final ComponentMapper<Invulnerable> INVULNERABLE = ComponentMapper.getFor(Invulnerable.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Commander> COMMANDER = ComponentMapper.getFor(Commander.class);
    private static final ComponentMapper<Presence> PRESENCE = ComponentMapper.getFor(Presence.class);

    private final SimConfig config;
    private final TileMap map;
    private final Streams streams;
    private final PooledEngine engine;
    private final Spawner spawner;
    private final ImmutableArray<Entity> commanders;
    private final ImmutableArray<Entity> levels;
    private final SpawnOrder spawnOrder;

    /** Where a tick's picks enter the upgrade package. */
    private final Picks picks;

    /** What the interface may read of the upgrades. */
    private final UpgradeView upgradeView;

    /** What the upgrades held have made of the numbers; read here only for {@link #squadMost}. */
    private final Modifiers modifiers;

    /**
     * Reused by {@link #step(CommanderInput)}, so driving one player costs no allocation per tick.
     * Never handed out; the multi-player path takes the caller's own holder instead.
     */
    private final PlayerInputs solo;

    /**
     * A world on plain open ground the size the config asks for. What tests and any caller that does
     * not care about terrain want; a scenario hands in a real map instead.
     */
    public Simulation(SimConfig config) {
        this(config, plainGround(config));
    }

    /**
     * Open ground for a game of this many slots. The capacity is what every per-player table in here is
     * sized from; the tests build one larger than {@link Players#MAX} to prove nothing is sized by a
     * literal (G0038).
     */
    public Simulation(SimConfig config, int capacity) {
        this(config, plainGround(config), capacity);
    }

    /**
     * A world laid out on a given map. The map's extent is the arena, so nothing in here needs to be
     * told separately how big the level is.
     */
    public Simulation(SimConfig config, TileMap map) {
        this(config, map, Players.MAX);
    }

    /** A world on a given map, for a game of this many slots. */
    public Simulation(SimConfig config, TileMap map, int capacity) {
        this.solo = new PlayerInputs(capacity);
        this.config = config;
        this.map = map;
        this.streams = new Streams(config.seed());
        this.engine = new PooledEngine();
        this.spawner = new Spawner(engine, config);
        this.modifiers = new Modifiers(engine, config);
        this.picks = new Picks(engine);
        this.upgradeView = new UpgradeView(engine, config);
        this.commanders = engine.getEntitiesFor(
                Family.all(Commander.class, Intent.class, Position.class).get());
        this.levels = engine.getEntitiesFor(Family.all(Level.class).get());

        // How loosely a gun throws, for every shooter; not how badly a creature aims (SPIT).
        Rng scatter = streams.of(RngStream.SCATTER);

        engine.addSystem(new PositionHistorySystem());
        engine.addSystem(new CooldownSystem());
        engine.addSystem(new DragSystem());
        engine.addSystem(new CommanderControlSystem(config, map, spawner, modifiers, scatter));
        engine.addSystem(new MeleeSystem(spawner));
        // After the commander's hands, before anything looks around.
        engine.addSystem(new TurretSystem(map, spawner, modifiers, scatter));
        engine.addSystem(new PerceptionSystem(config, map));
        engine.addSystem(new NavigationSystem(config, map));
        // After the course it bends, before avoidance.
        engine.addSystem(new WeaveSystem());
        // After every course a creature sets itself (7-9) and before avoidance bends it: a creature with
        // a man in its jaws is carrying him off, not hunting. Before the squad block, which leaves a
        // man in its jaws alone, so this is the only thing that moves him (G0045).
        engine.addSystem(new GripSystem());
        // One stream for the squad's mind and legs: one concern.
        Rng squad = streams.of(RngStream.TROOPER);
        // Everything the AI leaves to the dice (AiChance), apart from where the squad roams.
        Rng nerve = streams.of(RngStream.NERVE);
        engine.addSystem(new SquadOrderSystem(config, capacity));
        engine.addSystem(new TrooperMindSystem(config, map, modifiers, squad, nerve));
        engine.addSystem(new TrooperMoveSystem(config, map, squad));
        engine.addSystem(new TrooperCombatSystem(config, map, spawner, modifiers, nerve, scatter));
        engine.addSystem(new WanderSystem(streams.of(RngStream.WANDER), map, config.tickSeconds()));
        engine.addSystem(new AvoidanceSystem(config, map));
        engine.addSystem(new EnemyRangedSystem(map, spawner, modifiers, streams.of(RngStream.SPIT), scatter));
        // After everything that chooses a course; before the dodge, which overwrites it.
        engine.addSystem(new HopSystem());
        engine.addSystem(new DodgeSystem());
        engine.addSystem(new DriveSystem(map, config.ground(), modifiers));
        engine.addSystem(new EnemyLeapSystem(streams.of(RngStream.LEAP), config.tickSeconds()));
        engine.addSystem(new StunSystem());
        engine.addSystem(new MovementSystem());
        engine.addSystem(new StrikeSystem(spawner));
        engine.addSystem(new CollisionSystem(config, map, spawner, modifiers, nerve));
        engine.addSystem(new GrenadeSystem(map, spawner, modifiers));
        engine.addSystem(new PickupSystem(config, spawner, modifiers, streams.of(RngStream.ARMOURY)));
        engine.addSystem(new EvolutionSystem(config, spawner));
        engine.addSystem(new DamageSystem(config, spawner, modifiers));
        engine.addSystem(new ActionSystem(config, modifiers));
        engine.addSystem(new AidSystem(config, modifiers));
        engine.addSystem(new CasualtySystem(config, spawner, modifiers));
        engine.addSystem(new DyingSystem(spawner));
        // Where a man from the reserve is put down, whether he is a returning commander or a muster's
        // trooper: one concern (G0055), so one stream for both systems.
        Rng reinforcement = streams.of(RngStream.REINFORCEMENT);
        engine.addSystem(new RespawnSystem(config, map, spawner, reinforcement));
        // Before the death reactions: a shot that misses ends by dying too (G0018), and must leave
        // its blast or spatter.
        engine.addSystem(new ProjectileBoundsSystem(map, spawner));
        // Beside the mine, for the same reasons; a full body explodes by dying (G0018).
        engine.addSystem(new BloatSystem(config, spawner));
        // After the solver settles positions, and immediately before the blast, so a fuse that runs
        // out reaches this tick's death reactions (G0018).
        engine.addSystem(new MineSystem(spawner));
        engine.addSystem(new BlastSystem(map, spawner));
        // Beside the blast: in the window between marked dead and reaped (G0018).
        engine.addSystem(new SlickSystem(map, spawner, streams.of(RngStream.SLICK)));
        // Level systems from here, after the solver and the death chain.
        engine.addSystem(new ReinforcementSystem(config, map, spawner, modifiers, reinforcement));
        // Before the objectives.
        engine.addSystem(new WaveSystem(config, map, spawner, streams.of(RngStream.WAVE)));
        // After the death chain, before the reaper and the objectives.
        engine.addSystem(new NestSystem(config, map, spawner, streams.of(RngStream.NEST)));
        // Beside the waves and the nests, for their reason: what rises this tick counts this tick (G0048).
        engine.addSystem(new LairSystem(map, spawner));
        // After everything that could have put a boss down or killed one, reading this tick's perception.
        engine.addSystem(new BarrageSystem(config, spawner));
        engine.addSystem(new ControlPointSystem(spawner));
        // After every source of a draft (41, 44, 45), so what was earned this tick is dealt this tick; and
        // after every reader of Modifiers, so the ledger changes once, at the end, for every reader alike.
        engine.addSystem(new UpgradeSystem(config, streams.of(RngStream.DRAFT), capacity));
        engine.addSystem(new ObjectiveSystem());
        engine.addSystem(new FacingSystem());
        engine.addSystem(new AppearanceSystem(config));
        engine.addSystem(new ReaperSystem());
        spawnOrder = new SpawnOrder(engine);

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
     * Advances the world by one fixed step with only the first player driving. Every other slot is
     * idle, which is what a single-player game and almost every test wants.
     */
    public void step(CommanderInput input) {
        step(solo.clear().set(0, input));
    }

    /**
     * Advances the world by exactly one fixed step. Each slot's input is copied into that
     * commander's intent before any system runs, so systems read a component and never the input
     * object itself.
     *
     * <p><strong>This is the one place a relative aim becomes a place on the field.</strong>
     */
    public void step(PlayerInputs inputs) {
        spawnOrder.keep();
        // Joins first, so a player who takes his slot on this tick is driven by this tick's input.
        for (int slot = 0; slot < inputs.capacity(); slot++) {
            if (inputs.joins(slot) && Players.inSlot(commanders, slot) == null) {
                joinCommander(slot);
            }
        }
        // Then presence, before anything reads it (G0052), and the tick's picks, applied at its end (G0049).
        Presence presence = PRESENCE.get(levels.first());
        for (int slot = 0; slot < inputs.capacity(); slot++) {
            if (inputs.goesAway(slot)) {
                presence.leave(slot);
            }
            if (inputs.comesBack(slot)) {
                presence.comeBack(slot);
            }
        }
        picks.submit(inputs);
        Level level = level();
        if (inputs.switchesInvulnerability()) {
            level.commandersInvulnerable = inputs.invulnerable();
        }
        for (int i = 0; i < commanders.size(); i++) {
            Entity commander = commanders.get(i);
            CommanderInput input = inputs.get(COMMANDER.get(commander).slot);
            Position at = POSITION.get(commander);
            float aimX = input.aimRelative() ? at.x + input.aimX() : input.aimX();
            float aimY = input.aimRelative() ? at.y + input.aimY() : input.aimY();
            INTENT.get(commander)
                    .set(
                            input.moveX(),
                            input.moveY(),
                            aimX,
                            aimY,
                            input.firing(),
                            input.striking(),
                            input.throwing(),
                            input.acting(),
                            input.dragging(),
                            inputs.orderOf(COMMANDER.get(commander).slot));
            if (INVULNERABLE.has(commander)) {
                INVULNERABLE.get(commander).permanent = level.commandersInvulnerable;
            }
        }
        engine.update(config.tickSeconds());
        level.tick++;
    }

    /** The drafts waiting and what is held, read-only, for the interface (ADR 0017). */
    public UpgradeView upgradeView() {
        return upgradeView;
    }

    /** Spawns the first player's commander, which is the only one a single-player level has. */
    public Entity spawnCommander(float x, float y) {
        return spawnCommander(x, y, 0);
    }

    /**
     * Spawns a commander for one player.
     *
     * <p>Nothing checks whether the slot is taken: that is the client's question.
     */
    public Entity spawnCommander(float x, float y, int slot) {
        return spawner.spawnCommander(x, y, slot);
    }

    /**
     * Puts a player who has just taken a slot onto the field, wherever the fight currently is. Called
     * from {@link #step(PlayerInputs)} on the tick his join event arrives, and from nowhere else.
     *
     * <p><strong>Joining is plugging a pad in</strong>, at any point in a level. He arrives <em>on</em>
     * whoever is still standing, as a respawn does - reachable ground inside the leash - and the solver
     * untangles the overlap. With nobody standing, the middle of the spawn area.
     */
    private Entity joinCommander(int slot) {
        Entity standing = Players.standing(commanders);
        if (standing != null) {
            Position at = POSITION.get(standing);
            return spawnCommander(at.x, at.y, slot);
        }
        TileRect area = config.spawn().on(map);
        return spawnCommander(area.centreX(map), area.centreY(map), slot);
    }

    /**
     * The level entity's state, for a scenario naming what it wants done (G0007).
     *
     * <p>Always exactly one, spawned in the constructor.
     */
    public Level level() {
        return LEVEL.get(levels.first());
    }

    /** The most troopers the field holds right now, upgrades included (G0055) - for the interface. */
    public int squadMost() {
        return ReinforcementSystem.squadMost(config, modifiers);
    }

    /**
     * What the level still has to throw at you, for a scenario describing its waves (G0007).
     *
     * <p>Always exactly one, as {@link #level()}.
     */
    public Waves waves() {
        return WAVES.get(levels.first());
    }

    /**
     * What the level keeps off the field until every player is standing in one place, for a scenario
     * describing its boss (G0048).
     *
     * <p>Always exactly one, as {@link #level()}.
     */
    public Lair lair() {
        return LAIR.get(levels.first());
    }

    /** Spawns the post: ground the level says you must hold, and the other side comes for. */
    public Entity spawnPost(float x, float y) {
        return spawner.spawnPost(x, y);
    }

    /** Spawns a post that watches only the ground around it. Zero watches the whole level. */
    public Entity spawnPost(float x, float y, float watchRadius) {
        return spawner.spawnPost(x, y, watchRadius);
    }

    /** Spawns ground a squad takes by standing on it. Spawn these before whatever stands on them. */
    public Entity spawnControlPoint(float x, float y) {
        return spawner.spawnControlPoint(x, y);
    }

    /**
     * Spawns a nest: a hole in the ground that keeps sending more of one strain out of it. Spawn
     * these before whatever stands on them - a nest is ground rather than a thing on the ground.
     */
    public Entity spawnNest(UnitKind kind, float x, float y) {
        return spawner.spawnNest(kind, x, y);
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

    /** Spawns a mine: it starts ticking when something on the other side walks into it. */
    public Entity spawnMine(float x, float y) {
        return spawner.spawnMine(x, y);
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
     * <p>For the harness and tests; in play a blast is a death reaction.
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
        return spawnProjectile(x, y, directionX, directionY, WeaponKind.SIDEARM, side);
    }

    /**
     * Spawns a shot from any gun in the table, for a harness or a test that wants one in the world
     * without a shooter behind it.
     */
    public Entity spawnProjectile(float x, float y, float directionX, float directionY, WeaponKind gun, Side side) {
        return spawner.spawnProjectile(x, y, directionX, directionY, config.weapon(gun), side);
    }

    /** Whether god mode is currently on, for the harness overlay. */
    public boolean commanderInvulnerable() {
        return level().commandersInvulnerable;
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
        return level().tick;
    }

    public Rng rng() {
        return streams.root();
    }

    /**
     * The one stream for a concern, for a scenario placing something that concern owns while it
     * populates the world - where a level's crates go is drawn from the same stream as where its
     * reinforcements land, because both are where a reinforcement lands.
     */
    public Rng stream(RngStream concern) {
        return streams.of(concern);
    }

    /** Every stream, for the snapshot to write and restore (ADR 0014). */
    Streams streams() {
        return streams;
    }

    public SimConfig config() {
        return config;
    }

    /** How many slots this world was built for - what a {@link PlayerInputs} handed to it must hold. */
    public int capacity() {
        return solo.capacity();
    }

    /**
     * The ground this world is played on. Read freely - it is world state of the same kind as the
     * config, immutable for the life of the level, and the client reads it to draw the terrain.
     */
    public TileMap map() {
        return map;
    }
}
