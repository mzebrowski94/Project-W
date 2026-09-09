package pl.mzebrows.pwt.sim;

import java.util.function.UnaryOperator;
import lombok.With;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.Grant;
import pl.mzebrows.pwt.sim.def.LeapDef;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.def.PickupDef;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.Require;
import pl.mzebrows.pwt.sim.def.UnitDef;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WeaponDef;
import pl.mzebrows.pwt.sim.def.WeaponKind;

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
 * @param units every strain of chasing unit, by kind
 * @param pickups every kind of thing that can be lying on the field, by kind
 * @param weapons every gun the commander can be carrying, by kind
 * @param melee the commander's hand strike
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
        Defs<UnitKind, UnitDef> units,
        Defs<PickupKind, PickupDef> pickups,
        Defs<WeaponKind, WeaponDef> weapons,
        MeleeConfig melee,
        ObstacleConfig obstacle,
        CollisionConfig collision) {

    public SimConfig {
        Require.positive("tickSeconds", tickSeconds);
        Require.atLeastOne("maxTicksPerFrame", maxTicksPerFrame);
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
                new CommanderConfig(2f, 28f, 100f, 1.5f, 3f, 6f, 2f),
                defaultUnits(),
                defaultPickups(),
                defaultWeapons(),
                new MeleeConfig(0.45f, 0f, 0.12f, 1.2f, 1.4f, 120f, 180f, 1f, 45f, 120f),
                new ObstacleConfig(3.5f, 8),
                new CollisionConfig(3));
    }

    private static Defs<UnitKind, UnitDef> defaultUnits() {
        return Defs.<UnitKind, UnitDef>builder(UnitKind.class)
                .put(
                        UnitKind.GRUNT,
                        new UnitDef(
                                1.8f,
                                10.5f,
                                12,
                                100f,
                                100f,
                                1f,
                                0.6f,
                                1.5f,
                                1f,
                                3,
                                UnitKind.BRUTE,
                                new LeapDef(true, 16f, 42f, 0.28f, 2, 0.18f, 1.6f, 0.08f, 0.22f),
                                Look.GRUNT))
                .put(
                        UnitKind.BRUTE,
                        new UnitDef(
                                2.6f,
                                6.75f,
                                3,
                                250f,
                                100f,
                                1.2f,
                                0.6f,
                                1.5f,
                                1f,
                                0,
                                null,
                                new LeapDef(true, 22f, 34f, 0.42f, 3, 0.26f, 2.4f, 0.18f, 0.42f),
                                Look.BRUTE))
                .build();
    }

    /**
     * Every gun, by kind. The sidearm is what the commander starts and respawns with; the rest are
     * things he has to find and then hold on to.
     */
    private static Defs<WeaponKind, WeaponDef> defaultWeapons() {
        return Defs.<WeaponKind, WeaponDef>builder(WeaponKind.class)
                .put(WeaponKind.SIDEARM, new WeaponDef(0.18f, 70f, 0.6f, 50f, Look.PROJECTILE))
                .put(WeaponKind.RAILGUN, new WeaponDef(0.8f, 140f, 1f, 300f, Look.SLUG))
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
        largest = Math.max(largest, melee.radius());
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
     */
    @With
    public record CommanderConfig(
            float radius,
            float speed,
            float health,
            float deathThroesSeconds,
            float respawnDelaySeconds,
            float respawnMargin,
            float respawnInvulnerableSeconds) {
        public CommanderConfig {
            Require.positive("commander radius", radius);
            Require.positive("commander speed", speed);
            Require.positive("commander health", health);
            Require.notNegative("commander deathThroesSeconds", deathThroesSeconds);
            Require.notNegative("commander respawnDelaySeconds", respawnDelaySeconds);
            Require.notNegative("commander respawnMargin", respawnMargin);
            Require.notNegative("commander respawnInvulnerableSeconds", respawnInvulnerableSeconds);
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
            Require.notNegative("melee knockbackSpeed", knockbackSpeed);
            Require.notNegative("melee knockbackDecay", knockbackDecay);
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
