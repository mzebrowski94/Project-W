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
import pl.mzebrows.pwt.sim.component.Level;
import pl.mzebrows.pwt.sim.component.Position;
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
import pl.mzebrows.pwt.sim.system.GrenadeSystem;
import pl.mzebrows.pwt.sim.system.HopSystem;
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
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Commander> COMMANDER = ComponentMapper.getFor(Commander.class);

    private final SimConfig config;
    private final TileMap map;
    private final Streams streams;
    private final PooledEngine engine;
    private final Spawner spawner;
    private final ImmutableArray<Entity> commanders;
    private final ImmutableArray<Entity> levels;
    private final SpawnOrder spawnOrder;

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
        this.spawner = new Spawner(engine, config, map);
        this.commanders = engine.getEntitiesFor(
                Family.all(Commander.class, Intent.class, Position.class).get());
        this.levels = engine.getEntitiesFor(Family.all(Level.class).get());

        // A gun's own pattern, shared by every shooter that holds one: how loosely a WEAPON throws is
        // one concern, and a different one from how badly a creature aims (RngStream.SPIT) or how
        // often a man does something he only sometimes does (NERVE).
        Rng scatter = streams.of(RngStream.SCATTER);

        engine.addSystem(new PositionHistorySystem());
        engine.addSystem(new CooldownSystem());
        engine.addSystem(new DragSystem());
        engine.addSystem(new CommanderControlSystem(config, map, spawner, scatter));
        engine.addSystem(new MeleeSystem(spawner));
        // Immediately after the commander's own hands, and before anything looks around: a shot
        // fired from a turret this tick is in the world before the enemies decide what they can see,
        // exactly as a melee sweep is.
        engine.addSystem(new TurretSystem(map, spawner, scatter));
        engine.addSystem(new PerceptionSystem(config, map));
        engine.addSystem(new NavigationSystem(config, map));
        // Immediately after the course it bends, and well before avoidance: a weave that could steer
        // something into rock would stop being a weave the moment a level had walls in it.
        engine.addSystem(new WeaveSystem());
        // One stream for the squad, shared by the two systems that drive it: roaming and
        // sweeping are the same concern, and separating them would only mean two salts to
        // keep apart for no benefit.
        Rng squad = streams.of(RngStream.TROOPER);
        // Everything the AI leaves to the dice draws from one stream of its own, so re-tuning how
        // often a trooper swings never reshuffles where the squad roams (see AiChance).
        Rng nerve = streams.of(RngStream.NERVE);
        engine.addSystem(new SquadOrderSystem(config, capacity));
        engine.addSystem(new TrooperMindSystem(config, map, squad, nerve));
        engine.addSystem(new TrooperMoveSystem(config, map, squad));
        engine.addSystem(new TrooperCombatSystem(config, spawner, nerve, scatter));
        engine.addSystem(new WanderSystem(streams.of(RngStream.WANDER), map, config.tickSeconds()));
        engine.addSystem(new AvoidanceSystem(config, map));
        engine.addSystem(new EnemyRangedSystem(map, spawner, streams.of(RngStream.SPIT), scatter));
        // After everything that chooses a course, including planting a shooter, because a gait
        // paces a course rather than choosing one - and before the dodge, which overwrites it: a
        // sidestep from a shot you saw coming must not land in the middle of a sit.
        engine.addSystem(new HopSystem());
        engine.addSystem(new DodgeSystem());
        engine.addSystem(new DriveSystem(map, config.ground()));
        engine.addSystem(new EnemyLeapSystem(streams.of(RngStream.LEAP), config.tickSeconds()));
        engine.addSystem(new StunSystem());
        engine.addSystem(new MovementSystem());
        engine.addSystem(new StrikeSystem(spawner));
        engine.addSystem(new CollisionSystem(config, map, spawner, nerve));
        engine.addSystem(new GrenadeSystem(map, spawner));
        engine.addSystem(new PickupSystem(config, spawner));
        engine.addSystem(new EvolutionSystem(config, spawner));
        engine.addSystem(new DamageSystem(config, spawner));
        engine.addSystem(new ActionSystem(config));
        engine.addSystem(new AidSystem(config));
        engine.addSystem(new CasualtySystem(config, spawner));
        engine.addSystem(new DyingSystem(spawner));
        engine.addSystem(new RespawnSystem(spawner));
        // Before the death reactions below, and that is the whole of why it is here. A shot that
        // runs out of flight, or flies into rock, or leaves the arena ends by DYING like every other
        // end (G0018) - so it has to be marked dead while the reactions can still see it. Registered
        // after the death chain, it was marked dead past that window and reaped on the same tick,
        // which silently deleted the blast and the spatter every MISS should have left: a frog's
        // venom burst only on the men it actually hit, and a charged spit that fell short left
        // nothing at all. A shot that hit something is marked dead by the solver's contact long
        // before this and is unaffected.
        engine.addSystem(new ProjectileBoundsSystem(map, spawner));
        // Beside the mine below, and for the same two reasons: this tick's hits are already on the
        // component, and a body that fills this tick has to reach the death-reaction window on this
        // tick rather than being owed to the next one. It spawns nothing itself - a body pumped full
        // explodes by dying, through the one reaction (G0018).
        engine.addSystem(new BloatSystem(config, spawner));
        // Immediately before the blast, and that is the whole of why it is here: the solver has
        // settled every position, so "near enough to set one off" means where things actually ended
        // up; and a fuse that runs out this tick reaches the death-reaction window below on this
        // tick rather than being owed to the next one. It spawns nothing itself - a mine explodes by
        // dying, through the one reaction everything that explodes uses (G0018).
        engine.addSystem(new MineSystem(spawner));
        engine.addSystem(new BlastSystem(map, spawner));
        // Beside the blast, and for the same reason: everything that leaves something behind does so
        // by dying (G0018), so this belongs in the window between a thing being marked dead and
        // being reaped. Unlike a blast it owes no damage of its own - what it leaves is ordinary
        // bodies carrying ordinary blows, so the collision system decides who they catch.
        engine.addSystem(new SlickSystem(map, spawner, streams.of(RngStream.SLICK)));
        // With the other systems that are about the level rather than about anything standing in it,
        // and after the solver has settled every position: a man who arrives forms up on where the
        // commander actually ended up. After the death chain too, so a trooper who arrives this tick
        // is not counted among the dead this tick's reaper is about to take.
        engine.addSystem(new ReinforcementSystem(config, map, spawner, streams.of(RngStream.REINFORCEMENT)));
        // Before the objectives, and at the end of the tick with the other systems that are about the
        // level rather than about anything standing in it: a level whose last wave lands this tick
        // must not read as complete for the tick before the aliens appear.
        engine.addSystem(new WaveSystem(config, map, spawner, streams.of(RngStream.WAVE)));
        // With the other systems that are about the level rather than about anything standing in it,
        // and after the death chain above: a nest destroyed this tick empties the last of itself
        // while its body is still on the field, in the same window respawning and blasts run in, and
        // before the reaper takes it. Before ObjectiveSystem for the reason WaveSystem is - a level
        // whose last nest dies this tick must not read as clear for the tick before its brood lands.
        engine.addSystem(new NestSystem(config, map, spawner, streams.of(RngStream.NEST)));
        engine.addSystem(new ControlPointSystem(spawner));
        engine.addSystem(new ObjectiveSystem());
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
     * <p><strong>This is the one place a relative aim becomes a place on the field.</strong> A stick
     * sends an offset because it does not know where its commander is standing; adding his position
     * here means every system downstream reads the absolute point it has always read, and there is
     * no second kind of aim anywhere below this line.
     */
    public void step(PlayerInputs inputs) {
        spawnOrder.keep();
        // Joins first, so a player who takes his slot on this tick is driven by this tick's input.
        for (int slot = 0; slot < inputs.capacity(); slot++) {
            if (inputs.joins(slot) && Players.inSlot(commanders, slot) == null) {
                joinCommander(slot);
            }
        }
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

    /** Spawns the first player's commander, which is the only one a single-player level has. */
    public Entity spawnCommander(float x, float y) {
        return spawnCommander(x, y, 0);
    }

    /**
     * Spawns a commander for one player.
     *
     * <p>The slot is what ties him to a device, and nothing checks here whether it is already taken:
     * who sits where is the client's question, and a simulation that refused a duplicate would be
     * answering it twice.
     */
    public Entity spawnCommander(float x, float y, int slot) {
        return spawner.spawnCommander(x, y, slot);
    }

    /**
     * Puts a player who has just taken a slot onto the field, wherever the fight currently is. Called
     * from {@link #step(PlayerInputs)} on the tick his join event arrives, and from nowhere else.
     *
     * <p><strong>Joining is plugging a pad in</strong>, at any point in a level, and this is the one
     * place that answers where the newcomer appears - so it holds for every scenario there is rather
     * than being something each one remembers to support.
     *
     * <p>He arrives <em>on</em> whoever is still standing, which is the same answer a man coming back
     * from the dead gets and for the same reasons: it is ground somebody is demonstrably standing on,
     * so it is neither rock nor unreachable and needs no map query to establish; it is inside the
     * leash; and it puts him in the fight rather than at the edge of the level watching it. The
     * solver untangles the overlap on the tick he appears. With nobody standing - a wipe, or a level
     * whose first player has not been spawned yet - he goes to the middle of the level's spawn area,
     * which is where the level meant its players to start.
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
        return spawnProjectile(x, y, directionX, directionY, WeaponKind.SIDEARM, side);
    }

    /**
     * Spawns a shot from any gun in the table, for a harness or a test that wants one in the world
     * without a shooter behind it. Everything about it - what it looks like, what it leaves where it
     * stops - comes from the definition, exactly as it does when somebody pulls a trigger.
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
