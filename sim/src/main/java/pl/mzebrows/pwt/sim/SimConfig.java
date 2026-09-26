package pl.mzebrows.pwt.sim;

import java.util.List;
import java.util.SortedMap;
import java.util.function.UnaryOperator;
import lombok.With;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.def.AiChance;
import pl.mzebrows.pwt.sim.def.ApproachDef;
import pl.mzebrows.pwt.sim.def.AvoidanceDef;
import pl.mzebrows.pwt.sim.def.Chances;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.DodgeDef;
import pl.mzebrows.pwt.sim.def.GroundDef;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.def.PerceptionDef;
import pl.mzebrows.pwt.sim.def.PickupDef;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.Require;
import pl.mzebrows.pwt.sim.def.SlickDef;
import pl.mzebrows.pwt.sim.def.UnitDef;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WanderDef;
import pl.mzebrows.pwt.sim.def.WeaponDef;
import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * Tunables the simulation reads at construction time: exactly {@code game.properties}, bound by
 * {@link ConfigBinder} from each component's name. The simulation never touches the filesystem - what
 * ships is compiled in ({@link #defaults()}), and anything else arrives as text ({@link #parse}).
 * <strong>A new tunable is a component here and a line in the file</strong>, and nothing else.
 *
 * <p>Grouped into nested records rather than one flat parameter list so a system can be handed only
 * the group it needs, and so adding a tunable touches one constructor instead of every call site.
 *
 * <p><strong>Varying one thing is a single call.</strong> Lombok's {@code @With} generates a wither
 * per component here and on every nested record, so a scenario writes
 * {@code config.withObstacle(config.obstacle().withCount(0))} and nothing restates the fields it did
 * not touch. Hand-written withers used to do this and each one repeated the whole constructor, which
 * meant adding a config group was an edit to every one of them - the boilerplate grew as the square
 * of the design.
 *
 * <p>Strains are the exception to "one nested record per thing": there are many of them and more are
 * coming, so they live in a {@link UnitDefs} registry keyed by {@link UnitKind} rather than as a
 * record component each.
 *
 * @param seed seed for the simulation's {@link Rng}
 * @param tickSeconds length of one fixed simulation step
 * @param maxTicksPerFrame ceiling on catch-up steps, so a stalled frame cannot spiral
 * @param map the grid a level is laid out on; its extent <em>is</em> the arena
 * @param commander the player-controlled unit
 * @param trooper the AI-controlled members of his squad
 * @param spawn where a level puts the player's side to start with
 * @param units every strain of chasing unit, by kind
 * @param pickups every kind of thing that can be lying on the field, by kind
 * @param weapons every gun the commander can be carrying, by kind
 * @param chances how often each occasional AI behaviour happens, one number per {@code AiChance} -
 *     the index of everything the AI leaves to the dice. A strain carries its own copy, resolved
 *     against this one when the config is built, so retune a behaviour with {@link #withChance}
 *     rather than by replacing this table on its own
 * @param melee the commander's hand strike
 * @param blast what an explosion does, wherever it came from
 * @param fire what an explosion leaves burning behind it, wherever it came from
 * @param grenade what the commander throws
 * @param rabbit the neutral creature that wanders the field and is eaten
 * @param barrel the explosive one, which is neither eaten nor picked up
 * @param mine the explosive one that the other side sets off by walking into it
 * @param nest the holes in the ground that keep sending more out
 * @param controlPoint what it takes to hold a point, and how far taking one carries
 * @param turret the gun on a mount that a man gets on
 * @param obstacle the static circles that block movement
 * @param collision how hard the constraint solver works
 */
@With
public record SimConfig(
        long seed,
        float tickSeconds,
        int maxTicksPerFrame,
        MapConfig map,
        CommanderConfig commander,
        TrooperConfig trooper,
        SpawnConfig spawn,
        @ConfigKey("unit") Defs<UnitKind, UnitDef> units,
        @ConfigKey("pickup") Defs<PickupKind, PickupDef> pickups,
        @ConfigKey("weapon") Defs<WeaponKind, WeaponDef> weapons,
        Defs<Terrain, GroundDef> ground,
        @ConfigKey("ai.chance") Chances chances,
        MeleeConfig melee,
        BlastConfig blast,
        FireConfig fire,
        BloatConfig bloat,
        GrenadeConfig grenade,
        RabbitConfig rabbit,
        BarrelConfig barrel,
        MineConfig mine,
        CasualtyConfig casualty,
        ActionConfig action,
        NestConfig nest,
        ControlPointConfig controlPoint,
        TurretConfig turret,
        ObstacleConfig obstacle,
        CollisionConfig collision) {

    /** The prefix every key in {@code game.properties} starts with. */
    private static final String ROOT = "sim";

    public SimConfig {
        Require.positive("tickSeconds", tickSeconds);
        Require.atLeastOne("maxTicksPerFrame", maxTicksPerFrame);
        if (melee.clinchCarrySpeed() > 0f) {
            Require.atLeast(
                    "melee carry distance",
                    melee.carryDistance(),
                    melee.longestReachableGap(commander.radius()),
                    "a clinch that stops short of the commander leaves the freeze playing across a gap");
        }
    }

    /** The definition of one strain, for a system or a scenario that needs its numbers. */
    public UnitDef unit(UnitKind kind) {
        return units.of(kind);
    }

    /** The definition of one kind of pickup. */
    public PickupDef pickup(PickupKind kind) {
        return pickups.of(kind);
    }

    /** The definition of one gun. */
    public WeaponDef weapon(WeaponKind kind) {
        return weapons.of(kind);
    }

    /** What standing on one kind of ground costs whatever is standing on it. */
    public GroundDef ground(Terrain terrain) {
        return ground.of(terrain);
    }

    /** How often one occasional behaviour happens for a unit with no override of its own. */
    public float chance(AiChance kind) {
        return chances.of(kind);
    }

    /**
     * This config with one occasional behaviour retuned <strong>everywhere</strong> - the shared
     * table and every strain's own copy of it.
     *
     * <p>A strain carries a table already resolved against the shared one rather than a set of
     * overrides, so that spawning reads one number and never has to ask whether this strain had an
     * opinion. The price is that {@code withChances} alone does not reach a strain, and this method
     * is what closes that trap: a scenario or a test switching a behaviour on wants it on for
     * everything, not for everything that happened not to override it.
     */
    public SimConfig withChance(AiChance kind, float chance) {
        SimConfig changed = withChances(chances.with(kind, chance));
        for (UnitKind unitKind : UnitKind.values()) {
            changed = changed.withUnit(
                    unitKind, strain -> strain.withChances(strain.chances().with(kind, chance)));
        }
        return changed;
    }

    /**
     * This config with one strain changed, so a scenario or a test can vary a single number without
     * restating the strain or the rest of the config.
     */
    public SimConfig withUnit(UnitKind kind, UnaryOperator<UnitDef> change) {
        return withUnits(units.map(kind, change));
    }

    /**
     * What ships: {@code assets/game.properties}, compiled in by {@code :sim:generateShippedConfig} and
     * bound once. What every test runs, and what the client falls back to - so there is one copy of every
     * number, and it is the one players play.
     */
    public static SimConfig defaults() {
        return Shipped.CONFIG;
    }

    /**
     * A config from the text of a {@code game.properties} - a player's own, or the one a host shipped
     * (ADR 0012) - with every value it lacks or garbles taken from {@link #defaults()} and named in the
     * result, so the caller can say so. A key no record has is named too.
     *
     * @throws IllegalArgumentException if the values are readable but break a rule a record checks
     */
    public static Parsed parse(String text) {
        ConfigBinder.Bound<SimConfig> bound =
                ConfigBinder.bind(SimConfig.class, ROOT, ConfigBinder.parse(text), Shipped.VALUES);
        SimConfig config = bound.value();
        if (config == null) {
            throw new IllegalArgumentException("config: " + String.join("; ", bound.problems()));
        }
        return new Parsed(config, bound.problems());
    }

    /** A config read from text, and everything that had to be taken from what ships instead. */
    public record Parsed(SimConfig config, List<String> problems) {}

    /** Bound on first use rather than with the record, so a failure names the key rather than a class. */
    private static final class Shipped {

        static final SortedMap<String, String> VALUES = ConfigBinder.parse(ShippedConfig.TEXT);

        static final SimConfig CONFIG = ConfigBinder.bindStrictly(SimConfig.class, ROOT, VALUES);

        private Shipped() {}
    }

    /**
     * The largest radius any entity can have, which sets the collision grid's cell size. Every body
     * is a circle (ADR 0007), so one number covers all of them.
     *
     * <p>It must cover strains a unit can only reach by evolving, not just the ones a scenario
     * spawns. A cell size too small for a grown body stops the grid reporting some overlapping pairs
     * at all, and units walk through each other with nothing failing - which is why the strain half
     * of this is a single call into the registry rather than a list of kinds someone has to remember
     * to extend.
     */
    public float largestRadius() {
        float largest = Math.max(commander.radius(), obstacle.radius());
        largest = Math.max(largest, trooper.radius());
        largest = Math.max(largest, melee.radius());
        largest = Math.max(largest, grenade.radius());
        // Fire is an ordinary body too, and the widest one of it the game can produce is the one the
        // widest explosion leaves. Asked of the blast radii rather than of a number somebody keeps
        // in step by hand, for the same reason the gun table is asked below.
        largest = Math.max(largest, fire.forBlast(largestBlastRadius()).radius());
        for (WeaponDef gun : weapons.values()) {
            // A shot is swept along its path, and filed at the middle of it, so what the grid must
            // make room for is the shot's own radius plus half the ground it covers in a tick.
            largest = Math.max(largest, gun.projectileRadius() + gun.projectileSpeed() * tickSeconds * 0.5f);
            // A circle of ruined ground is an ordinary body in the pairwise path, unlike a blast,
            // so the grid has to have room for one - and it is asked of the gun table rather than
            // of a list somebody has to remember to extend.
            largest = Math.max(largest, gun.slicks().radius());
        }
        for (UnitDef strain : units.values()) {
            largest = Math.max(largest, strain.radius());
        }
        for (PickupDef pickup : pickups.values()) {
            largest = Math.max(largest, pickup.radius());
        }
        largest = Math.max(largest, nest.radius());
        largest = Math.max(largest, turret.radius());
        return Math.max(largest, mine.radius());
    }

    /**
     * The widest explosion anything in the game can make, in world units.
     *
     * <p>Every one of them is a number somebody named on the thing that explodes - a barrel, a
     * grenade, a turret, a gun that goes off where its shots land - and this is the one place that
     * asks all of them at once, so what fire is sized against cannot fall behind a new explosive.
     */
    public float largestBlastRadius() {
        float largest = Math.max(barrel.blastRadius(), grenade.blastRadius());
        largest = Math.max(largest, turret.blastRadius());
        largest = Math.max(largest, mine.blastRadius());
        for (WeaponDef gun : weapons.values()) {
            largest = Math.max(largest, gun.blastRadius());
        }
        return largest;
    }

    /**
     * A hole in the ground that keeps sending more of one strain out of it.
     *
     * <p>One block for every nest on the field rather than one per strain: what makes two nests
     * different is <em>what comes out</em>, which is a {@code UnitKind} named where the nest is put
     * down, and everything below is about the hole rather than about the creature.
     *
     * @param radius its circular body, in world units. A thing standing on the field, so it is solid
     *     and can be shot, and wide enough to be a target worth aiming at rather than a dot
     * @param health what it absorbs before it is destroyed. <strong>A job, not a stray round</strong>,
     *     and above a turret's on purpose: a turret is yours and losing one is an event, a nest is
     *     theirs and leaving one standing is a decision. It has to be payable with the sidearm, since
     *     the first nest on a march comes long before the first weapons cache does - so the ceiling is
     *     how long the player can stand still and shoot with a brood already on the field
     * @param periodSeconds how long between broods
     * @param broodMin how few come out at a time
     * @param broodMax how many, dealt between the two from the nest's own bag, so every size comes out
     *     once before any comes out again. A fixed number makes every nest worth exactly the same; a
     *     range means walking up to see. At most {@code Bag.MOST} sizes
     * @param deathBroodMin how few come out when it is destroyed
     * @param deathBroodMax how many. <strong>A nest is at its worst the moment you kill it</strong>,
     *     which is what makes destroying one a decision about timing rather than a chore
     * @param hatchRadius how far around it a brood is put down, in world units. Outside its own body,
     *     so nothing is hatched inside the thing that hatched it
     * @param wakeRadius how close somebody on the player's side must come before it starts working,
     *     in world units. <strong>Deliberately large</strong>: it is not a detection range but the
     *     answer to a long level, where a nest working from the first tick would have sent twenty
     *     creatures at empty ground before anybody arrived
     * @param rouseRadius how far its cry carries when it is attacked, in world units. The same idea
     *     as a control point's, and sized like one: taking a nest away should bring the neighbourhood
     *     rather than be something done quietly from the edge of its range
     */
    @With
    public record NestConfig(
            float radius,
            float health,
            float periodSeconds,
            int broodMin,
            int broodMax,
            int deathBroodMin,
            int deathBroodMax,
            float hatchRadius,
            float wakeRadius,
            float rouseRadius) {

        public NestConfig {
            Require.positive("nest radius", radius);
            Require.positive("nest health", health);
            Require.positive("nest periodSeconds", periodSeconds);
            Require.notNegative("nest broodMin", broodMin);
            Require.notNegative("nest deathBroodMin", deathBroodMin);
            Require.atLeast("nest broodMax", broodMax, broodMin, "a nest cannot have a smaller most than fewest");
            Require.fitsABag("nest brood", broodMin, broodMax);
            Require.atLeast(
                    "nest deathBroodMax",
                    deathBroodMax,
                    deathBroodMin,
                    "a nest cannot have a smaller most than fewest");
            Require.positive("nest hatchRadius", hatchRadius);
            Require.notNegative("nest wakeRadius", wakeRadius);
            Require.notNegative("nest rouseRadius", rouseRadius);
        }
    }

    /**
     * What it takes to hold a control point.
     *
     * @param radius how far from the middle counts as standing on it, in world units. Wide enough
     *     that a squad fits inside it, or holding one becomes a queue rather than a stand
     * @param captureSeconds how long somebody has to stay inside it. This is the number that decides
     *     whether a point is a formality or a fight - it is the window the level has to answer with
     * @param aggroRadius how far the noise of an attempt carries, in world units. Longer than a
     *     unit's own detection range on purpose: taking a point is meant to bring things to you
     * @param reinforcementRadius how far from a point the level lays its reinforcements, in world
     *     units. Outside the point's own radius, so fetching one is a step off the ground you are
     *     holding rather than something you collect by standing still
     * @param reinforcementsMin fewest crates a point is worth
     * @param reinforcementsMax most. Dealt between the two from one bag across the level's points,
     *     so the points on a level are not all worth exactly the same and none of them is worth
     *     guessing about. At most {@code Bag.MOST} values
     */
    @With
    public record ControlPointConfig(
            float radius,
            float captureSeconds,
            float aggroRadius,
            float reinforcementRadius,
            int reinforcementsMin,
            int reinforcementsMax) {
        public ControlPointConfig {
            Require.positive("control point radius", radius);
            Require.positive("control point captureSeconds", captureSeconds);
            Require.notNegative("control point aggroRadius", aggroRadius);
            Require.positive("control point reinforcementRadius", reinforcementRadius);
            Require.notNegative("control point reinforcementsMin", reinforcementsMin);
            if (reinforcementsMax < reinforcementsMin) {
                throw new IllegalArgumentException("control point reinforcementsMax must not be below its minimum");
            }
            Require.fitsABag("control point reinforcements", reinforcementsMin, reinforcementsMax);
        }
    }

    /**
     * How hard the constraint solver works.
     *
     * @param passes how many times per tick overlap resolution and arena clamping are applied. One
     *     is enough for open space; a unit squeezed between an obstacle and a wall needs the second,
     *     and the third is headroom. Raising it costs time and buys stability, never behaviour.
     */
    @With
    public record CollisionConfig(int passes) {
        public CollisionConfig {
            Require.atLeastOne("collision passes", passes);
        }
    }

    /**
     * The grid a level is laid out on. <strong>This is the only place a level's size is decided</strong>
     * - the arena is exactly what the grid adds up to, so a map and an arena can never disagree about
     * how big a level is.
     *
     * <p>These are the defaults a scenario's map is built from. Once built, the {@code TileMap} is the
     * thing every system reads; nothing consults this afterwards.
     *
     * <p>The arena boundary is still handled by clamping a position, never by a pairwise test.
     *
     * @param cols tiles across
     * @param rows tiles down
     * @param tileSize width of one tile, in world units
     * @param regions how many areas the ground is partitioned into. Regions are what the renderer
     *     tints apart so a level reads as places rather than as one flat sheet, and later what decides
     *     which terrain and which contents belong where.
     */
    @With
    public record MapConfig(int cols, int rows, float tileSize, int regions) {
        public MapConfig {
            Require.atLeastOne("map cols", cols);
            Require.atLeastOne("map rows", rows);
            Require.positive("map tileSize", tileSize);
            Require.atLeastOne("map regions", regions);
        }

        /** The playable width, in world units. */
        public float worldWidth() {
            return cols * tileSize;
        }

        /** The playable height, in world units. */
        public float worldHeight() {
            return rows * tileSize;
        }
    }

    /**
     * The player-controlled unit.
     *
     * @param radius its circular body, in world units
     * @param speed how fast it moves, in world units per second
     * @param health damage it absorbs before dying
     * @param bleedOutSeconds how long he lies on the ground shot off his feet before he is gone, in
     *     seconds. <strong>His own, and deliberately not the squad's</strong>: what a player actually
     *     sits out is this plus the throes plus the respawn, and three windows sized for a trooper
     *     added up to most of a fight. Shortening the shared one instead would have shortened every
     *     rescue on the field, which is a different decision about a different mechanic
     * @param deathThroesSeconds how long its body stays on the field after a killing blow, before it
     *     is removed and the respawn countdown starts. The window the dying animation will play in.
     * @param respawnDelaySeconds how long after the body is removed before it comes back
     * @param respawnMargin how far in from the left edge of the arena it comes back, in world units
     * @param respawnInvulnerableSeconds protection on arrival, so it does not die again immediately
     * @param leashRadius how far a commander may walk from the middle of the commanders, in world
     *     units. Pillar 4 says there is one fight and you can always see it, and one shared view
     *     cannot frame players free to walk apart for ever. <strong>A distance, deliberately not the
     *     camera's:</strong> zoom is a rendering setting and must never become extra rope, so this is
     *     sized against the near-zoom view by hand rather than read from it. Zero is no leash, which
     *     is what a single commander effectively has anyway
     */
    @With
    public record CommanderConfig(
            float radius,
            float speed,
            float health,
            float bleedOutSeconds,
            float deathThroesSeconds,
            float respawnDelaySeconds,
            float respawnMargin,
            float respawnInvulnerableSeconds,
            float leashRadius) {
        public CommanderConfig {
            Require.positive("commander radius", radius);
            Require.positive("commander speed", speed);
            Require.positive("commander health", health);
            Require.positive("commander bleedOutSeconds", bleedOutSeconds);
            Require.notNegative("commander deathThroesSeconds", deathThroesSeconds);
            Require.notNegative("commander respawnDelaySeconds", respawnDelaySeconds);
            Require.notNegative("commander respawnMargin", respawnMargin);
            Require.notNegative("commander respawnInvulnerableSeconds", respawnInvulnerableSeconds);
            Require.notNegative("commander leashRadius", leashRadius);
        }
    }

    /**
     * One AI-controlled member of the squad, as numbers.
     *
     * <p>Separate from {@link UnitDef} rather than a strain in it, for the reasons on the
     * {@code Trooper} component: a trooper has no evolution, no leap and no contact damage, and would
     * carry all three as dead fields.
     *
     * @param radius circular body, in world units
     * @param speed world units per second at its ordinary pace. Below the commander's, so a squad
     *     following him strings out behind rather than crowding him
     * @param squadSize how many spawn with him. <strong>Scenario-facing</strong>: a scenario varies it
     *     through {@code withTrooper(...)} rather than by editing {@code game.properties}
     * @param squadGrowth how many men beyond the ones he starts with the field will hold. The squad's
     *     maximum is {@code squadSize + squadGrowth}, and reinforcements stop there. Expressed as
     *     headroom rather than as a ceiling so that a scenario raising {@code squadSize} cannot
     *     accidentally configure a maximum below its own starting squad
     * @param reinforcementRadius how far from the commander a rescued trooper is put down, in world
     *     units. Close enough that the men you won are in the fight you won them in
     * @param health damage one absorbs before dying
     * @param deathThroesSeconds how long its body stays on the field after a killing blow
     * @param weapon the gun it carries
     * @param avoidance how far ahead it looks for things in its way, and how hard it leans off them
     * @param urgency how much heading for the objective beats looking around, in {@code [0, 1]}. One
     *     is a beeline; zero is a unit that weaves so hard it barely advances. <strong>Not a
     *     speed</strong> - "forward" is what changes speed, and one dial doing both would make the
     *     order invisible. This is the number that sets the game's pace
     * @param scanSpread how far off the route a full lean carries it, as a fraction of its heading
     * @param scanMinSeconds shortest a lean lasts before it sweeps back the other way
     * @param scanMaxSeconds longest. Drawn between the two from the level seed so a squad does not
     *     sweep in lockstep, which reads as a chorus line rather than as men looking around
     * @param arriveRadius how close counts as having got there, in world units
     * @param perception what it can notice: how far, how wide an arc, and whether rock hides things
     *     from it. A trooper is the unit G0015 exists for - it declares all three
     * @param orderRadius how far the commander's voice carries, in world units. This is the whole of
     *     who hears an order (G0017) - wide enough that a squad walking with him all hear it, short
     *     enough that a stray one has to be gone and fetched
     * @param forwardDistance how much ground a "forward" buys, in world units. A distance rather
     *     than a countdown, so a squad held up by a fight still gets its full push afterwards
     * @param forwardSpeedFactor how much faster it walks while that lasts
     * @param followDistance how close a following trooper tries to stay to the commander
     * @param patchRadius how far from the post a trooper roams on a level that names one, in world
     *     units. The squad's own patch: with nothing in front of them they stay around the thing they
     *     were told to hold instead of wandering off across the level, in world
     *     units. Loose on purpose: a squad glued to him is a squad he cannot see past
     * @param approach how wide of where it is going a trooper walks, so a squad crosses the ground
     *     abreast rather than in single file - the pack's own spread (G0033), used for walking to a
     *     point and for keeping up with the commander (G0041). Off walks straight at the place itself
     * @param followAheadSeconds how far ahead of the commander a follower aims, in seconds of his own
     *     walking. A squad that walks at where he will be comes alongside him when he turns, where one
     *     that walks at where he is trails behind him. Zero aims at the man himself
     * @param holdSeconds how long a man holds a point the squad has taken before he follows a
     *     commander who has gone on ahead of him (G0040). He never goes on ahead of one
     * @param holdJitterSeconds how much each man's hold differs from that, either way, drawn from the
     *     squad's own stream when he starts holding - so a squad peels off a point rather than leaving it
     *     in step
     * @param rallyRadius how near a point a commander has to be to count as being at it, in world
     *     units: at the next one, the men holding this one set off at once; at this one, they stay
     * @param blockSeconds how long a walking man may make less than {@code blockDistance} of ground
     *     before he counts as stuck and takes a detour. Zero never counts anybody as stuck
     * @param blockDistance how much ground in that time still counts as getting somewhere, in world
     *     units. Well under what a man slowed by a thicket or a beam covers, so slow is not stuck
     * @param detourSeconds how long a stuck man backs off to one side before he tries his way again.
     *     The side is his own and alternates, so a man who backed into rock tries the other side next
     * @param alertSeconds how long a man who has been hit looks all round for what hit him, rather than
     *     only inside his arc, and turns towards where it came from. Zero leaves him facing the way he was
     * @param threatBonus how much nearer, in world units, something coming for somebody counts as being
     *     than something that is not - so a man shoots the grunt walking at him before the nest behind it
     * @param sharedTargetPenalty how much further, in world units, something counts as being for every
     *     man already shooting at it this tick - so a squad spreads its fire instead of all emptying into
     *     the nearest thing. Zero is every man for the nearest
     */
    @With
    public record TrooperConfig(
            float radius,
            float speed,
            int squadSize,
            int squadGrowth,
            float reinforcementRadius,
            float health,
            float deathThroesSeconds,
            WeaponKind weapon,
            AvoidanceDef avoidance,
            float urgency,
            float scanSpread,
            float scanMinSeconds,
            float scanMaxSeconds,
            float arriveRadius,
            PerceptionDef perception,
            float orderRadius,
            float forwardDistance,
            float forwardSpeedFactor,
            float followDistance,
            float patchRadius,
            ApproachDef approach,
            float followAheadSeconds,
            float holdSeconds,
            float holdJitterSeconds,
            float rallyRadius,
            float blockSeconds,
            float blockDistance,
            float detourSeconds,
            float alertSeconds,
            float threatBonus,
            float sharedTargetPenalty) {
        public TrooperConfig {
            Require.positive("trooper radius", radius);
            Require.positive("trooper speed", speed);
            Require.notNegative("trooper squadSize", squadSize);
            Require.notNegative("trooper squadGrowth", squadGrowth);
            Require.positive("trooper reinforcementRadius", reinforcementRadius);
            Require.positive("trooper health", health);
            Require.notNegative("trooper deathThroesSeconds", deathThroesSeconds);
            Require.notNegative("trooper scanSpread", scanSpread);
            Require.positive("trooper scanMinSeconds", scanMinSeconds);
            Require.positive("trooper arriveRadius", arriveRadius);
            if (urgency < 0f || urgency > 1f) {
                throw new IllegalArgumentException("trooper urgency must be in [0, 1], was " + urgency);
            }
            if (scanMaxSeconds < scanMinSeconds) {
                throw new IllegalArgumentException("trooper scanMaxSeconds must not be below scanMinSeconds");
            }
            Require.positive("trooper orderRadius", orderRadius);
            Require.notNegative("trooper forwardDistance", forwardDistance);
            Require.positive("trooper forwardSpeedFactor", forwardSpeedFactor);
            Require.positive("trooper followDistance", followDistance);
            Require.positive("trooper patchRadius", patchRadius);
            Require.notNegative("trooper followAheadSeconds", followAheadSeconds);
            Require.notNegative("trooper holdSeconds", holdSeconds);
            Require.notNegative("trooper holdJitterSeconds", holdJitterSeconds);
            Require.positive("trooper rallyRadius", rallyRadius);
            Require.notNegative("trooper blockSeconds", blockSeconds);
            Require.notNegative("trooper blockDistance", blockDistance);
            Require.notNegative("trooper detourSeconds", detourSeconds);
            Require.notNegative("trooper alertSeconds", alertSeconds);
            Require.notNegative("trooper threatBonus", threatBonus);
            Require.notNegative("trooper sharedTargetPenalty", sharedTargetPenalty);
            if (holdJitterSeconds > holdSeconds) {
                throw new IllegalArgumentException("trooper holdJitterSeconds must not be above holdSeconds");
            }
        }
    }

    /**
     * Where a level puts the player's side to start with, as a rectangle of tiles on the left of the
     * map.
     *
     * <p><strong>A stand-in for something a map will one day carry itself.</strong> A spawn area
     * belongs to a level, and levels are still hand-laid; naming it in config keeps every scenario
     * starting in the same recognisable place until generated fragments can say where their entrances
     * are. A scenario that wants a different one overrides it, exactly as it overrides anything else.
     *
     * <p>It is tiles, and units are jittered inside them (G0009). Nothing is placed at a tile centre
     * and a squad is never lined up on a grid.
     *
     * @param col leftmost tile column of the area
     * @param width how many columns across
     * @param height how many rows tall, centred vertically on the map
     */
    @With
    public record SpawnConfig(int col, int width, int height) {
        public SpawnConfig {
            Require.notNegative("spawn col", col);
            Require.atLeastOne("spawn width", width);
            Require.atLeastOne("spawn height", height);
        }

        /** The spawn area on this map: the configured columns, centred on the map's own height. */
        public TileRect on(TileMap map) {
            int rows = Math.min(height, map.rows());
            int row = Math.max(0, (map.rows() - rows) / 2);
            return new TileRect(col, row, Math.min(width, map.cols()), rows);
        }
    }

    /**
     * The commander's hand strike: a short, fast sweep that answers something already committed to
     * a leap. Every field here is what will differ between melee weapons, which is why there is a
     * record rather than a handful of constants.
     *
     * @param cooldownSeconds minimum time between swings
     * @param windupSeconds delay between the input and the sweep appearing. Zero for a bare hand;
     *     the field exists because a heavier weapon is exactly this number being non-zero.
     * @param activeSeconds how long the sweep is in the world and able to connect
     * @param reach distance from the commander's centre to the centre of the sweep, in world units.
     *     Short on purpose: the sweep must not land until whatever it is answering is nearly touching,
     *     or a lethal hit kills at arm's length and the exchange reads as a swat instead of a clinch.
     *     The window the player gets is {@code activeSeconds}, not this - the sweep lingers and the
     *     leap flies into it - so shortening this moves when he must press, not how long he has.
     * @param radius circular body of the sweep itself
     * @param damage what a connecting sweep takes off what it hits
     * @param arcDegrees how far around the aim direction the sweep travels; 180 is a half-circle
     * @param lockSeconds how long a connecting sweep holds both the commander and its target still
     * @param locksCommittedOnly whether that lock is spent only when the target had already committed
     *     to attacking. On, a kill on something mid-leap is a clinch and a kill on something merely
     *     walking is a swat that costs him nothing but the cooldown - which is what stops the harmless
     *     target being the expensive one
     * @param clinchCarrySpeed how fast a body it kills finishes its lunge into the commander, in
     *     world units per second. It must carry further than the sweep out-reaches a touch - about a
     *     unit at the shipped numbers - or the two finish the clinch a stride apart
     * @param clinchCarryDecay how fast that carry bleeds off, in world units per second per second
     * @param knockbackSpeed how hard a surviving target is thrown, in world units per second
     * @param knockbackDecay how fast that throw bleeds off, in world units per second per second
     */
    @With
    public record MeleeConfig(
            float cooldownSeconds,
            float windupSeconds,
            float activeSeconds,
            float reach,
            float radius,
            float damage,
            float arcDegrees,
            float lockSeconds,
            boolean locksCommittedOnly,
            float clinchCarrySpeed,
            float clinchCarryDecay,
            float knockbackSpeed,
            float knockbackDecay) {
        public MeleeConfig {
            Require.notNegative("melee cooldownSeconds", cooldownSeconds);
            Require.notNegative("melee windupSeconds", windupSeconds);
            Require.positive("melee activeSeconds", activeSeconds);
            Require.notNegative("melee reach", reach);
            Require.positive("melee radius", radius);
            Require.notNegative("melee damage", damage);
            Require.notNegative("melee arcDegrees", arcDegrees);
            Require.notNegative("melee lockSeconds", lockSeconds);
            Require.notNegative("melee clinchCarrySpeed", clinchCarrySpeed);
            Require.notNegative("melee clinchCarryDecay", clinchCarryDecay);
            Require.notNegative("melee knockbackSpeed", knockbackSpeed);
            Require.notNegative("melee knockbackDecay", knockbackDecay);
        }

        /**
         * How far a body carried into the commander travels before it stops, in world units.
         *
         * <p>A carry that never decays never stops, which is reported as an unreachable distance
         * rather than as a division by zero.
         */
        public float carryDistance() {
            return clinchCarryDecay <= 0f
                    ? Float.MAX_VALUE
                    : clinchCarrySpeed * clinchCarrySpeed / (2f * clinchCarryDecay);
        }

        /**
         * The stride a sweep can leave between the commander and something it killed at full stretch.
         *
         * <p>The sweep sits {@code reach} out and is {@code radius} across, so the far edge of what it
         * can touch is that much further out than the commander's own body - and the target's radius
         * cancels, because it counts on both sides. Anything the clinch is meant to close has to close
         * at least this.
         */
        public float longestReachableGap(float bodyRadius) {
            float gap = reach + radius - bodyRadius;
            return gap > 0f ? gap : 0f;
        }
    }

    /**
     * What an explosion does, wherever it came from.
     *
     * <p>The radius is deliberately <strong>not</strong> here. A grenade and a barrel make blasts of
     * different sizes, so each of them names its own; what is shared is what a blast <em>is</em> -
     * fatal, and briefly visible - and that is one rule for every explosion in the game.
     *
     * @param damage what a blast takes off whatever it catches. Far above any unit's health on
     *     purpose: one circle and death inside it is the rule the player can read, and a number that
     *     left the heaviest strain standing would quietly turn it into a damage curve
     * @param durationSeconds how long the flash is drawn for. Fast, and long enough to see
     * @param shakeStrength how hard the ground moves at the middle of one, in world units. Zero for
     *     an explosion nothing feels. What a <em>view</em> does with that is the client's decision;
     *     this is the simulation saying how violent the event was, not how the camera should behave
     * @param shakeRadiusFactor how far that is felt, as a multiple of the blast's own radius. Above
     *     one on purpose - you hear an explosion you were not close enough to be killed by, and that
     *     is most of what tells a player one has gone off somewhere they were not looking
     */
    @With
    public record BlastConfig(float damage, float durationSeconds, float shakeStrength, float shakeRadiusFactor) {
        public BlastConfig {
            Require.positive("blast damage", damage);
            Require.positive("blast durationSeconds", durationSeconds);
            Require.notNegative("blast shakeStrength", shakeStrength);
            Require.notNegative("blast shakeRadiusFactor", shakeRadiusFactor);
        }
    }

    /**
     * What a beam held on a body does to it, and what is left when it lets go.
     *
     * <p>One block for every unit in the game rather than a number per strain, because what varies
     * between a grunt and a brute is already the body: the same fraction of the same rise makes a
     * brute's corpse a bigger thing than a grunt's with nothing said about either. A per-strain
     * radius is one field on {@code UnitDef} the day somebody wants one.
     *
     * @param referenceRadius the body a gun's {@code bloatPerShot} is quoted against, in world
     *     units. <strong>A bigger thing takes longer to fill</strong>, in proportion to its radius,
     *     so a brute is a decision and a grunt is an afterthought - and nothing had to be said per
     *     strain, because a strain's size is already what differs. Set it to the ordinary chasing
     *     strain's radius and a gun's number then means what it says
     * @param livingFactor how much of a shot's worth a thing still on its feet banks, as a
     *     fraction. <strong>This is where "partly charged beforehand and finished later" lives.</strong>
     *     One would mean a creature burnt to death arrives dead and nearly full, with no decision
     *     left to make about the body; too little and the kill counts for nothing and every corpse
     *     starts from scratch. It wants to be a bit over half
     * @param settlePerSecond how much of the charge falls away per second <strong>with nothing on
     *     it</strong>. A thing being charged does not settle at all - draining it while the beam is
     *     on it makes the gun fight the clock rather than the body. Slow enough that going and
     *     dealing with something else and coming back is a real option, which is the whole of what a
     *     partial charge is for
     * @param blastFactor how wide the circle is when one bursts, as a multiple of the body's own
     *     radius - so a brute leaves a bigger bang than a grunt, for the same reason it took longer
     *     to fill. Four is twice the body's <em>diameter</em>. It leaves nothing burning: a corpse
     *     bomb that fenced off the ground it went off on is a tool the player cannot use twice in
     *     the same place
     * @param holdSeconds the least a charged body has left to lie there, in seconds, refreshed for
     *     as long as there is any charge on it. What makes a half-filled corpse a thing you can come
     *     back to rather than a thing you had to finish in one go
     */
    @With
    public record BloatConfig(
            float referenceRadius, float livingFactor, float settlePerSecond, float blastFactor, float holdSeconds) {

        public BloatConfig {
            Require.positive("bloat referenceRadius", referenceRadius);
            Require.notNegative("bloat settlePerSecond", settlePerSecond);
            Require.notNegative("bloat blastFactor", blastFactor);
            Require.notNegative("bloat holdSeconds", holdSeconds);
            if (livingFactor < 0f || livingFactor > 1f) {
                throw new IllegalArgumentException("bloat livingFactor must be in [0, 1], was " + livingFactor);
            }
        }

        /** Whether a body pumped full is worth anything at all. Zero is a game without the mechanic. */
        public boolean bursts() {
            return blastFactor > 0f;
        }

        /**
         * What one shot is worth against a body of this size.
         *
         * <p>In proportion to the radius rather than to the area, deliberately: squaring it would
         * make a brute four times the work of a grunt rather than half again, and the thing the
         * player has to be able to feel is that a bigger creature is <em>a bit</em> more expensive,
         * not that it is a different mechanic.
         *
         * <p>A thing still on its feet banks only a fraction of it, which is what leaves a body
         * <em>partly</em> charged at the moment it dies and a decision still to make about it.
         */
        public float perShotAgainst(float bodyRadius, float perShot, boolean alreadyDown) {
            float forTheBody = bodyRadius <= 0f ? perShot : perShot * referenceRadius / bodyRadius;
            return alreadyDown ? forTheBody : forTheBody * livingFactor;
        }

        /** How wide the circle a body of this size leaves when it bursts. */
        public float blastFor(float bodyRadius) {
            return bodyRadius * blastFactor;
        }
    }

    /**
     * What an explosion leaves burning where it was.
     *
     * <p><strong>One block for every fire in the game</strong>, rather than one per thing that
     * explodes. What makes a barrel's fire worse than a grenade's is that the barrel's blast was
     * wider, and everything here is a fraction of that blast - so a new explosive gets fire without
     * naming a single number, and re-tuning how a fire behaves cannot leave one explosive behind.
     *
     * <p>It produces a {@link SlickDef}, which is the whole of G0035: fire is ruined ground with a
     * different declaration, not a second mechanic. The side is not here either - a blast is on
     * nobody's side, and the spatter takes the side of whatever died, so fire is neutral without
     * anything saying so.
     *
     * @param blobs how many circles one explosion leaves burning
     * @param radiusFactor how wide each of them gets, as a fraction of the blast's own radius
     * @param spreadFactor how far from the middle they fall, as a fraction of the blast's radius.
     *     Well under one, so the fire sits inside the ground the explosion already had rather than
     *     reaching past it - an explosion whose fire is wider than its own circle is a blast that
     *     lingers, which G0018 refused
     * @param minSeconds the shortest a circle burns for
     * @param maxSeconds the longest. Drawn per circle between the two, so a fire goes out in patches
     * @param damage what walking into one takes off. Wounding, like gunfire and like a blast: it
     *     leaves a casualty somebody can come and get
     * @param victimInvulnerableSeconds how long a man it has put down is protected from the rest of it
     * @param growFraction how much of its life a circle spends swelling
     * @param fadeFraction how much of it it spends collapsing again
     */
    @With
    public record FireConfig(
            int blobs,
            float radiusFactor,
            float spreadFactor,
            float minSeconds,
            float maxSeconds,
            float damage,
            float victimInvulnerableSeconds,
            float growFraction,
            float fadeFraction) {

        public FireConfig {
            Require.notNegative("fire blobs", blobs);
            Require.notNegative("fire radiusFactor", radiusFactor);
            Require.notNegative("fire spreadFactor", spreadFactor);
            Require.notNegative("fire minSeconds", minSeconds);
            Require.notNegative("fire damage", damage);
            Require.notNegative("fire victimInvulnerableSeconds", victimInvulnerableSeconds);
            Require.notNegative("fire growFraction", growFraction);
            Require.notNegative("fire fadeFraction", fadeFraction);
            if (maxSeconds < minSeconds) {
                throw new IllegalArgumentException("fire cannot burn for less than its own minimum");
            }
        }

        /** Whether an explosion leaves anything burning at all. Zero blobs is a game with no fire in it. */
        public boolean leavesAnything() {
            return blobs > 0 && radiusFactor > 0f && minSeconds > 0f;
        }

        /** What a blast of this radius leaves burning, as the ruined ground it is (G0035). */
        public SlickDef forBlast(float blastRadius) {
            if (!leavesAnything() || blastRadius <= 0f) {
                return SlickDef.off();
            }
            return new SlickDef(
                    blobs,
                    blastRadius * radiusFactor,
                    blastRadius * spreadFactor,
                    minSeconds,
                    maxSeconds,
                    damage,
                    victimInvulnerableSeconds,
                    growFraction,
                    fadeFraction,
                    Look.FIRE);
        }
    }

    /**
     * The neutral creature that wanders the field, is eaten by whatever catches it, and sidesteps a
     * shot it sees coming.
     *
     * <p>What it <em>is</em> - how big, what colour, what each side gets for taking it - is a
     * {@code PickupDef} like every other pickup's, because taking one works exactly the same way. What
     * it <em>does</em> is here, because nothing else lying on the field does anything.
     *
     * @param speed how fast it moves under its own power, in world units per second
     * @param health what it absorbs before dying. One shot is meant to be enough; the number exists so
     *     that is a decision rather than an accident of the damage code
     * @param deathThroesSeconds how long the body lies there. A body cannot be eaten, so this is how
     *     long a denied meal stays visible as one
     * @param wander how it passes the time
     * @param dodge how it answers a shot
     */
    @With
    public record RabbitConfig(float speed, float health, float deathThroesSeconds, WanderDef wander, DodgeDef dodge) {
        public RabbitConfig {
            Require.positive("rabbit speed", speed);
            Require.positive("rabbit health", health);
            Require.notNegative("rabbit deathThroesSeconds", deathThroesSeconds);
        }
    }

    /**
     * What the commander throws.
     *
     * <p><strong>{@code range} is a distance and not a countdown</strong>, and that is the mechanic
     * rather than an implementation note: a bounce changes a grenade's direction and never its speed,
     * so every throw covers the same ground however many walls it comes off. It is spent along the aim
     * <em>direction</em>, so pulling the view back never reaches further.
     *
     * <p>Nothing here counts grenades. Unlimited ones are alpha-phase scaffolding and a count would be
     * one more number in this record, so leaving it out settles nothing about how ammunition works.
     *
     * @param cooldownSeconds minimum time between throws. The whole of what stops a stream of them
     * @param speed how fast it travels, in world units per second
     * @param range how far it travels in total, in world units, whatever it bounces off on the way.
     *     Comfortably longer than the blast it leaves, or every throw would be suicide
     * @param radius the grenade's own circular body. Small: it has to come off things, not shove them
     * @param blastRadius how wide the blast it leaves is, in world units
     */
    @With
    public record GrenadeConfig(float cooldownSeconds, float speed, float range, float radius, float blastRadius) {
        public GrenadeConfig {
            Require.notNegative("grenade cooldownSeconds", cooldownSeconds);
            Require.positive("grenade speed", speed);
            Require.positive("grenade range", range);
            Require.positive("grenade radius", radius);
            Require.positive("grenade blastRadius", blastRadius);
        }
    }

    /**
     * The explosive barrel: the one pickup that is worth nothing to anybody.
     *
     * <p>How big it is stays in its {@link PickupDef} with every other pickup's radius. What is here
     * is the pair of numbers nothing else on the field has - how little it takes to set one off, and
     * how much ground it takes with it.
     *
     * @param health what it absorbs before it goes off. One of anything, on purpose: a barrel is a
     *     trigger standing in the open, not something to grind down
     * @param blastRadius how wide the blast it leaves is, in world units. The number that decides
     *     whether a barrel is scenery or a reason to change where you are standing
     * @param bittenBlastRadius how wide it goes off instead when something bit into it rather than
     *     shooting it, in world units. Smaller, and that asymmetry is the point: a creature that set
     *     one off with its mouth was standing on top of it and takes most of the blast itself, so a
     *     barrel the aliens walk into is a gift rather than a disaster. Shooting one from across the
     *     field is still the full circle, which is what keeps it worth aiming at
     */
    @With
    public record BarrelConfig(float health, float blastRadius, float bittenBlastRadius) {
        public BarrelConfig {
            Require.positive("barrel bittenBlastRadius", bittenBlastRadius);
            Require.positive("barrel health", health);
            Require.positive("barrel blastRadius", blastRadius);
        }
    }

    /**
     * The explosive that goes off when the other side walks into it.
     *
     * <p>The barrel's opposite number. A barrel is a thing standing in the open that the player
     * shoots at the right moment; a mine is a thing lying in the open that decides for itself, so
     * what it is worth is entirely where it was put. Both explode by dying, through the one reaction
     * (G0018), so there is nothing about a blast here.
     *
     * @param radius how big the thing itself is, in world units. Small: it has to read as a dot on
     *     the ground rather than as something standing on it, and it has to be walkable-into
     * @param triggerRadius how close something on the other side has to come. Barely past the thing
     *     itself on purpose - a mine that reaches out is a mine that goes off in the middle of a
     *     pack, which is a grenade the player did not have to aim. A creature has to nearly stand on
     *     one
     * @param fuseSeconds how long it ticks before it goes off. The whole of the mechanic: long
     *     enough that the player sees it start and something nearby might still walk clear, short
     *     enough that whatever set it off is usually still standing there
     * @param health what it absorbs before it goes off early. One of anything, like a barrel: it is
     *     a trigger lying in the open, so shooting one is a way of choosing the moment yourself
     * @param blastRadius how wide it goes off, in world units. Under a barrel's, deliberately - a
     *     mine is something a level lays in numbers, and one worth as much as a barrel would make
     *     the ground it is on unusable rather than dangerous
     * @param flashFromSeconds how long one flash of the fuse lasts when it has just started
     * @param flashToSeconds how long one lasts at the end. Shorter, so the flashing quickens - that
     *     acceleration is the tell, and the number it is counting is never shown (pillar 1)
     */
    @With
    public record MineConfig(
            float radius,
            float triggerRadius,
            float fuseSeconds,
            float health,
            float blastRadius,
            float flashFromSeconds,
            float flashToSeconds) {

        public MineConfig {
            Require.positive("mine radius", radius);
            Require.positive("mine triggerRadius", triggerRadius);
            Require.positive("mine fuseSeconds", fuseSeconds);
            Require.positive("mine health", health);
            Require.positive("mine blastRadius", blastRadius);
            Require.notNegative("mine flashFromSeconds", flashFromSeconds);
            Require.notNegative("mine flashToSeconds", flashToSeconds);
            if (flashToSeconds > flashFromSeconds) {
                throw new IllegalArgumentException("a fuse that slows down as it runs out is not a warning");
            }
        }
    }

    /**
     * What happens to a man who is shot off his feet.
     *
     * @param bleedOutSeconds how long he lies there before he is gone, in seconds. This is the whole
     *     of what makes a casualty a decision rather than an event: it is the window the player has
     *     to cross open ground, and the number that decides whether going to get him is brave or
     *     merely expensive
     * @param stabiliseSeconds how long somebody has to stand over him, unbroken, to put him back on
     *     his feet. Seconds standing still in the open is what the rescue actually costs
     * @param reviveHealthFraction how much of his health he gets back, in {@code (0, 1]}. Well under
     *     a full bar on purpose: a man picked up in the middle of a firefight goes down again to the
     *     next thing that touches him, so getting him up is not the same as getting him out
     * @param reviveInvulnerableSeconds how long he is protected as he stands up. Without it the
     *     stray round that was already in the air undoes the whole rescue, which reads as the game
     *     cheating rather than as bad luck
     * @param draggedBleedFactor how fast his clock runs while he is being hauled, as a multiple of
     *     normal. Zero stops it, which is the shipped answer: the ground you cross carrying him is
     *     free, and what you pay is at the far end. Whether that is the right trade is a question
     *     for play, which is exactly why it is a number here rather than a rule in a system
     * @param hauledStabiliseFactor what having been hauled multiplies the work by. Well above one,
     *     so taking him somewhere quiet is a real decision against patching him up where he fell
     */
    @With
    public record CasualtyConfig(
            float bleedOutSeconds,
            float stabiliseSeconds,
            float reviveHealthFraction,
            float reviveInvulnerableSeconds,
            float draggedBleedFactor,
            float hauledStabiliseFactor) {

        public CasualtyConfig {
            Require.positive("casualty bleedOutSeconds", bleedOutSeconds);
            Require.positive("casualty stabiliseSeconds", stabiliseSeconds);
            Require.positive("casualty reviveHealthFraction", reviveHealthFraction);
            Require.notNegative("casualty reviveInvulnerableSeconds", reviveInvulnerableSeconds);
            Require.notNegative("casualty draggedBleedFactor", draggedBleedFactor);
            Require.positive("casualty hauledStabiliseFactor", hauledStabiliseFactor);
            if (reviveHealthFraction > 1f) {
                throw new IllegalArgumentException(
                        "casualty reviveHealthFraction must be at most 1, was " + reviveHealthFraction);
            }
        }
    }

    /**
     * What the commander can do with his hands, and how close he has to be to do it.
     *
     * @param reach how close something must be, surface to surface, to be within arm's length, in
     *     world units. One number for every action there is or will be: a player who learnt that
     *     reviving needs him almost touching should not have to learn a second distance for hauling
     * @param dragSpeedFactor how much of his walking pace survives while he hauls, in {@code (0, 1]}
     * @param dragLateralFactor how much of a sideways step survives while he hauls, in {@code [0, 1]}.
     *     Small but not zero: backing out of a fight in a dead straight line is not a retreat, it is
     *     a rail
     * @param dragGap clearance between the two bodies while he hauls, in world units
     */
    @With
    public record ActionConfig(float reach, float dragSpeedFactor, float dragLateralFactor, float dragGap) {

        public ActionConfig {
            Require.positive("action reach", reach);
            Require.positive("action dragSpeedFactor", dragSpeedFactor);
            Require.notNegative("action dragLateralFactor", dragLateralFactor);
            Require.notNegative("action dragGap", dragGap);
        }
    }

    /**
     * The gun on a mount: how big it is, how much it takes to bring down, and how fast it comes round.
     *
     * <p>Health well above a man's, because a turret is somewhere a level tells you to stand and
     * losing one should be a decision the other side had to work for. The blast is small on purpose -
     * it is a thing going up under whoever is on it, not a barrel.
     *
     * @param radius circular body, in world units, though it is drawn square - what it looks like is
     *     the client's and what it collides as is data (ADR 0007)
     * @param health damage it absorbs before it is destroyed
     * @param blastRadius how wide a circle it leaves when it goes, in world units
     * @param turnRate how fast it comes round with the trigger off, as the fraction of the angle left
     *     it covers per second
     * @param firingTurnFactor what is left of that rate while it is firing, in {@code (0, 1]}. This is
     *     the number that makes holding the trigger and tracking a target two different decisions
     * @param range how far a crew of its own judgement will shoot from it, in world units. Well past
     *     what a man can see for himself, which is most of what a turret is worth to a squad
     * @param crewRadius how far a trooper will divert to get on a free one, in world units
     * @param quietSeconds how long with nothing in range before a trooper gets off again
     * @param weapon which gun is on it
     */
    @With
    public record TurretConfig(
            float radius,
            float health,
            float blastRadius,
            float turnRate,
            float firingTurnFactor,
            float range,
            float crewRadius,
            float quietSeconds,
            WeaponKind weapon) {

        public TurretConfig {
            Require.positive("turret radius", radius);
            Require.positive("turret health", health);
            Require.notNegative("turret blastRadius", blastRadius);
            Require.notNegative("turret turnRate", turnRate);
            if (firingTurnFactor <= 0f || firingTurnFactor > 1f) {
                throw new IllegalArgumentException(
                        "turret firingTurnFactor must be in (0, 1], was " + firingTurnFactor);
            }
            Require.positive("turret range", range);
            Require.notNegative("turret crewRadius", crewRadius);
            Require.notNegative("turret quietSeconds", quietSeconds);
        }
    }

    /**
     * The static circles that block movement and projectiles.
     *
     * @param radius circular body, in world units
     * @param count how many the scenario spawns
     */
    @With
    public record ObstacleConfig(float radius, int count) {
        public ObstacleConfig {
            Require.positive("obstacle radius", radius);
            Require.notNegative("obstacle count", count);
        }
    }
}
