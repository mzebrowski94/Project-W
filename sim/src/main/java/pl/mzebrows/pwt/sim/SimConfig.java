package pl.mzebrows.pwt.sim;

import java.util.function.UnaryOperator;
import lombok.With;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.def.AiChance;
import pl.mzebrows.pwt.sim.def.AvoidanceDef;
import pl.mzebrows.pwt.sim.def.Chances;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.DodgeDef;
import pl.mzebrows.pwt.sim.def.Grant;
import pl.mzebrows.pwt.sim.def.HeatDef;
import pl.mzebrows.pwt.sim.def.LeapDef;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.def.PerceptionDef;
import pl.mzebrows.pwt.sim.def.PickupDef;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.RangedDef;
import pl.mzebrows.pwt.sim.def.Require;
import pl.mzebrows.pwt.sim.def.UnitDef;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WanderDef;
import pl.mzebrows.pwt.sim.def.WeaponDef;
import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.sim.def.WeaveDef;

/**
 * Tunables the simulation reads at construction time. Loaded from {@code game.properties} by the
 * client module - the simulation itself never touches the filesystem.
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
 * @param grenade what the commander throws
 * @param rabbit the neutral creature that wanders the field and is eaten
 * @param barrel the explosive one, which is neither eaten nor picked up
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
        Defs<UnitKind, UnitDef> units,
        Defs<PickupKind, PickupDef> pickups,
        Defs<WeaponKind, WeaponDef> weapons,
        Chances chances,
        MeleeConfig melee,
        BlastConfig blast,
        GrenadeConfig grenade,
        RabbitConfig rabbit,
        BarrelConfig barrel,
        CasualtyConfig casualty,
        ActionConfig action,
        ControlPointConfig controlPoint,
        TurretConfig turret,
        ObstacleConfig obstacle,
        CollisionConfig collision) {

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

    /** Values used when {@code game.properties} is missing or unreadable. */
    public static SimConfig defaults() {
        return new SimConfig(
                1L,
                1f / 60f,
                5,
                new MapConfig(32, 18, 5f, 6),
                new CommanderConfig(2f, 19f, 100f, 1.5f, 3f, 6f, 2f, 120f),
                new TrooperConfig(
                        1.6f,
                        13.5f,
                        4,
                        100f,
                        1f,
                        WeaponKind.SIDEARM,
                        new AvoidanceDef(true, 7f, 0.35f, 1.6f),
                        0.55f,
                        0.8f,
                        0.7f,
                        1.6f,
                        3f,
                        new PerceptionDef(false, true, 22f, 28f, 0f, 100f, true),
                        22f,
                        40f,
                        1.6f,
                        7f,
                        30f),
                new SpawnConfig(1, 2, 5),
                defaultUnits(),
                defaultPickups(),
                defaultWeapons(),
                Chances.standard(),
                new MeleeConfig(0.6f, 0f, 0.12f, 1.2f, 1.4f, 120f, 180f, 1f, true, 14f, 100f, 45f, 120f),
                new BlastConfig(1000f, 0.25f, 3f, 2.5f),
                new GrenadeConfig(1.2f, 38f, 34f, 0.55f, 12f),
                new RabbitConfig(
                        15f,
                        1f,
                        0.4f,
                        new WanderDef(true, 18f, 0.7f, 1f, 0.3f, 1.1f, 3f),
                        new DodgeDef(true, 26f, 32f, 0.32f, 0.9f, 1.2f)),
                new BarrelConfig(1f, 11f, 4f),
                new CasualtyConfig(8f, 0.8f, 0.4f, 1f, 0f, 1.6f),
                new ActionConfig(2.5f, 0.45f, 0.35f, 0.4f),
                new ControlPointConfig(6f, 5f, 48f),
                new TurretConfig(2.2f, 300f, 6f, 2.5f, 0.3f, 55f, 26f, 6f, WeaponKind.TURRET_GUN),
                new ObstacleConfig(3.5f, 8),
                new CollisionConfig(3));
    }

    private static Defs<UnitKind, UnitDef> defaultUnits() {
        return Defs.<UnitKind, UnitDef>builder(UnitKind.class)
                .put(
                        UnitKind.GRUNT,
                        new UnitDef(
                                1.8f,
                                7.5f,
                                12,
                                100f,
                                100f,
                                200f,
                                1.3f,
                                0.6f,
                                1.5f,
                                1f,
                                3,
                                UnitKind.BRUTE,
                                PerceptionDef.allRound(true, true, 30f, 45f, 14f),
                                new WanderDef(true, 12f, 0.45f, 1.5f, 0.4f, 1.4f, 4f),
                                new AvoidanceDef(true, 7f, 0.35f, 1.6f),
                                new LeapDef(true, 16f, 29f, 0.28f, 2, 0.18f, 1.6f, 0.08f, 0.22f),
                                RangedDef.off(),
                                WeaveDef.off(),
                                DodgeDef.off(),
                                Chances.standard(),
                                Look.GRUNT))
                .put(
                        UnitKind.BRUTE,
                        new UnitDef(
                                2.6f,
                                4.75f,
                                3,
                                250f,
                                100f,
                                200f,
                                1.5f,
                                0.6f,
                                1.5f,
                                1f,
                                0,
                                null,
                                PerceptionDef.allRound(true, true, 24f, 36f, 10f),
                                new WanderDef(true, 8f, 0.35f, 2f, 0.8f, 2.4f, 5f),
                                new AvoidanceDef(true, 9f, 0.5f, 1.3f),
                                new LeapDef(true, 22f, 24f, 0.42f, 3, 0.26f, 2.4f, 0.18f, 0.42f),
                                RangedDef.off(),
                                WeaveDef.off(),
                                DodgeDef.off(),
                                Chances.standard(),
                                Look.BRUTE))
                .put(
                        UnitKind.SPITTER,
                        new UnitDef(
                                1.7f,
                                6f,
                                4,
                                70f,
                                100f,
                                200f,
                                1.3f,
                                0.6f,
                                1.5f,
                                1f,
                                0,
                                null,
                                PerceptionDef.allRound(true, true, 30f, 45f, 14f),
                                new WanderDef(true, 10f, 0.4f, 1.5f, 0.5f, 1.6f, 4f),
                                new AvoidanceDef(true, 7f, 0.35f, 1.6f),
                                LeapDef.off(),
                                new RangedDef(
                                        true,
                                        18f,
                                        14f,
                                        true,
                                        WeaponKind.SPIT,
                                        true,
                                        150f,
                                        3f,
                                        9f,
                                        0.6f,
                                        3,
                                        0.35f,
                                        WeaponKind.CHARGED_SPIT),
                                // It wants a barrel far more than anything that closes and claws
                                // does, because a barrel is what turns its gun into an artillery
                                // piece. Standing one next to a barrel is a decision a level makes.
                                WeaveDef.off(),
                                DodgeDef.off(),
                                Chances.standard().with(AiChance.BITE_A_BARREL, 8f),
                                Look.SPITTER))
                .put(
                        UnitKind.RUNNER,
                        new UnitDef(
                                1.4f,
                                9.5f,
                                6,
                                60f,
                                100f,
                                200f,
                                1.1f,
                                0.6f,
                                1.5f,
                                0.8f,
                                0,
                                null,
                                // It notices you from most of the way across a level, which is the
                                // whole of what it is for: the strain that comes to find you.
                                PerceptionDef.allRound(true, true, 60f, 80f, 14f),
                                new WanderDef(true, 16f, 0.5f, 1.5f, 0.3f, 1f, 4f),
                                new AvoidanceDef(true, 7f, 0.35f, 1.6f),
                                new LeapDef(true, 14f, 34f, 0.24f, 2, 0.14f, 1.2f, 0.06f, 0.16f),
                                RangedDef.off(),
                                new WeaveDef(true, 0.55f, 9f),
                                new DodgeDef(true, 22f, 26f, 0.26f, 0.7f, 1.4f),
                                Chances.standard(),
                                Look.RUNNER))
                .build();
    }

    /**
     * Every gun, by kind. The sidearm is what the commander starts and respawns with; the rest are
     * things he has to find and then hold on to.
     */
    private static Defs<WeaponKind, WeaponDef> defaultWeapons() {
        return Defs.<WeaponKind, WeaponDef>builder(WeaponKind.class)
                .put(WeaponKind.SIDEARM, new WeaponDef(0.3f, 42f, 0.6f, 50f, Look.PROJECTILE, 0f, HeatDef.none(), 0f))
                .put(
                        WeaponKind.RAILGUN,
                        new WeaponDef(1.2f, 85f, 1f, 300f, Look.SLUG, 0f, new HeatDef(0.34f, 0.12f, 0.4f), 0f))
                .put(WeaponKind.SPIT, new WeaponDef(1.1f, 30f, 0.7f, 100f, Look.SPIT, 0f, HeatDef.none(), 0f))
                .put(
                        WeaponKind.CHARGED_SPIT,
                        new WeaponDef(1.1f, 30f, 0.8f, 100f, Look.CHARGED_SPIT, 5f, HeatDef.none(), 0f))
                .put(
                        WeaponKind.TURRET_GUN,
                        new WeaponDef(
                                0.16f, 150f, 0.45f, 30f, Look.TURRET_SHOT, 0f, new HeatDef(0.1f, 0.35f, 0.6f), 0.9f))
                .build();
    }

    /**
     * What each kind of pickup is and does.
     *
     * <p><strong>What a pickup grants is structure, not tuning</strong>, so it lives here rather than
     * in {@code game.properties} - which effect an item has, and which strain it names, are decisions
     * about the design rather than numbers to balance, and an evolution chain edited in a properties
     * file could be edited into a loop. The number that actually balances an eating evolution is
     * {@code feedToEvolve} on the strain, and that is loaded.
     */
    private static Defs<PickupKind, PickupDef> defaultPickups() {
        return Defs.<PickupKind, PickupDef>builder(PickupKind.class)
                .put(PickupKind.MUSHROOM, new PickupDef(0.9f, Look.MUSHROOM, Grant.NOTHING, new Grant.Feed(1)))
                .put(
                        PickupKind.FLOWER,
                        new PickupDef(0.9f, Look.FLOWER, Grant.NOTHING, new Grant.Mutate(UnitKind.BRUTE)))
                .put(
                        PickupKind.FROZEN_TROOPER,
                        new PickupDef(1.6f, Look.FROZEN_TROOPER, new Grant.Reinforce(1), new Grant.Feed(1)))
                .put(
                        PickupKind.ARMOURY,
                        new PickupDef(1.2f, Look.ARMOURY, new Grant.Arm(WeaponKind.RAILGUN), new Grant.Feed(1)))
                .put(PickupKind.RABBIT, new PickupDef(0.8f, Look.RABBIT, Grant.NOTHING, new Grant.Feed(1)))
                .put(PickupKind.BARREL, new PickupDef(1f, Look.BARREL, Grant.NOTHING, Grant.DETONATE))
                .build();
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
        for (WeaponDef gun : weapons.values()) {
            largest = Math.max(largest, gun.projectileRadius());
        }
        for (UnitDef strain : units.values()) {
            largest = Math.max(largest, strain.radius());
        }
        for (PickupDef pickup : pickups.values()) {
            largest = Math.max(largest, pickup.radius());
        }
        return largest;
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
     */
    @With
    public record ControlPointConfig(float radius, float captureSeconds, float aggroRadius) {
        public ControlPointConfig {
            Require.positive("control point radius", radius);
            Require.positive("control point captureSeconds", captureSeconds);
            Require.notNegative("control point aggroRadius", aggroRadius);
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
            float deathThroesSeconds,
            float respawnDelaySeconds,
            float respawnMargin,
            float respawnInvulnerableSeconds,
            float leashRadius) {
        public CommanderConfig {
            Require.positive("commander radius", radius);
            Require.positive("commander speed", speed);
            Require.positive("commander health", health);
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
     */
    @With
    public record TrooperConfig(
            float radius,
            float speed,
            int squadSize,
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
            float patchRadius) {
        public TrooperConfig {
            Require.positive("trooper radius", radius);
            Require.positive("trooper speed", speed);
            Require.notNegative("trooper squadSize", squadSize);
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
