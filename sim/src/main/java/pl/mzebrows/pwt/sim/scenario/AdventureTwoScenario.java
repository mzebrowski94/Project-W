package pl.mzebrows.pwt.sim.scenario;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import pl.mzebrows.pwt.map.Randomness;
import pl.mzebrows.pwt.map.Scatter;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Lair;
import pl.mzebrows.pwt.sim.component.Level;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.def.Objective;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * The second march east, as long as the first, and <strong>the ants are in every biome of it but the
 * first</strong> (G0045).
 *
 * <p>Built on {@link AdventureScenario}'s shape - seven biomes, six walls with one gap each, two points
 * in every biome you march through and a last one you take and then hold - so what is different is
 * what lives in it. Each biome pairs the ant with one other problem, and the pairing is the lesson:
 * a creature you have to get round is a different creature next to each of the others.
 *
 * <ul>
 *   <li><strong>The scrub.</strong> Grunts, runners and rabbits, and no ants. The squad and the order
 *       keys again, before anything asks for more.
 *   <li><strong>The sand.</strong> Spitters, and the first three ants, far apart on open ground: room to
 *       walk round one and find out that its back is soft and its face is not.
 *   <li><strong>The anthill.</strong> Ants in numbers and the holes they come out of, with grunts and
 *       food among them. The biome that teaches the grip: a man will be taken here, and getting him
 *       back is the fight.
 *   <li><strong>The fen.</strong> Frogs and ants, in reeds. Getting round something is walking across
 *       the ground beside it, and here that ground is what the frogs are ruining.
 *   <li><strong>The cut.</strong> Brutes, spitters and ants among heavy rock - corridors where there is
 *       no getting round anything, so a fist in its face or a barrel beside it is the answer.
 *   <li><strong>The brake.</strong> Runners and ants in undergrowth, which he wades through and they
 *       barely notice: the one biome where the chase after a man who has been taken is uphill.
 *   <li><strong>The keep.</strong> Four guns round ground you take and then hold, and the waves that
 *       come in from the corners have ants among them. And at its east end, once every player is
 *       through the last gap, the boss grunt rises among six nests and a cache of every gun (G0048) -
 *       kept off the field until then, so pulling the view back from the brake shows an empty keep.
 * </ul>
 *
 * <p>Only the points are required, as in the first march, and the last one is held rather than taken.
 * The boss sits inside the keep's watch, so holding it means killing him.
 */
final class AdventureTwoScenario implements Scenario {

    private static final int COLS = 216;
    private static final int ROWS = 30;
    private static final float TILE = 5f;

    /** Three shading bands a biome, as in the first march. */
    private static final int REGIONS = 21;

    private static final int BAND = COLS / REGIONS;

    private static final int FIRST_ROW = 1;

    private static final int LAST_ROW = ROWS - 2;

    /** The wall columns, the same six as the first march: the length is the same, the route is not. */
    private static final int[] WALLS = {30, 60, 90, 120, 150, 180};

    /** Each wall's gap, as its first row. Different heights from the first march, so the route snakes elsewhere. */
    private static final int[] GAPS = {20, 5, 16, 3, 22, 12};

    private static final int GAP_HEIGHT = 4;

    private static final int KEEP_COL = 197;

    private static final int KEEP_ROW = 15;

    /** Past the keep's corners and short of the brake behind the wall. */
    private static final float KEEP_WATCH = 118f;

    private static final TileRect NORTH_WEST_GATE = new TileRect(183, 2, 4, 4);

    private static final TileRect NORTH_EAST_GATE = new TileRect(209, 2, 4, 4);

    private static final TileRect SOUTH_WEST_GATE = new TileRect(183, 23, 4, 4);

    private static final TileRect SOUTH_EAST_GATE = new TileRect(209, 23, 4, 4);

    private static final int SQUAD_SIZE = 5;

    /** The keep, wall to border: where every player has to be before the boss rises. */
    private static final TileRect LAIR = new TileRect(181, FIRST_ROW, 34, LAST_ROW);

    /** Where the boss sits: the keep's east end, a little further from the point than it notices anybody. */
    private static final int BOSS_COL = 210;

    private static final int BOSS_ROW = 15;

    /** The first column of each biome marched through, for scattering boulders inside it. */
    private static final int[] BIOME_COLS = {FIRST_ROW, 31, 61, 91, 121, 151};

