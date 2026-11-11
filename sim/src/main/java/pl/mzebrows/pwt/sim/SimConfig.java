package pl.mzebrows.pwt.sim;

import java.util.List;
import java.util.function.UnaryOperator;
import lombok.With;
import pl.mzebrows.pwt.map.Randomness;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.def.AiChance;
import pl.mzebrows.pwt.sim.def.ApproachDef;
import pl.mzebrows.pwt.sim.def.AvoidanceDef;
import pl.mzebrows.pwt.sim.def.BiomeKind;
import pl.mzebrows.pwt.sim.def.Chances;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.DodgeDef;
import pl.mzebrows.pwt.sim.def.FinaleKind;
import pl.mzebrows.pwt.sim.def.GroundDef;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.def.PerceptionDef;
import pl.mzebrows.pwt.sim.def.PickupDef;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.Require;
import pl.mzebrows.pwt.sim.def.ShakeDef;
import pl.mzebrows.pwt.sim.def.SlickDef;
import pl.mzebrows.pwt.sim.def.TrooperGrenadeDef;
import pl.mzebrows.pwt.sim.def.UnitDef;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WanderDef;
import pl.mzebrows.pwt.sim.def.WeaponDef;
import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.sim.def.Whole;
import pl.mzebrows.pwt.sim.system.upgrade.AdaptationDef;
import pl.mzebrows.pwt.sim.system.upgrade.DraftDef;
import pl.mzebrows.pwt.sim.system.upgrade.Stat;
import pl.mzebrows.pwt.sim.system.upgrade.StatDef;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeDef;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;