    private static final int BIOME_WIDTH = 28;

    /** Fewer than the first march: getting round something needs room, and a boulder is not room. */
    private static final int BOULDERS_PER_BIOME = 5;

    private static final float BOULDER_CLEARANCE = 7f;

    private static final float BOULDER_WALL_MARGIN = 7f;

    /** A few medkits along the march (G0053), beside its points. */
    @Override
    public int medkits(SimConfig config) {
        return config.medkit().perLevel();
    }

    @Override
    public String name() {
        return "Adventure 2";
    }

    @Override
    public String description() {
        return "A second march east with ants in every biome but the first: get round them, get your men back,"
                + " and see what waits at the end of the keep.";
    }

    /** Nothing awake at the start, as in the first march. */
    @Override
    public SimConfig configure(SimConfig base) {
        SimConfig config = base.withMap(new SimConfig.MapConfig(COLS, ROWS, TILE, REGIONS));
        for (UnitKind kind : UnitKind.values()) {
            config = config.withUnit(
                    kind, strain -> strain.withPerception(strain.perception().withAlwaysAggressive(false)));
        }
        return config.withTrooper(config.trooper().withSquadSize(SQUAD_SIZE))
                .withSpawn(new SimConfig.SpawnConfig(2, 4, 8));
    }

    @Override
    public TileMap map(SimConfig config, Randomness randomness) {
        TileMap.Builder builder = TileMap.builder(COLS, ROWS, TILE);
        builder.fill(Terrain.PLAIN);

        builder.rect(31, FIRST_ROW, 29, LAST_ROW, Terrain.DUST);
        builder.rect(61, FIRST_ROW, 29, LAST_ROW, Terrain.ORGANIC);
        builder.rect(91, FIRST_ROW, 29, LAST_ROW, Terrain.MIRE);
        builder.rect(121, FIRST_ROW, 29, LAST_ROW, Terrain.DUST);
        builder.rect(151, FIRST_ROW, 29, LAST_ROW, Terrain.PLAIN);
        // The keep is burnt ground: somewhere that has been fought over already.
        builder.rect(181, FIRST_ROW, COLS - 181, LAST_ROW, Terrain.ASH);

        // Patches of a neighbouring ground, so no biome is one flat sheet.
        builder.rect(8, 5, 4, 3, Terrain.ASH);
        builder.rect(18, 22, 4, 3, Terrain.ASH);
        builder.rect(24, 9, 3, 3, Terrain.DUST);
        builder.rect(12, 14, 3, 3, Terrain.ASH);
        builder.rect(36, 20, 4, 3, Terrain.PLAIN);
        builder.rect(47, 6, 3, 3, Terrain.ORGANIC);
        builder.rect(54, 23, 3, 3, Terrain.ASH);
        builder.rect(66, 20, 4, 3, Terrain.DUST);
        builder.rect(80, 4, 3, 3, Terrain.MIRE);
        builder.rect(86, 23, 3, 3, Terrain.PLAIN);
        builder.rect(95, 4, 4, 3, Terrain.ORGANIC);
        builder.rect(111, 22, 3, 3, Terrain.DUST);
        builder.rect(126, 5, 3, 3, Terrain.ASH);
        builder.rect(140, 22, 4, 3, Terrain.PLAIN);
        builder.rect(156, 4, 3, 3, Terrain.ORGANIC);
        builder.rect(170, 23, 4, 3, Terrain.ORGANIC);
        builder.rect(162, 13, 3, 3, Terrain.DUST);
        builder.rect(190, 6, 4, 3, Terrain.DUST);
        builder.rect(205, 21, 4, 3, Terrain.DUST);
        builder.rect(210, 9, 3, 3, Terrain.PLAIN);

        // Reeds in the fen and undergrowth in the brake. The brake is the one biome where most of the
        // ground is thicket: he wades and they do not, so a man carried off into it is a long way off.
        builder.rect(100, 8, 6, 6, Terrain.THICKET);
        builder.rect(108, 17, 7, 6, Terrain.THICKET);
        builder.rect(96, 20, 4, 5, Terrain.THICKET);
        builder.rect(154, 8, 7, 6, Terrain.THICKET);
        builder.rect(163, 18, 7, 7, Terrain.THICKET);
        builder.rect(172, 5, 5, 8, Terrain.THICKET);
        builder.rect(158, 22, 4, 5, Terrain.THICKET);
        paintRegions(builder);

        builder.border(1, Terrain.ROCK);
        for (int i = 0; i < WALLS.length; i++) {
            wall(builder, WALLS[i], GAPS[i]);
        }
        rock(builder);
        return builder.build();
    }