/**
 * Tunables the simulation reads at construction time: exactly {@code assets/sim/*.properties}, read in
 * name order and bound as one text by {@link ConfigBinder} from each component's name. The simulation never touches the filesystem - what
 * ships is compiled in ({@link #defaults()}), and anything else arrives as text ({@link #FILE}).
 * <strong>A new tunable is a component here and a line in the file its prefix lives in</strong>, and
 * nothing else. Why a number is set where it is lives beside it in the file; the docs here say only what
 * it means.
 *
 * <p>Nested records so a system is handed only the group it needs. {@code @With} on each makes
 * varying one number a single call: {@code config.withObstacle(config.obstacle().withCount(0))}.
 * Kinds with many members - strains, pickups, guns, ground - are {@link Defs} registries instead.
 *
 * @param seed seed for the simulation's {@link Rng}
 * @param tickSeconds length of one fixed simulation step
 * @param maxTicksPerFrame ceiling on catch-up steps, so a stalled frame cannot spiral
 * @param map the grid a level is laid out on; its extent <em>is</em> the arena
 * @param commander the player-controlled unit
 * @param trooper the AI-controlled members of his squad
 * @param spawn where a level puts the player's side to start with
 * @param reserve the men a side has off the field, and how the field is filled from them (G0055)
 * @param units every strain of chasing unit, by kind
 * @param pickups every kind of thing that can be lying on the field, by kind
 * @param weapons every gun the commander can be carrying, by kind
 * @param muzzleGap how far clear of its holder's body a shot or a throw leaves, for every gun and every
 *     hand there is - one number, so nothing fires from inside itself and no shooter is special
 * @param chances how often each occasional AI behaviour happens, one number per {@code AiChance} -
 *     the index of everything the AI leaves to the dice. A strain carries its own copy, resolved
 *     against this one when the config is built, so retune a behaviour with {@link #withChance}
 *     rather than by replacing this table on its own
 * @param melee the commander's hand strike
 * @param blast what an explosion does, wherever it came from
 * @param fire what an explosion leaves burning behind it, wherever it came from
 * @param blood what an alien leaves where it dies, for as many circles as its strain and the swarm's
 *     toxic blood make it (G0076)
 * @param shock what a gun's shocks build to, and how fast that comes off again (G0059)
 * @param grenade what the commander throws
 * @param rabbit the neutral creature that wanders the field and is eaten
 * @param barrel the explosive one, which is neither eaten nor picked up
 * @param mine the explosive one that the other side sets off by walking into it
 * @param wound what a wound costs a man on his feet, and how long it protects him (G0053)
 * @param nest the holes in the ground that keep sending more out
 * @param controlPoint what it takes to hold a point, and how far taking one carries
 * @param turret the gun on a mount that a man gets on
 * @param obstacle the static circles that block movement
 * @param collision how hard the constraint solver works
 * @param draft how drafts are earned and dealt (G0049)
 * @param stats how far each number an upgrade may move can be moved
 * @param upgrades every upgrade and adaptation, by kind (ADR 0017)
 * @param adaptation when the swarm adapts (ADR 0017), both halves off at zero
 * @param swarm what every strain and nest is scaled by, read through {@link #healthOf} and
 *     {@link #nestHealth} and nowhere else
 * @param mission what a mission is built with that no other level has: its tiers and its ramp (G0063), its strip
 *     and where its biomes' contents go (G0068)
 * @param biomes every biome a mission deals from, by kind (missions ticket 03)
 * @param finales every finale a mission ends on, by kind (G0064)
 * @param door what stands in every doorway (G0069)
 * @param medkit how many medkits a level that asks for them scatters (G0053)
 * @param crate how many pickups a crate is dealt (G0056)
 * @param pop how long a take by touch is shown for (G0078)
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
        ReserveConfig reserve,
        @ConfigKey("unit") Defs<UnitKind, UnitDef> units,
        @ConfigKey("pickup") Defs<PickupKind, PickupDef> pickups,
        @ConfigKey("weapon") Defs<WeaponKind, WeaponDef> weapons,
        float muzzleGap,
        Defs<Terrain, GroundDef> ground,
        @ConfigKey("ai.chance") Chances chances,
        MeleeConfig melee,
        BlastConfig blast,
        FireConfig fire,
        BloodConfig blood,
        BloatConfig bloat,
        ShockConfig shock,
        GrenadeConfig grenade,
        RabbitConfig rabbit,
        BarrelConfig barrel,
        MineConfig mine,
        CasualtyConfig casualty,
        WoundConfig wound,
        ActionConfig action,
        NestConfig nest,
        ControlPointConfig controlPoint,
        TurretConfig turret,
        ObstacleConfig obstacle,
        CollisionConfig collision,
        @ConfigKey("upgrade.draft") DraftDef draft,
        @ConfigKey("upgrade.stat") Defs<Stat, StatDef> stats,
        @ConfigKey("upgrade") Defs<UpgradeKind, UpgradeDef> upgrades,
        AdaptationDef adaptation,
        SwarmConfig swarm,
        MissionConfig mission,
        @ConfigKey("biome") Defs<BiomeKind, BiomeDef> biomes,
        @ConfigKey("finale") Defs<FinaleKind, FinaleDef> finales,
        DoorConfig door,
        MedkitConfig medkit,
        CrateConfig crate,
        PopConfig pop) {

    /** The prefix every key in {@code assets/sim/*.properties} starts with. */
    private static final String ROOT = "sim";

    public SimConfig {
        Require.positive("tickSeconds", tickSeconds);
        Require.atLeastOne("maxTicksPerFrame", maxTicksPerFrame);
        Require.notNegative("muzzleGap", muzzleGap);
        if (melee.clinchCarrySpeed() > 0f) {
            Require.atLeast(
                    "melee carry distance",
                    melee.carryDistance(),
                    melee.longestReachableGap(commander.radius()),
                    "a clinch that stops short of the commander leaves the freeze playing across a gap");
        }
        UpgradeDef.checkCatalogue(draft, stats, upgrades, pickups);
        if (BiomeKind.values().length < mission.biomes()) {
            throw new IllegalArgumentException("a mission deals " + mission.biomes()
                    + " biomes, none twice, from a pool of " + BiomeKind.values().length);
        }
        BiomeDef.checkStock(biomes, pickups);
    }

    /** The definition of one strain, for a system or a scenario that needs its numbers. */
    public UnitDef unit(UnitKind kind) {
        return units.of(kind);
    }

    /**
     * How much health one strain arrives with: its own, scaled by the swarm's factor and its tier's, rounded
     * once. <strong>The one place a strain's health is read</strong>, so a unit spawned, a unit evolved and
     * a check on the shipped numbers all see the same number.
     */
    public int healthOf(UnitKind kind) {
        return Whole.percentOf(units.of(kind).health(), swarm.healthPercent(), swarm.tierPercent());
    }

    /** How much health a nest arrives with: its own, scaled by the swarm's factors, as a strain's is. */
    public int nestHealth() {
        return Whole.percentOf(nest.health(), swarm.healthPercent(), swarm.tierPercent());
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
     * <p>A strain carries a table already resolved against the shared one, so {@code withChances}
     * alone does not reach it; use this.
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
     * {@code assets/sim/*.properties}: what ships, compiled in by {@code :sim:generateShippedConfig}, and how
     * a player's or a host's text of them is read.
     */
    public static final ConfigFile<SimConfig> FILE = new ConfigFile<>(
            "sim/*.properties", SimConfig.class, ROOT, ShippedConfig.TEXT, ConfigBinder.Conversions.none());

    /**
     * What ships: {@code assets/sim/*.properties}, compiled in by {@code :sim:generateShippedConfig} and
     * bound once. What every test runs, and what the client falls back to - so there is one copy of every
     * number, and it is the one players play.
     */
    public static SimConfig defaults() {
        return FILE.defaults();
    }

    /**
     * The shipped files, by name, in the order their text is read: {@code assets/sim/} holds exactly
     * these. Generated from that directory with the constants, so whoever reads its own copy of the files
     * reads the same ones in the same order.
     */
    public static List<String> shippedFiles() {
        return ShippedConfig.FILES;
    }

    /**
     * The largest radius any entity can have, which sets the collision grid's cell size. Every body
     * is a circle (ADR 0007), so one number covers all of them.
     *
     * <p>It must cover every body the game can produce - evolved strains, the widest fire, a shot's
     * swept path - because a cell too small silently drops overlapping pairs. So each part is asked
     * of its registry rather than listed by hand. {@code ConstraintSolver} throws on a body above it.
     */
    public float largestRadius() {
        float largest = Math.max(commander.radius(), Math.max(obstacle.radius(), obstacle.trunkMax()));
        largest = Math.max(largest, trooper.radius());
        largest = Math.max(largest, melee.radius());
        largest = Math.max(largest, grenade.radius());
        // Fire is an ordinary body; the widest is the one the widest explosion leaves.
        largest = Math.max(largest, fire.forBlast(largestBlastRadius()).radius());
        largest = Math.max(largest, blood.radius());
        for (WeaponDef gun : weapons.values()) {
            // A shot is swept and filed at the middle of its path: its radius plus half a tick's travel.
            largest = Math.max(largest, gun.projectileRadius() + gun.projectileSpeed() * tickSeconds * 0.5f);
            // Ruined ground is an ordinary body in the pairwise path, unlike a blast.
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
     * <p>The one place that asks every explosive at once, so fire's sizing cannot fall behind a new one.
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
     * <p>One block for every nest: what differs between nests is the {@code UnitKind} that comes out,
     * named where the nest is put down.
     *
     * @param radius its circular body, in world units; solid and shootable
     * @param health what it absorbs before it is destroyed
     * @param periodSeconds how long between broods
     * @param firstBroodSeconds how long after waking the first brood comes, for every nest - soon, because a
     *     nest wakes when you arrive in its biome (G0062)
     * @param broodMin how few come out at a time
     * @param broodMax how many, dealt from the nest's own bag. At most {@code Bag.MOST} sizes
     * @param deathBroodMin how few come out when it is destroyed
     * @param deathBroodMax how many
     * @param hatchRadius how far around it a brood is put down, in world units; outside its own body
     * @param hatchKeepOff how far off every man on the player's side a hatched creature is put down, in
     *     world units, so a brood comes out of the hole rather than out of the man standing over it
     * @param wakeRadius how close somebody on the player's side and in its biome must come before it starts
     *     working, in world units. Not a detection range
     * @param rouseRadius how far its cry carries when it is attacked, in world units
     */
    @With
    public record NestConfig(
            float radius,
            int health,
            float periodSeconds,
            float firstBroodSeconds,
            int broodMin,
            int broodMax,
            int deathBroodMin,
            int deathBroodMax,
            float hatchRadius,
            float hatchKeepOff,
            float wakeRadius,
            float rouseRadius) {

        public NestConfig {
            Require.positive("nest radius", radius);
            Require.positive("nest health", health);
            Require.positive("nest periodSeconds", periodSeconds);
            Require.positive("nest firstBroodSeconds", firstBroodSeconds);
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
            Require.notNegative("nest hatchKeepOff", hatchKeepOff);
            Require.notNegative("nest wakeRadius", wakeRadius);
            Require.notNegative("nest rouseRadius", rouseRadius);
        }
    }

    /**
     * What stands in a doorway (G0069): one block for every door, since one door fits every doorway.
     *
     * @param openSeconds how long opening one takes - the action key held, at arm's length, standing still
     * @param sinkSeconds how long an opened door is seen sinking into the ground; a look only, its doorway is open
     *     from the tick it opened
     * @param leapDamage what a creature's leap lands on it, whatever the strain
     * @param health what it takes before it breaks
     * @param waitDistance how far ahead of a trooper a shut door has to be for his way to count as shut by it, in
     *     world units: where the men behind the one opening it stop and watch. Above arm's length
     * @param throwSpeed how fast a creature whose leap lands on it is thrown back off it, in world units a second
     * @param throwSeconds how long it is held while it is, slowing to a stop as the hold ends
     * @param shake how hard a leap landing on it shakes the ground - felt by the commanders near it
     */
    @With
    public record DoorConfig(
            float openSeconds,
            float sinkSeconds,
            int leapDamage,
            int health,
            float waitDistance,
            float throwSpeed,
            float throwSeconds,
            ShakeDef shake) {
        public DoorConfig {
            Require.positive("door openSeconds", openSeconds);
            Require.notNegative("door sinkSeconds", sinkSeconds);
            Require.notNegative("door leapDamage", leapDamage);
            Require.positive("door health", health);
            Require.positive("door waitDistance", waitDistance);
            Require.notNegative("door throwSpeed", throwSpeed);
            Require.positive("door throwSeconds", throwSeconds);
        }
    }

    /**
     * What it takes to hold a control point.
     *
     * @param radius how far from the middle counts as standing on it, in world units
     * @param captureSeconds how long somebody has to stay inside it
     * @param aggroRadius how far the noise of an attempt carries, in world units
     * @param reinforcementRadius how far from a point the level lays its reinforcements, in world units;
     *     outside the point's own radius
     * @param reinforcementsMin fewest crates a point is worth
     * @param reinforcementsMax most, dealt from one bag across the level's points. At most
     *     {@code Bag.MOST} values
     * @param loiterRadius how near a post a creature walking at it stops and ambles about its own patch
     *     instead, in world units - so the other side comes near the post without piling onto it (G0027)
     * @param grenadeCaches how many grenade caches a point lays beside itself the tick it is taken, for every seated
     *     slot (G0078); zero lays none
     */
    @With
    public record ControlPointConfig(
            float radius,
            float captureSeconds,
            float aggroRadius,
            float reinforcementRadius,
            int reinforcementsMin,
            int reinforcementsMax,
            float loiterRadius,
            int grenadeCaches) {
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
            Require.positive("control point loiterRadius", loiterRadius);
            Require.notNegative("control point grenadeCaches", grenadeCaches);
        }
    }

    /**
     * How hard the constraint solver works.
     *
     * @param passes how many times per tick overlap resolution and arena clamping are applied. Raising
     *     it costs time and buys stability, never behaviour
     */
    @With
    public record CollisionConfig(int passes) {
        public CollisionConfig {
            Require.atLeastOne("collision passes", passes);
        }
    }

    /**
     * The grid a level is laid out on, and so the arena: the only place a level's size is decided.
     * Only a scenario's map is built from it; systems read the {@code TileMap}.
     *
     * @param cols tiles across
     * @param rows tiles down
     * @param tileSize width of one tile, in world units
     * @param regions how many areas the ground is partitioned into
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
     * @param maxWounds how many wounds he carries on his feet; one more puts him down (G0053)
     * @param bleedOutSeconds how long he lies shot off his feet before he is gone. His own, not the
     *     squad's {@link CasualtyConfig#bleedOutSeconds}
     * @param deathThroesSeconds how long its body stays on the field after a killing blow, before the
     *     respawn countdown starts
     * @param respawnDelaySeconds how long after the body is removed before it comes back
     * @param respawnShieldSeconds how long the shield he comes back behind lasts, so he does not die again
     *     immediately; it also breaks at the first hit (G0053)
     * @param leashRadius how far a commander may walk from the middle of the commanders, in world
     *     units (pillar 4). Never read from the camera - zoom must not become extra rope. Zero is no
     *     leash
     * @param weapon the gun he spawns and comes back with: the sidearm, in every level but the proving
     *     ground (G0060). A better gun is found on the field and lost on death (pillar 3)
     */
    @With
    public record CommanderConfig(
            float radius,
            float speed,
            int maxWounds,
            float bleedOutSeconds,
            float deathThroesSeconds,
            float respawnDelaySeconds,
            float respawnShieldSeconds,
            float leashRadius,
            WeaponKind weapon) {
        public CommanderConfig {
            Require.positive("commander radius", radius);
            Require.positive("commander speed", speed);
            Require.notNegative("commander maxWounds", maxWounds);
            Require.positive("commander bleedOutSeconds", bleedOutSeconds);
            Require.notNegative("commander deathThroesSeconds", deathThroesSeconds);
            Require.notNegative("commander respawnDelaySeconds", respawnDelaySeconds);
            Require.notNegative("commander respawnShieldSeconds", respawnShieldSeconds);
            Require.notNegative("commander leashRadius", leashRadius);
        }
    }

    /**
     * One AI-controlled member of the squad, as numbers.
     *
     * <p>Not a {@link UnitDef} strain: a trooper has no evolution, leap or contact damage.
     *
     * @param radius circular body, in world units
     * @param speed world units per second at its ordinary pace
     * @param squadSize how many spawn with him; scenarios vary it with {@code withTrooper(...)}
     * @param squadGrowth how many men beyond those the field will hold: the squad's maximum is
     *     {@code squadSize + squadGrowth}. Headroom, so raising {@code squadSize} cannot undercut it
     * @param reinforcementRadius how far from the commander a rescued trooper is put down, in world units
     * @param maxWounds how many wounds one carries on his feet; one more puts him down (G0053)
     * @param deathThroesSeconds how long its body stays on the field after a killing blow
     * @param weapon the gun it carries
     * @param avoidance how far ahead it looks for things in its way, and how hard it leans off them
     * @param urgency how much heading for the objective beats looking around, in {@code [0, 1]}: one is
     *     a beeline. Not a speed - "forward" changes speed
     * @param scanSpread how far off the route a full lean carries it, as a fraction of its heading
     * @param scanMinSeconds shortest a lean lasts before it sweeps back the other way
     * @param scanMaxSeconds longest; drawn between the two so a squad does not sweep in lockstep
     * @param arriveRadius how close counts as having got there, in world units
     * @param perception what it can notice: range, arc and whether rock hides things (G0015)
     * @param orderRadius how far the commander's voice carries, in world units: who hears an order (G0017)
     * @param forwardDistance how much ground a "forward" buys, in world units. A distance, not a countdown
     * @param forwardSpeedFactor how much faster it walks while that lasts
     * @param followDistance how close a following trooper tries to stay to the commander
     * @param patchRadius how far from the post a trooper roams on a level that names one, in world units
     * @param approach how wide of where it is going a trooper walks, so a squad crosses abreast (G0033,
     *     G0041). Off walks straight at the place
     * @param followAheadSeconds how far ahead of the commander a follower aims, in seconds of his
     *     walking. Zero aims at the man himself
     * @param holdSeconds how long a man holds a taken point before following a commander who went on
     *     (G0040). He never goes on ahead of one
     * @param holdJitterSeconds how much each man's hold differs from that, either way, drawn from the
     *     squad's stream
     * @param rallyRadius how near a point a commander must be to count as at it, in world units: at the
     *     next one, the men holding this one set off at once; at this one, they stay
     * @param postRingRadius how far round a point that holds afterwards a trooper's march to it ends, in
     *     world units (G0061): past the point's own radius, so the squad forms up round it and taking it is
     *     left to whoever steps in
     * @param blockSeconds how long a walking man may make less than {@code blockDistance} before he
     *     counts as stuck and takes a detour. Zero never counts anybody as stuck
     * @param blockDistance how much ground in that time still counts as progress, in world units
     * @param detourSeconds how long a stuck man backs off to one side, alternating sides, before trying
     *     again
     * @param alertSeconds how long a man who has been hit looks all round and turns towards where it
     *     came from. Zero leaves him facing the way he was
     * @param threatBonus how much nearer, in world units, something coming for somebody counts as
     * @param sharedTargetPenalty how much further, in world units, something counts as for every man
     *     already shooting at it this tick. Zero is every man for the nearest
     * @param medkitReach how far a wounded man with nothing to fight will walk for a medkit (G0053), in
     *     world units. Zero never
     * @param closeInFactor how far inside his gun's reach a man whose gun has one walks before he plants
     *     himself to shoot, as a fraction of it, in {@code (0, 1]}. He sets off again only once his target
     *     is past the reach itself, so below one is the margin that stops him ambling in and out of it
     * @param grenade how often a trooper throws a grenade, and the rules that keep its blast off his own
     *     side (G0051); whether he ever does is {@code AiChance.THROW_A_GRENADE}
     */
    @With
    public record TrooperConfig(
            float radius,
            float speed,
            int squadSize,
            int squadGrowth,
            float reinforcementRadius,
            int maxWounds,
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
            float postRingRadius,
            float blockSeconds,
            float blockDistance,
            float detourSeconds,
            float alertSeconds,
            float threatBonus,
            float sharedTargetPenalty,
            float medkitReach,
            float closeInFactor,
            TrooperGrenadeDef grenade) {
        public TrooperConfig {
            Require.positive("trooper radius", radius);
            Require.positive("trooper speed", speed);
            Require.notNegative("trooper squadSize", squadSize);
            Require.notNegative("trooper squadGrowth", squadGrowth);
            Require.positive("trooper reinforcementRadius", reinforcementRadius);
            Require.notNegative("trooper maxWounds", maxWounds);
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
            Require.positive("trooper postRingRadius", postRingRadius);
            Require.notNegative("trooper blockSeconds", blockSeconds);
            Require.notNegative("trooper blockDistance", blockDistance);
            Require.notNegative("trooper detourSeconds", detourSeconds);
            Require.notNegative("trooper alertSeconds", alertSeconds);
            Require.notNegative("trooper threatBonus", threatBonus);
            Require.notNegative("trooper sharedTargetPenalty", sharedTargetPenalty);
            if (closeInFactor <= 0f || closeInFactor > 1f) {
                throw new IllegalArgumentException("trooper closeInFactor must be in (0, 1], was " + closeInFactor);
            }
            if (holdJitterSeconds > holdSeconds) {
                throw new IllegalArgumentException("trooper holdJitterSeconds must not be above holdSeconds");
            }
        }
    }

    /**
     * Where a level puts the player's side to start with, as a rectangle of tiles on the left of the
     * map.
     *
     * <p>Scaffolding until a map carries its own entrances. Units are jittered inside the tiles (G0009).
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
            return on(map.cols(), map.rows());
        }

        /** The spawn area on a map this many tiles across and down, before the map exists - a mission's start. */
        public TileRect on(int mapCols, int mapRows) {
            int rows = Math.min(height, mapRows);
            int row = Math.max(0, (mapRows - rows) / 2);
            return new TileRect(col, row, Math.min(width, mapCols), rows);
        }
    }

    /**
     * The reserve: the men a side has off the field (G0055). The field starts with the squad
     * ({@link TrooperConfig#squadSize}) and holds it plus its headroom; the reserve is everybody else,
     * and every man who comes onto the field after the first tick - a muster's trooper, a returning
     * commander - is one of them. A scenario may set every number.
     *
     * @param start how many the reserve holds when the level starts
     * @param max the most it ever holds; a crate's men past it are wasted
     * @param musterSeconds how long between musters, which call men from the reserve onto the field
     *     while it has room. Zero never musters
     * @param musterMin how few a muster calls up
     * @param musterMax how many, dealt from a bag. At most {@code Bag.MOST} sizes
     */
    @With
    public record ReserveConfig(int start, int max, float musterSeconds, int musterMin, int musterMax) {
        public ReserveConfig {
            Require.notNegative("reserve start", start);
            Require.atLeast("reserve max", max, start, "a reserve cannot start fuller than it can hold");
            Require.notNegative("reserve musterSeconds", musterSeconds);
            Require.notNegative("reserve musterMin", musterMin);
            Require.atLeast(
                    "reserve musterMax", musterMax, musterMin, "a muster cannot have a smaller most than fewest");
            Require.fitsABag("reserve muster", musterMin, musterMax);
        }
    }

    /**
     * The commander's hand strike: a short, fast sweep that answers something already committed to
     * a leap.
     *
     * @param cooldownSeconds minimum time between swings
     * @param windupSeconds delay between the input and the sweep appearing. Zero for a bare hand
     * @param activeSeconds how long the sweep is in the world and able to connect - the player's window
     * @param reach distance from the commander's centre to the centre of the sweep, in world units;
     *     moves when he must press, not how long he has
     * @param radius circular body of the sweep itself
     * @param damage what a connecting sweep takes off what it hits
     * @param arcDegrees how far around the aim direction the sweep travels; 180 is a half-circle
     * @param lockSeconds how long a connecting sweep holds both the commander and its target still
     * @param locksCommittedOnly whether that lock is spent only when the target had already committed
     *     to attacking; otherwise a kill is a swat costing only the cooldown
     * @param clinchCarrySpeed how fast a body it kills finishes its lunge into the commander, in world
     *     units per second. Must carry at least {@link #longestReachableGap} (checked)
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
            int damage,
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
         * <p>A carry that never decays never stops: {@code Float.MAX_VALUE}.
         */
        public float carryDistance() {
            return clinchCarryDecay <= 0f
                    ? Float.MAX_VALUE
                    : clinchCarrySpeed * clinchCarrySpeed / (2f * clinchCarryDecay);
        }

        /**
         * The stride a sweep can leave between the commander and something it killed at full stretch.
         *
         * <p>The target's radius cancels, counting on both sides. The clinch must close at least this.
         */
        public float longestReachableGap(float bodyRadius) {
            float gap = reach + radius - bodyRadius;
            return gap > 0f ? gap : 0f;
        }
    }

    /**
     * What an explosion does, wherever it came from.
     *
     * <p>No radius: each explosive names its own. One rule for every blast - fatal inside the circle.
     *
     * @param damage what a blast takes off whatever it catches; above any unit's health
     * @param durationSeconds how long the flash is drawn for
     * @param shakeStrength how hard the ground moves at the middle of one, in world units - how violent
     *     the event was, not what a camera does. Zero for none
     * @param shakeRadiusFactor how far that is felt, as a multiple of the blast's own radius
     * @param harm what one does to a man it catches (G0053), unless the explosive names its own - a
     *     grenade does
     */
    @With
    public record BlastConfig(
            int damage, float durationSeconds, float shakeStrength, float shakeRadiusFactor, Harm harm) {
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
     * <p>One block for every unit: what differs between strains is already the body's radius.
     *
     * @param referenceRadius the body a gun's {@code bloatPerShot} is quoted against, in world units; a
     *     bigger body takes proportionally longer to fill
     * @param livingFactor how much of a shot's worth a thing still on its feet banks, as a fraction
     * @param settlePerSecond how much of the charge falls away per second with nothing on it. A thing
     *     being charged does not settle
     * @param blastFactor how wide the circle is when one bursts, as a multiple of the body's radius.
     *     Leaves nothing burning. Zero turns the mechanic off
     * @param holdSeconds the least a charged body has left to lie there, refreshed while any charge is
     *     on it
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
         * <p>In proportion to the radius, not the area. A thing still on its feet banks only
         * {@code livingFactor} of it.
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
     * What a gun's shots build on a body until it is stunned (G0059), and what comes off it again.
     *
     * <p>One block for every unit: what differs between strains is already the body's radius, as with
     * {@link BloatConfig}.
     *
     * @param referenceRadius the body a gun's {@code stunAfterHits} is quoted against, in world units; a
     *     bigger body takes proportionally more hits
     * @param drainPerSecond how much of a full shock comes off per second on a tick nothing builds it
     */
    @With
    public record ShockConfig(float referenceRadius, float drainPerSecond) {

        public ShockConfig {
            Require.positive("shock referenceRadius", referenceRadius);
            Require.notNegative("shock drainPerSecond", drainPerSecond);
        }

        /** What one shot is worth against a body of this size: in proportion to the radius, not the area. */
        public float perHitAgainst(float bodyRadius, float perHit) {
            return bodyRadius <= 0f ? perHit : perHit * referenceRadius / bodyRadius;
        }
    }

    /**
     * What an explosion leaves burning where it was.
     *
     * <p>One block for every fire, sized as fractions of the blast, so a new explosive gets fire without
     * naming a number. Produces a {@link SlickDef}: fire is ruined ground (G0035), and neutral.
     *
     * @param blobs how many circles one explosion leaves burning
     * @param radiusFactor how wide each of them gets, as a fraction of the blast's own radius
     * @param spreadFactor how far from the middle they fall, as a fraction of the blast's radius; under
     *     one, or it is a blast that lingers (G0018)
     * @param minSeconds the shortest a circle burns for
     * @param maxSeconds the longest; drawn per circle between the two
     * @param damage what walking into one takes off anything with health. A man it wounds (G0053)
     * @param victimInvulnerableSeconds how long an alien it has hurt is protected from the rest of it; a
     *     man's protection is his own grace
     * @param growFraction how much of its life a circle spends swelling
     * @param fadeFraction how much of it it spends collapsing again
     * @param knockbackSpeed how hard a man it wounds is thrown off it, in world units per second
     */
    @With
    public record FireConfig(
            int blobs,
            float radiusFactor,
            float spreadFactor,
            float minSeconds,
            float maxSeconds,
            int damage,
            float victimInvulnerableSeconds,
            float growFraction,
            float fadeFraction,
            float knockbackSpeed) {

        public FireConfig {
            Require.notNegative("fire blobs", blobs);
            Require.notNegative("fire radiusFactor", radiusFactor);
            Require.notNegative("fire spreadFactor", spreadFactor);
            Require.notNegative("fire minSeconds", minSeconds);
            Require.notNegative("fire damage", damage);
            Require.notNegative("fire victimInvulnerableSeconds", victimInvulnerableSeconds);
            Require.notNegative("fire growFraction", growFraction);
            Require.notNegative("fire fadeFraction", fadeFraction);
            Require.notNegative("fire knockbackSpeed", knockbackSpeed);
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
                    Look.FIRE,
                    knockbackSpeed,
                    Harm.WOUND,
                    1f,
                    0f,
                    1f);
        }
    }

    /**
     * What an alien leaves where it dies: a spatter of its own venom (G0076).
     *
     * <p>One block for every strain, as fire is one block for every explosion. How many circles is not
     * here: it is the strain's own count plus what the swarm's toxic blood adds, read when it dies, so an
     * alien already on the field when the swarm adapts leaves it as readily as one born after. Produces a
     * {@link SlickDef}: it is ruined ground, on the side of whatever died.
     *
     * @param radius how wide each circle is, in world units
     * @param spread how far from where it fell their centres may land
     * @param seconds the shortest a circle stays on the ground
     * @param secondsMax the longest; drawn per circle between the two
     * @param damage what walking into one takes off anything with health. A man it wounds (G0053)
     * @param victimInvulnerableSeconds how long whatever it hurts is protected from the rest of it; a man's protection
     *     is his own grace
     * @param fadeFraction how much of its life a circle spends drying up
     * @param knockbackSpeed how hard a man it wounds is thrown off it, in world units per second
     * @param heeded the chance a trooper gives one spatter room, decided once per man per spatter
     */
    @With
    public record BloodConfig(
            float radius,
            float spread,
            float seconds,
            float secondsMax,
            int damage,
            float victimInvulnerableSeconds,
            float fadeFraction,
            float knockbackSpeed,
            float heeded) {

        public BloodConfig {
            Require.notNegative("blood radius", radius);
            Require.notNegative("blood spread", spread);
            Require.notNegative("blood seconds", seconds);
            Require.notNegative("blood damage", damage);
            Require.notNegative("blood victimInvulnerableSeconds", victimInvulnerableSeconds);
            Require.notNegative("blood fadeFraction", fadeFraction);
            Require.notNegative("blood knockbackSpeed", knockbackSpeed);
            if (secondsMax < seconds) {
                throw new IllegalArgumentException("blood cannot lie for less than its own minimum");
            }
            if (heeded < 0f || heeded > 1f) {
                throw new IllegalArgumentException("blood heeded is a chance, in [0, 1], was " + heeded);
            }
        }

        /** No blood at all: a game in which an alien leaves nothing where it dies. */
        public static BloodConfig off() {
            return new BloodConfig(0f, 0f, 0f, 0f, 0, 0f, 0f, 0f, 1f);
        }

        /**
         * The shape of what an alien leaves, as the ruined ground it is, with no circles yet: how many is the dier's
         * own count and the swarm's, read as it dies.
         */
        public SlickDef shape() {
            return new SlickDef(
                    0,
                    radius,
                    spread,
                    seconds,
                    secondsMax,
                    damage,
                    victimInvulnerableSeconds,
                    0f,
                    fadeFraction,
                    Look.SLICK,
                    knockbackSpeed,
                    Harm.WOUND,
                    1f,
                    0f,
                    heeded);
        }
    }

    /**
     * The neutral creature that wanders the field, is eaten by whatever catches it, and sidesteps a
     * shot it sees coming.
     *
     * <p>What it is - size, look, what taking it grants - is its {@code PickupDef}; what it does is here.
     *
     * @param speed how fast it moves under its own power, in world units per second
     * @param health what it absorbs before dying
     * @param deathThroesSeconds how long the body lies there; a body cannot be eaten
     * @param wander how it passes the time
     * @param dodge how it answers a shot
     */
    @With
    public record RabbitConfig(float speed, int health, float deathThroesSeconds, WanderDef wander, DodgeDef dodge) {
        public RabbitConfig {
            Require.positive("rabbit speed", speed);
            Require.positive("rabbit health", health);
            Require.notNegative("rabbit deathThroesSeconds", deathThroesSeconds);
        }
    }

    /**
     * What the commander throws.
     *
     * <p>{@code range} is a distance, not a countdown: a bounce changes direction, never speed, so every
     * throw covers the same ground, spent along the aim direction. Unlimited grenades are scaffolding.
     *
     * @param cooldownSeconds minimum time between throws
     * @param speed how fast it travels, in world units per second
     * @param range how far it travels in total, in world units, however it bounces
     * @param radius the grenade's own circular body
     * @param blastRadius how wide the blast it leaves is, in world units
     * @param blastHarm what that blast does to a man (G0053): a grenade is a weapon meant to wound, the
     *     second kind of blast G0018 asked for
     * @param most how many grenades a commander carries at most, and starts with (G0077) - a trooper's throws are
     *     not counted
     */
    @With
    public record GrenadeConfig(
            float cooldownSeconds,
            float speed,
            float range,
            float radius,
            float blastRadius,
            Harm blastHarm,
            int most) {
        public GrenadeConfig {
            Require.atLeastOne("grenade most", most);
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
     * <p>Its size is its {@link PickupDef}'s.
     *
     * @param health what it absorbs before it goes off
     * @param blastRadius how wide the blast it leaves is, in world units
     * @param bittenBlastRadius how wide it goes off when something bit into it rather than shot it, in
     *     world units
     */
    @With
    public record BarrelConfig(int health, float blastRadius, float bittenBlastRadius) {
        public BarrelConfig {
            Require.positive("barrel bittenBlastRadius", bittenBlastRadius);
            Require.positive("barrel health", health);
            Require.positive("barrel blastRadius", blastRadius);
        }
    }

    /**
     * The explosive that goes off when the other side walks into it.
     *
     * <p>It explodes by dying, through the one reaction (G0018), so nothing about a blast is here.
     *
     * @param radius how big the thing itself is, in world units
     * @param triggerRadius how close something on the other side has to come, in world units
     * @param fuseSeconds how long it ticks before it goes off
     * @param health what it absorbs before it goes off early
     * @param blastRadius how wide it goes off, in world units
     * @param flashFromSeconds how long one flash of the fuse lasts when it has just started
     * @param flashToSeconds how long one lasts at the end; not above {@code flashFromSeconds}, so the
     *     flashing quickens - the tell
     */
    @With
    public record MineConfig(
            float radius,
            float triggerRadius,
            float fuseSeconds,
            int health,
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
     * @param bleedOutSeconds how long he lies there before he is gone
     * @param stabiliseSeconds how long somebody has to stand over him, unbroken, to put him back on his
     *     feet
     * @param reviveInvulnerableSeconds how long he is protected as he stands up
     * @param draggedBleedFactor how fast his clock runs while he is being hauled, as a multiple of
     *     normal. Zero stops it
     * @param hauledStabiliseFactor what having been hauled multiplies the work by
     */
    @With
    public record CasualtyConfig(
            float bleedOutSeconds,
            float stabiliseSeconds,
            float reviveInvulnerableSeconds,
            float draggedBleedFactor,
            float hauledStabiliseFactor) {

        public CasualtyConfig {
            Require.positive("casualty bleedOutSeconds", bleedOutSeconds);
            Require.positive("casualty stabiliseSeconds", stabiliseSeconds);
            Require.notNegative("casualty reviveInvulnerableSeconds", reviveInvulnerableSeconds);
            Require.notNegative("casualty draggedBleedFactor", draggedBleedFactor);
            Require.positive("casualty hauledStabiliseFactor", hauledStabiliseFactor);
        }
    }

    /**
     * A crate (G0056): how many pickups one is dealt on its first opening, from a bag, so the fewest three
     * times running is not a streak. An upgrade may raise the fewest ({@code Modifiers.crateLeast}); the
     * most rises with it, and neither passes {@link #CAPACITY}.
     *
     * @param fewest the fewest pickups a crate is dealt
     * @param most the most, before any upgrade
     */
    @With
    public record CrateConfig(int fewest, int most) {

        /** The most any crate holds, whatever an upgrade says: the cards a top bar has room for. */
        public static final int CAPACITY = 4;

        public CrateConfig {
            Require.atLeastOne("crate fewest", fewest);
            Require.atLeast("crate most", most, fewest, "a crate cannot hold a smaller most than fewest");
            if (most > CAPACITY) {
                throw new IllegalArgumentException(
                        "a crate holds at most " + CAPACITY + " pickups - the cards a top bar shows - not " + most);
            }
        }
    }

    /**
     * A pop (G0078): a pickup the player's side has just taken by touch, shown for a moment rising off the taker.
     *
     * @param seconds how long one lasts; how far it rises and how it fades are the renderer's
     */
    @With
    public record PopConfig(float seconds) {
        public PopConfig {
            Require.positive("pop seconds", seconds);
        }
    }

    /**
     * Medkits a level scatters (G0053). What one is - how big, how it looks, what it grants - is its
     * {@code PickupDef}.
     *
     * @param perLevel how many a level that asks for them scatters beside its control points; a level
     *     that does not ask gets none
     */
    @With
    public record MedkitConfig(int perLevel) {

        public MedkitConfig {
            Require.notNegative("medkit perLevel", perLevel);
        }
    }

    /**
     * The swarm as a whole.
     *
     * @param healthPercent what every strain's and the nest's health is scaled by, as a whole
     *     percentage - one number, so the ratios between strains survive a retune. Not barrels, mines,
     *     rabbits or turrets. A hundred is the numbers as written; the result is rounded by the one rule
     *     hit points have ({@link Whole})
     * @param tierPercent the same, for a mission's tier, on top of {@code healthPercent} and rounded with it
     *     once (G0063). A hundred off a mission; a mission's comes from {@link MissionConfig#apply}
     */
    @With
    public record SwarmConfig(int healthPercent, int tierPercent) {

        public SwarmConfig {
            Require.atLeastOne("swarm healthPercent", healthPercent);
            Require.atLeastOne("swarm tierPercent", tierPercent);
        }
    }

    /**
     * What a wound does to a man still on his feet (G0053).
     *
     * @param graceSeconds how long one wound protects him from the next - and from nothing heavier
     * @param shoveSeconds how long ground that wounds him throws him off itself for; the throw bleeds off
     *     over exactly this long
     * @param speedFactor how much of his pace a wounded man keeps, in {@code (0, 1]}
     * @param cooldownFactor what a wound multiplies the time between his gun's shots by, at least one
     * @param mendSeconds how long after his last wound a man's wounds clear on their own - the base the
     *     {@code MEND_SECONDS} stat adds to. Zero is never, which is every man until an upgrade says
     *     otherwise
     */
    @With
    public record WoundConfig(
            float graceSeconds, float shoveSeconds, float speedFactor, float cooldownFactor, int mendSeconds) {

        public WoundConfig {
            Require.notNegative("wound graceSeconds", graceSeconds);
            Require.notNegative("wound shoveSeconds", shoveSeconds);
            Require.notNegative("wound mendSeconds", mendSeconds);
            if (speedFactor <= 0f || speedFactor > 1f) {
                throw new IllegalArgumentException("wound speedFactor must be in (0, 1], was " + speedFactor);
            }
            if (cooldownFactor < 1f) {
                throw new IllegalArgumentException("wound cooldownFactor must be at least one, was " + cooldownFactor);
            }
        }
    }

    /**
     * What the commander can do with his hands, and how close he has to be to do it.
     *
     * @param reach how close something must be, surface to surface, to be within arm's length, in world
     *     units. One distance for every action
     * @param dragSpeedFactor how much of his walking pace survives while he hauls, in {@code (0, 1]}
     * @param dragLateralFactor how much of a sideways step survives while he hauls, in {@code [0, 1]}
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
     * @param radius circular body, in world units, though drawn square (ADR 0007)
     * @param health damage it absorbs before it is destroyed
     * @param blastRadius how wide a circle it leaves when it goes, in world units
     * @param turnRate how fast it comes round with the trigger off, as the fraction of the angle left
     *     it covers per second
     * @param firingTurnFactor what is left of that rate while it is firing, in {@code (0, 1]}
     * @param range how far a crew of its own judgement will shoot from it, in world units
     * @param crewRadius how far a trooper will divert to get on a free one, in world units
     * @param quietSeconds how long with nothing in range before a trooper gets off again
     * @param weapon which gun is on it
     */
    @With
    public record TurretConfig(
            float radius,
            int health,
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
     * @param radius a boulder's circular body, in world units
     * @param count how many boulders the scenario spawns
     * @param trunkMin the thinnest a tree's trunk is, its radius in world units (G0072)
     * @param trunkMax the thickest
     */
    @With
    public record ObstacleConfig(float radius, int count, float trunkMin, float trunkMax) {
        public ObstacleConfig {
            Require.positive("obstacle radius", radius);
            Require.notNegative("obstacle count", count);
            Require.positive("obstacle trunkMin", trunkMin);
            Require.atLeast("obstacle trunkMax", trunkMax, trunkMin, "a trunk cannot be thickest below its thinnest");
        }

        /** How thick one tree's trunk is, drawn. */
        public float trunk(Randomness rng) {
            return trunkMin == trunkMax ? trunkMin : rng.nextFloat(trunkMin, trunkMax);
        }
    }
}