    /** One rock column with a hole in it, built as its two solid pieces. */
    private static void wall(TileMap.Builder builder, int col, int gapRow) {
        builder.rect(col, FIRST_ROW, 1, gapRow - FIRST_ROW, Terrain.ROCK);
        int below = gapRow + GAP_HEIGHT;
        builder.rect(col, below, 1, LAST_ROW + 1 - below, Terrain.ROCK);
    }

    /**
     * The outcrops. Light where the lesson is getting round an ant - the sand and the fen - and heavy
     * in the cut, where it is what you do when you cannot.
     */
    private static void rock(TileMap.Builder builder) {
        // The scrub.
        builder.rect(16, 6, 1, 2, Terrain.ROCK);
        builder.rect(22, 15, 2, 1, Terrain.ROCK);
        builder.rect(10, 24, 1, 1, Terrain.ROCK);

        // The sand: low and scattered. Open ground is what makes walking round something possible.
        builder.rect(38, 8, 2, 3, Terrain.ROCK);
        builder.rect(46, 15, 1, 3, Terrain.ROCK);
        builder.rect(52, 24, 3, 1, Terrain.ROCK);
        builder.rect(56, 10, 1, 2, Terrain.ROCK);

        // The anthill: enough to break a line, not enough to hide behind for long.
        builder.rect(68, 8, 2, 5, Terrain.ROCK);
        builder.rect(70, 8, 1, 2, Terrain.ROCK);
        builder.rect(76, 17, 3, 2, Terrain.ROCK);
        builder.rect(83, 11, 1, 3, Terrain.ROCK);
        builder.rect(73, 25, 2, 1, Terrain.ROCK);

        // The fen: almost nothing. The ground is the problem.
        builder.rect(104, 15, 2, 1, Terrain.ROCK);
        builder.rect(115, 6, 1, 3, Terrain.ROCK);

        // The cut: corridors. There is no getting round anything here.
        builder.rect(124, 9, 2, 8, Terrain.ROCK);
        builder.rect(126, 15, 2, 2, Terrain.ROCK);
        builder.rect(131, 19, 3, 6, Terrain.ROCK);
        builder.rect(134, 3, 2, 7, Terrain.ROCK);
        builder.rect(136, 8, 1, 3, Terrain.ROCK);
        builder.rect(140, 12, 3, 6, Terrain.ROCK);
        builder.rect(145, 4, 2, 6, Terrain.ROCK);
        builder.rect(145, 21, 2, 5, Terrain.ROCK);
        builder.rect(128, 3, 1, 2, Terrain.ROCK);

        // The brake.
        builder.rect(166, 11, 2, 4, Terrain.ROCK);
        builder.rect(177, 19, 1, 3, Terrain.ROCK);

        // The keep: four short pieces, somewhere to put your back.
        builder.rect(188, 12, 2, 3, Terrain.ROCK);
        builder.rect(205, 16, 2, 3, Terrain.ROCK);
        builder.rect(196, 5, 3, 1, Terrain.ROCK);
        builder.rect(197, 24, 3, 1, Terrain.ROCK);
    }

    private static void paintRegions(TileMap.Builder builder) {
        for (int col = 0; col < COLS; col++) {
            int region = Math.min(REGIONS - 1, col / BAND);
            for (int row = 0; row < ROWS; row++) {
                builder.region(col, row, region);
            }
        }
    }

    @Override
    public void populate(Simulation simulation) {
        TileMap map = simulation.map();

        // Points first, west to east.
        point(simulation, map, 10, 20);
        point(simulation, map, 24, 6);
        point(simulation, map, 40, 5);
        point(simulation, map, 53, 17);
        point(simulation, map, 66, 24);
        point(simulation, map, 84, 7);
        point(simulation, map, 97, 16);
        point(simulation, map, 113, 11);
        point(simulation, map, 129, 23);
        point(simulation, map, 143, 8);
        point(simulation, map, 157, 18);
        point(simulation, map, 173, 16);
        simulation
                .spawnControlPoint(map.tileCentreX(KEEP_COL), map.tileCentreY(KEEP_ROW))
                .getComponent(ControlPoint.class)
                .holdAfterwards(KEEP_WATCH);

        // Nests before boulders, which reserve everything already standing. None in the scrub - it has
        // no ants and asks for nothing new - and none in the keep, where what comes arrives on a schedule.
        nests(simulation, map, UnitKind.GRUNT, 26, 24, 44, 25);
        nests(simulation, map, UnitKind.SPITTER, 49, 11);
        nests(simulation, map, UnitKind.ANT, 78, 24, 88, 10, 118, 20, 147, 17, 169, 3);
        nests(simulation, map, UnitKind.FROG, 99, 26, 110, 4, 116, 15);
        nests(simulation, map, UnitKind.BRUTE, 138, 24, 122, 20);
        nests(simulation, map, UnitKind.RUNNER, 160, 26, 176, 9);

        deployPlayerSide(simulation);

        boulders(simulation, map);

        // THE SCRUB: grunts, runners and rabbits. Nothing within sight of the spawn, and no ants.
        units(simulation, map, UnitKind.GRUNT, 14, 10, 17, 19, 21, 12, 25, 16, 27, 4, 19, 26);
        units(simulation, map, UnitKind.RUNNER, 15, 4, 23, 25, 27, 13);
        rabbits(simulation, map, 6, 26, 13, 22, 20, 9, 26, 20);

        // THE SAND: spitters, and the first ants - three of them, far apart, on open ground.
        units(simulation, map, UnitKind.SPITTER, 35, 12, 42, 24, 48, 3, 55, 16, 58, 6);
        units(simulation, map, UnitKind.ANT, 44, 11, 51, 21, 57, 26);
        rabbits(simulation, map, 37, 17);

        // THE ANTHILL: ants in numbers, with grunts and food among them.
        units(simulation, map, UnitKind.ANT, 63, 5, 67, 15, 72, 22, 79, 11, 82, 19, 86, 4, 88, 26);
        units(simulation, map, UnitKind.GRUNT, 64, 14, 71, 4, 87, 18, 74, 13);
        pickups(simulation, map, PickupKind.MUSHROOM, 65, 10, 71, 26, 77, 6, 85, 14, 69, 19, 81, 25);
        pickups(simulation, map, PickupKind.FLOWER, 75, 12, 87, 21);
        rabbits(simulation, map, 62, 26);
        // The first caches. The shotgun is an answer to being close, which is where an ant's back is.
        pickups(simulation, map, PickupKind.ARMOURY_SHOTGUN, 72, 10);
        pickups(simulation, map, PickupKind.ARMOURY_FLAMER, 83, 26);

        // THE FEN: frogs and ants, in the reeds.
        units(simulation, map, UnitKind.FROG, 93, 6, 98, 13, 103, 20, 106, 11, 111, 26, 114, 18, 117, 8, 116, 11);
        units(simulation, map, UnitKind.ANT, 94, 10, 102, 25, 107, 4, 112, 15, 118, 26);
        pickups(simulation, map, PickupKind.ARMOURY_RAILGUN, 99, 23);
        pickups(simulation, map, PickupKind.ARMOURY_LASER, 115, 23);

        // THE CUT: brutes, spitters and ants among heavy rock, and barrels - the answer to an ant in a
        // corridor you cannot get round it in.
        units(simulation, map, UnitKind.BRUTE, 122, 14, 129, 6, 135, 23, 142, 20, 148, 8);
        units(simulation, map, UnitKind.SPITTER, 123, 4, 137, 20, 148, 12, 133, 13);
        units(simulation, map, UnitKind.ANT, 127, 26, 139, 4, 144, 16, 148, 26);
        units(simulation, map, UnitKind.GRUNT, 128, 12);
        barrels(simulation, map, 125, 21, 132, 7, 138, 17, 148, 22, 144, 13);
        pickups(simulation, map, PickupKind.ARMOURY_SHOTGUN, 126, 24);
        pickups(simulation, map, PickupKind.ARMOURY_RAILGUN, 137, 6);

        // THE BRAKE: runners and ants in the undergrowth, and a brute.
        units(
                simulation,
                map,
                UnitKind.RUNNER,
                152,
                6,
                155,
                15,
                159,
                11,
                165,
                25,
                168,
                7,
                171,
                18,
                175,
                26,
                178,
                13,
                161,
                3);
        units(simulation, map, UnitKind.ANT, 153, 24, 164, 5, 170, 12, 177, 25);
        units(simulation, map, UnitKind.BRUTE, 174, 22);
        barrels(simulation, map, 158, 15, 167, 21);
        mines(simulation, map, 152, 19, 166, 4, 175, 12);
        pickups(simulation, map, PickupKind.ARMOURY_FLAMER, 165, 9);
        pickups(simulation, map, PickupKind.ARMOURY_LASER, 171, 26);

        keep(simulation, map);

        Level level = simulation.level();
        level.require(Objective.CAPTURE_POINTS);
        level.require(Objective.HOLD_POINT);
    }

    /**
     * The last biome: four guns round the ground you take, mines across the approaches, two ants asleep
     * in the far corners, and a schedule held back until the point is taken - with ants in it.
     */
    private static void keep(Simulation simulation, TileMap map) {
        turret(simulation, map, KEEP_COL - 5, KEEP_ROW - 5);
        turret(simulation, map, KEEP_COL + 5, KEEP_ROW - 5);
        turret(simulation, map, KEEP_COL - 5, KEEP_ROW + 5);
        turret(simulation, map, KEEP_COL + 5, KEEP_ROW + 5);
        mines(simulation, map, 188, 7, 191, 9, 206, 7, 203, 9, 188, 23, 191, 21, 206, 23, 203, 21, 193, 15, 201, 15);
        // No east barrel: that is where the boss rises, and his first ring would set it off in his own face.
        barrels(simulation, map, 192, 12, 202, 12, 192, 18, 202, 18, 185, 15);
        units(simulation, map, UnitKind.ANT, 184, 14, 212, 14);
        pickups(simulation, map, PickupKind.ARMOURY_RAILGUN, 194, 15);
        pickups(simulation, map, PickupKind.ARMOURY_LASER, 200, 15);

        // Ants among every other kind, from alternating corners. An ant in a wave is an ant that takes
        // a man off a gun, which is the keep's version of the lesson.
        simulation
                .waves()
                .holdBack()
                .add(6f, UnitKind.GRUNT, 10, NORTH_WEST_GATE)
                .add(5f, UnitKind.ANT, 3, SOUTH_EAST_GATE)
                .add(4f, UnitKind.RUNNER, 8, NORTH_EAST_GATE)
                .add(7f, UnitKind.SPITTER, 5, SOUTH_WEST_GATE)
                .add(5f, UnitKind.ANT, 4, NORTH_WEST_GATE)
                .add(8f, UnitKind.BRUTE, 4, NORTH_EAST_GATE)
                .add(5f, UnitKind.FROG, 6, SOUTH_EAST_GATE)
                .add(4f, UnitKind.RUNNER, 10, SOUTH_WEST_GATE)
                .add(6f, UnitKind.ANT, 5, NORTH_EAST_GATE)
                .add(8f, UnitKind.GRUNT, 12, SOUTH_EAST_GATE)
                .add(6f, UnitKind.ANT, 6, SOUTH_WEST_GATE);

        lair(simulation, map);
    }

    /**
     * The end of the march (G0048): the boss grunt at the keep's east end, ringed by a nest of every
     * strain that walks and a cache of every gun - kept off the field until every player is inside the
     * keep.
     */
    private static void lair(Simulation simulation, TileMap map) {
        Lair lair = simulation.lair().at(LAIR);
        // Holes first, as with everything placed by hand: the renderer draws in spawn order, and a nest
        // is ground rather than a thing standing on it.
        lairNest(lair, map, UnitKind.GRUNT, BOSS_COL, BOSS_ROW - 6);
        lairNest(lair, map, UnitKind.SPITTER, BOSS_COL - 4, BOSS_ROW - 3);
        lairNest(lair, map, UnitKind.RUNNER, BOSS_COL + 3, BOSS_ROW - 3);
        lairNest(lair, map, UnitKind.FROG, BOSS_COL - 3, BOSS_ROW + 5);
        lairNest(lair, map, UnitKind.ANT, BOSS_COL + 3, BOSS_ROW + 3);
        lairNest(lair, map, UnitKind.BRUTE, BOSS_COL, BOSS_ROW + 6);
        // A cache of every gun, inside his reach: taking one means stepping into the patterns. Three in
        // an arc on the near side, and the flamer behind him - the close gun, for whoever gets round.
        lairPickup(lair, map, PickupKind.ARMOURY_SHOTGUN, BOSS_COL - 6, BOSS_ROW - 3);
        lairPickup(lair, map, PickupKind.ARMOURY_RAILGUN, BOSS_COL - 6, BOSS_ROW);
        lairPickup(lair, map, PickupKind.ARMOURY_LASER, BOSS_COL - 7, BOSS_ROW + 3);
        lairPickup(lair, map, PickupKind.ARMOURY_FLAMER, BOSS_COL + 3, BOSS_ROW);
        lair.unit(UnitKind.BOSS_GRUNT, map.tileCentreX(BOSS_COL), map.tileCentreY(BOSS_ROW));
    }

    private static void lairPickup(Lair lair, TileMap map, PickupKind kind, int col, int row) {
        lair.pickup(kind, map.tileCentreX(col), map.tileCentreY(row));
    }

    private static void lairNest(Lair lair, TileMap map, UnitKind kind, int col, int row) {
        lair.nest(kind, map.tileCentreX(col), map.tileCentreY(row));
    }

    /** Boulders, scattered from the simulation's own stream, off rock and off everything already standing. */
    private static void boulders(Simulation simulation, TileMap map) {
        float radius = simulation.config().obstacle().radius();
        Scatter scatter = new Scatter(map, simulation.rng(), BOULDER_CLEARANCE, BOULDER_WALL_MARGIN)
                .keepingOffRock(BOULDER_WALL_MARGIN);
        for (Entity standing : simulation
                .engine()
                .getEntitiesFor(Family.all(Position.class, Body.class).get())) {
            Position where = standing.getComponent(Position.class);
            scatter.reserve(where.x, where.y, standing.getComponent(Body.class).radius);
        }
        for (int from : BIOME_COLS) {
            scatter.scatter(
                    new TileRect(from, FIRST_ROW, BIOME_WIDTH, LAST_ROW),
                    BOULDERS_PER_BIOME,
                    radius,
                    simulation::spawnObstacle);
        }
    }

    private static void nests(Simulation simulation, TileMap map, UnitKind kind, int... colsAndRows) {
        for (int i = 0; i < colsAndRows.length; i += 2) {
            simulation.spawnNest(kind, map.tileCentreX(colsAndRows[i]), map.tileCentreY(colsAndRows[i + 1]));
        }
    }

    private static void point(Simulation simulation, TileMap map, int col, int row) {
        simulation.spawnControlPoint(map.tileCentreX(col), map.tileCentreY(row));
    }

    private static void units(Simulation simulation, TileMap map, UnitKind kind, int... colsAndRows) {
        for (int i = 0; i < colsAndRows.length; i += 2) {
            simulation.spawnUnit(kind, map.tileCentreX(colsAndRows[i]), map.tileCentreY(colsAndRows[i + 1]));
        }
    }

    private static void rabbits(Simulation simulation, TileMap map, int... colsAndRows) {
        for (int i = 0; i < colsAndRows.length; i += 2) {
            simulation.spawnRabbit(map.tileCentreX(colsAndRows[i]), map.tileCentreY(colsAndRows[i + 1]));
        }
    }

    private static void mines(Simulation simulation, TileMap map, int... colsAndRows) {
        for (int i = 0; i < colsAndRows.length; i += 2) {
            simulation.spawnMine(map.tileCentreX(colsAndRows[i]), map.tileCentreY(colsAndRows[i + 1]));
        }
    }

    private static void turret(Simulation simulation, TileMap map, int col, int row) {
        simulation.spawnTurret(map.tileCentreX(col), map.tileCentreY(row));
    }

    private static void barrels(Simulation simulation, TileMap map, int... colsAndRows) {
        for (int i = 0; i < colsAndRows.length; i += 2) {
            simulation.spawnBarrel(map.tileCentreX(colsAndRows[i]), map.tileCentreY(colsAndRows[i + 1]));
        }
    }

    private static void pickups(Simulation simulation, TileMap map, PickupKind kind, int... colsAndRows) {
        for (int i = 0; i < colsAndRows.length; i += 2) {
            simulation.spawnPickup(kind, map.tileCentreX(colsAndRows[i]), map.tileCentreY(colsAndRows[i + 1]));
        }
    }
}
