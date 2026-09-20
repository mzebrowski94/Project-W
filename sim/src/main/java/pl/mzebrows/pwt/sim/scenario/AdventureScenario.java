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
import pl.mzebrows.pwt.sim.component.Level;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.def.Objective;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * A march east through six biomes, two control points in each, for up to four players.
 *
 * <p>The long level: nine hundred world units across, which is about six screens, and the first
 * scenario that is a <em>level</em> rather than an arena built to show one system. What it is for is
 * everything that only goes wrong at length - the squad's pace, the shared camera, the leash, and
 * whether co-op holds together when the players want to be in two places.
 *
 * <p><strong>Each biome teaches one thing and the last asks for all of it.</strong> The walk east is
 * one way: a rock wall divides each biome from the next and the only way through is a gap, put at a
 * different height each time so the route snakes rather than running down the middle.
 *
 * <ul>
 *   <li><strong>The plain.</strong> Open ground, a handful of grunts and rabbits to deny. Nothing
 *       here is a problem; it is where the squad's advance and the order keys become muscle memory.
 *   <li><strong>The dust.</strong> Short rock walls and the first spitters, so cover starts mattering
 *       - a trooper only engages through open ground, and so does what is shooting at it.
 *   <li><strong>The growth.</strong> Organic ground, runners, and food lying about. Leave the
 *       mushrooms and a grunt becomes a brute; leave a flower and something becomes worse than that.
 *       The biome that punishes walking past things.
 *   <li><strong>The quarry.</strong> Heavy rock, brutes, spitters behind them and barrels among them.
 *       Everything the first three taught, in the least room.
 *   <li><strong>The mire.</strong> Standing water, and frogs. Nothing here chases you far - back off
 *       and one loses you - but what they throw ruins the ground, so the biome is about where you
 *       are standing rather than about what is coming at you. The one that teaches you to read the
 *       floor.
 *   <li><strong>The ashes.</strong> Burnt ground, and everything at once: brutes in the way,
 *       spitters behind them, runners through the gaps and frogs ruining the ground under all of it.
 *       The last biome asks for all five.
 * </ul>
 *
 * <p><strong>The squad holds every point it takes</strong> and waits to be told to push on, so the
 * level advances at the commander's pace. An order reaches only what is within earshot (G0017), so
 * pushing on means walking up to the men holding the point - which is the whole loop this level is
 * built around, and the reason it is eight points long rather than two.
 *
 * <p>Only the points are required. Clearing a level this size of everything alive is a different
 * game, and a slower one.
 *
 * <p>Fixes its own map size, because the four bands and the three gaps <em>are</em> the level.
 */
final class AdventureScenario implements Scenario {

    private static final int COLS = 180;
    private static final int ROWS = 30;
    private static final float TILE = 5f;

    /**
     * Three bands per biome rather than one.
     *
     * <p>A region is shaded slightly apart from its neighbours, so one region per biome makes each
     * biome a flat sheet of its own colour - four screens of that reads as a spreadsheet. Three means
     * the ground changes under you as you march as well as when you cross a wall.
     */
    private static final int REGIONS = 18;

    /** How many columns one shading band covers. */
    private static final int BAND = COLS / REGIONS;

    /** The walkable rows inside the border. */
    private static final int FIRST_ROW = 1;

    private static final int LAST_ROW = ROWS - 2;

    /** Where one biome ends and the next begins: the wall columns. */
    private static final int WALL_ONE = 30;

    private static final int WALL_TWO = 60;
    private static final int WALL_THREE = 90;
    private static final int WALL_FOUR = 120;
    private static final int WALL_FIVE = 150;

    /** Each wall's gap: first row, and how many rows tall. Different heights, so the route snakes. */
    private static final int GAP_ONE_ROW = 3;

    private static final int GAP_TWO_ROW = 22;
    private static final int GAP_THREE_ROW = 13;
    private static final int GAP_FOUR_ROW = 24;
    private static final int GAP_FIVE_ROW = 8;
    private static final int GAP_HEIGHT = 4;

    private static final int SQUAD_SIZE = 5;

    /** The first column of each biome, and how wide one is, for scattering inside it. */
    private static final int[] BIOME_COLS = {
        FIRST_ROW, WALL_ONE + 1, WALL_TWO + 1, WALL_THREE + 1, WALL_FOUR + 1, WALL_FIVE + 1
    };

    private static final int BIOME_WIDTH = WALL_ONE - 2;

    private static final int BOULDERS_PER_BIOME = 7;

    /** Gap left round a boulder, and how far one stays off a wall - wide enough for a brute to pass. */
    private static final float BOULDER_CLEARANCE = 7f;

    private static final float BOULDER_WALL_MARGIN = 7f;

    @Override
    public String name() {
        return "Adventure";
    }

    @Override
    public String description() {
        return "Six biomes east, two points in each, and a squad that holds what it takes until you say forward.";
    }

    /**
     * Nothing is awake at the start. A level this long with every alien aggressive from the first
     * tick is not four biomes, it is one fight that begins at the spawn area and never stops.
     */
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

        // The four bands. Painted as whole columns and then walled, so a biome is a stretch of the
        // march rather than a blob - which is what makes "the dust" a place a player can be told to
        // meet somebody in.
        builder.rect(WALL_ONE, FIRST_ROW, WALL_TWO - WALL_ONE, LAST_ROW, Terrain.DUST);
        builder.rect(WALL_TWO, FIRST_ROW, WALL_THREE - WALL_TWO, LAST_ROW, Terrain.ORGANIC);
        builder.rect(WALL_THREE, FIRST_ROW, WALL_FOUR - WALL_THREE, LAST_ROW, Terrain.DUST);
        builder.rect(WALL_FOUR, FIRST_ROW, WALL_FIVE - WALL_FOUR, LAST_ROW, Terrain.MIRE);
        builder.rect(WALL_FIVE, FIRST_ROW, COLS - WALL_FIVE, LAST_ROW, Terrain.ASH);

        // Patches of a neighbouring ground inside each biome. They cost nothing - every one of them
        // is walkable and none of them changes a route - and they are most of what stops a biome
        // reading as one flat rectangle with things standing on it.
        builder.rect(6, 15, 4, 3, Terrain.DUST);
        builder.rect(20, 8, 3, 4, Terrain.DUST);
        builder.rect(14, 21, 3, 3, Terrain.ORGANIC);
        builder.rect(33, 12, 3, 3, Terrain.ORGANIC);
        builder.rect(48, 22, 4, 3, Terrain.ORGANIC);
        builder.rect(41, 4, 3, 3, Terrain.PLAIN);
        builder.rect(63, 3, 4, 3, Terrain.DUST);
        builder.rect(79, 12, 3, 4, Terrain.DUST);
        builder.rect(72, 24, 4, 3, Terrain.PLAIN);
        builder.rect(92, 15, 3, 3, Terrain.ORGANIC);
        builder.rect(115, 3, 3, 4, Terrain.PLAIN);
        builder.rect(100, 2, 3, 3, Terrain.PLAIN);
        builder.rect(126, 19, 4, 3, Terrain.ORGANIC);
        builder.rect(137, 4, 3, 3, Terrain.DUST);
        builder.rect(145, 24, 3, 3, Terrain.ORGANIC);
        builder.rect(133, 12, 4, 4, Terrain.MIRE);
        builder.rect(156, 6, 3, 3, Terrain.DUST);
        builder.rect(167, 21, 4, 3, Terrain.MIRE);
        builder.rect(175, 11, 3, 4, Terrain.DUST);
        paintRegions(builder);

        builder.border(1, Terrain.ROCK);
        wall(builder, WALL_ONE, GAP_ONE_ROW);
        wall(builder, WALL_TWO, GAP_TWO_ROW);
        wall(builder, WALL_THREE, GAP_THREE_ROW);
        wall(builder, WALL_FOUR, GAP_FOUR_ROW);
        wall(builder, WALL_FIVE, GAP_FIVE_ROW);
        rock(builder);
        return builder.build();
    }

    /**
     * One rock column with a hole in it. Built as the two solid pieces rather than as a wall with the
     * gap carved back out, so a gap can never be re-filled by a later coat of paint.
     */
    private static void wall(TileMap.Builder builder, int col, int gapRow) {
        builder.rect(col, FIRST_ROW, 1, gapRow - FIRST_ROW, Terrain.ROCK);
        int below = gapRow + GAP_HEIGHT;
        builder.rect(col, below, 1, LAST_ROW + 1 - below, Terrain.ROCK);
    }

    /**
     * Every outcrop, in clusters rather than in lines.
     *
     * <p>A one-tile wall is a fence and reads as one; two or three tiles bent round a corner reads as
     * rock, gives a spitter something to sit behind and a squad something to come round. Nothing here
     * is wider than three tiles or taller than nine, so no cluster can seal a biome - the walls are
     * the only thing that divides one from the next, and they have their gaps.
     */
    private static void rock(TileMap.Builder builder) {
        // The plain: almost nothing, because it is the biome where nothing is a problem yet.
        builder.rect(17, 5, 1, 2, Terrain.ROCK);
        builder.rect(13, 23, 2, 1, Terrain.ROCK);
        builder.rect(24, 14, 1, 1, Terrain.ROCK);

        // The dust: cover, and the first biome where where you stand matters.
        builder.rect(36, 9, 2, 6, Terrain.ROCK);
        builder.rect(38, 9, 1, 3, Terrain.ROCK);
        builder.rect(45, 3, 3, 2, Terrain.ROCK);
        builder.rect(45, 5, 1, 4, Terrain.ROCK);
        builder.rect(52, 17, 2, 7, Terrain.ROCK);
        builder.rect(50, 21, 2, 2, Terrain.ROCK);
        builder.rect(41, 14, 1, 1, Terrain.ROCK);
        builder.rect(57, 11, 1, 2, Terrain.ROCK);

        // The growth: open, so a runner's line is the problem rather than the ground.
        builder.rect(70, 13, 2, 4, Terrain.ROCK);
        builder.rect(66, 4, 1, 1, Terrain.ROCK);
        builder.rect(76, 25, 2, 1, Terrain.ROCK);
        builder.rect(85, 8, 1, 2, Terrain.ROCK);

        // The quarry: everything the first three taught, in the least room.
        builder.rect(95, 4, 2, 9, Terrain.ROCK);
        builder.rect(97, 10, 2, 3, Terrain.ROCK);
        builder.rect(103, 13, 3, 8, Terrain.ROCK);
        builder.rect(101, 19, 2, 2, Terrain.ROCK);
        builder.rect(110, 2, 2, 7, Terrain.ROCK);
        builder.rect(112, 7, 1, 4, Terrain.ROCK);
        builder.rect(93, 22, 1, 2, Terrain.ROCK);
        builder.rect(107, 4, 2, 1, Terrain.ROCK);
        builder.rect(116, 19, 1, 3, Terrain.ROCK);

        // The mire: open. What makes the ground hard to cross here is what is lying on it, not what
        // is built out of it, so rock would only give the player somewhere safe to stand.
        builder.rect(127, 11, 2, 3, Terrain.ROCK);
        builder.rect(139, 20, 1, 2, Terrain.ROCK);
        builder.rect(146, 5, 2, 1, Terrain.ROCK);

        // The ashes: cover again, and more of it than anywhere, because everything the march taught
        // has to be usable at once in the least room.
        builder.rect(155, 13, 2, 7, Terrain.ROCK);
        builder.rect(157, 16, 2, 2, Terrain.ROCK);
        builder.rect(162, 3, 3, 2, Terrain.ROCK);
        builder.rect(164, 5, 1, 4, Terrain.ROCK);
        builder.rect(169, 14, 2, 6, Terrain.ROCK);
        builder.rect(167, 24, 2, 2, Terrain.ROCK);
        builder.rect(174, 4, 2, 5, Terrain.ROCK);
        builder.rect(176, 18, 1, 3, Terrain.ROCK);
    }

    /** Bands of shading along the march, three to a biome, so the ground changes under you as you go. */
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

        // Points first: the renderer draws in spawn order, and a point is ground to stand on rather
        // than a thing standing on the ground. Spawn order is west to east, which is also the order
        // a squad measuring by route will take them in.
        point(simulation, map, 9, 7);
        point(simulation, map, 23, 22);
        point(simulation, map, 40, 20);
        point(simulation, map, 53, 7);
        point(simulation, map, 69, 8);
        point(simulation, map, 83, 22);
        point(simulation, map, 99, 21);
        point(simulation, map, 114, 9);
        point(simulation, map, 128, 6);
        point(simulation, map, 142, 23);
        point(simulation, map, 158, 20);
        point(simulation, map, 172, 9);

        // Nests before boulders, and boulders reserve everything already standing: a nest is
        // immovable and so is a boulder, so two of them in the same place is a knot the solver
        // cannot untie. Points get the same protection out of the same call, which they never had.
        //
        // ONE MORE IN EACH BIOME THAN THE LAST. The march gets harder because the ground is filling
        // in behind you faster, not because the numbers on the creatures went up - and every one of
        // them sleeps until somebody comes near, so what the player meets is a nest rather than an
        // hour of its output. The strain is whatever that biome teaches.
        nests(simulation, map, UnitKind.GRUNT, 25, 5);
        nests(simulation, map, UnitKind.GRUNT, 34, 25);
        nests(simulation, map, UnitKind.SPITTER, 48, 11);
        nests(simulation, map, UnitKind.RUNNER, 64, 6, 80, 24);
        nests(simulation, map, UnitKind.GRUNT, 88, 14);
        nests(simulation, map, UnitKind.BRUTE, 92, 16, 117, 12);
        nests(simulation, map, UnitKind.SPITTER, 100, 6);
        nests(simulation, map, UnitKind.GRUNT, 108, 20);
        nests(simulation, map, UnitKind.FROG, 124, 14, 133, 26, 140, 4, 147, 17);
        nests(simulation, map, UnitKind.GRUNT, 136, 9);
        nests(simulation, map, UnitKind.FROG, 153, 7, 161, 22, 176, 14);
        nests(simulation, map, UnitKind.BRUTE, 165, 10, 171, 26);
        nests(simulation, map, UnitKind.RUNNER, 158, 3);

        deployPlayerSide(simulation);

        boulders(simulation, map);

        // The plain: enough grunts to be a fight, and rabbits to deny before something eats them.
        grunts(simulation, map, 6, 6, 10, 14, 12, 9, 15, 18, 19, 12, 21, 25, 23, 7, 26, 20, 27, 4, 28, 26);
        rabbits(simulation, map, 7, 26, 8, 22, 16, 26, 22, 16, 27, 10);

        // The dust: the same grunts in greater numbers, and the first things that shoot back.
        grunts(
                simulation,
                map,
                32,
                6,
                34,
                17,
                35,
                25,
                39,
                4,
                40,
                10,
                42,
                20,
                44,
                13,
                47,
                8,
                49,
                26,
                51,
                5,
                55,
                15,
                58,
                22);
        units(simulation, map, UnitKind.SPITTER, 33, 10, 38, 23, 43, 6, 48, 16, 54, 10, 58, 4);

        // The growth: runners in numbers, and food that turns whatever reaches it into a worse problem.
        units(
                simulation,
                map,
                UnitKind.RUNNER,
                62,
                7,
                64,
                18,
                66,
                26,
                68,
                4,
                72,
                10,
                73,
                21,
                75,
                6,
                77,
                15,
                79,
                25,
                82,
                9,
                84,
                17,
                88,
                5);
        grunts(simulation, map, 63, 13, 74, 3, 81, 26, 87, 20);
        pickups(
                simulation,
                map,
                PickupKind.MUSHROOM,
                65,
                12,
                71,
                22,
                78,
                8,
                86,
                15,
                69,
                26,
                62,
                15,
                74,
                5,
                81,
                18,
                88,
                24,
                73,
                27,
                67,
                19);
        pickups(simulation, map, PickupKind.FLOWER, 67, 9, 84, 21, 72, 13, 79, 26, 89, 6);
        rabbits(simulation, map, 62, 24, 80, 11);
        // The first caches, and nothing before them. The plain and the dust are what a sidearm is
        // tuned for, and a better gun handed out in either would be a shortcut past the half of the
        // march that teaches you to aim. They are contested like anything else - a runner that
        // reaches one first eats it - so a gun this far east is something to get to rather than
        // something to collect.
        // THREE KINDS OF CRATE, one per gun, and the growth is where he meets all of them. Each is
        // the colour of what is in it, so which one is worth the walk is a decision rather than a
        // surprise (pillar 5). The shotgun and the flamer are both answers to being close, which is
        // exactly the problem runners set - and a railgun among them is the other half of the choice.
        pickups(simulation, map, PickupKind.ARMOURY_SHOTGUN, 76, 20);
        pickups(simulation, map, PickupKind.ARMOURY_FLAMER, 68, 22);
        pickups(simulation, map, PickupKind.ARMOURY_RAILGUN, 87, 11);

        // The quarry: brutes in the way, spitters behind them, and barrels among them.
        units(simulation, map, UnitKind.BRUTE, 92, 7, 94, 18, 99, 3, 100, 25, 106, 22, 108, 9, 113, 15, 117, 5);
        units(simulation, map, UnitKind.SPITTER, 92, 12, 97, 21, 99, 8, 102, 4, 106, 15, 109, 26, 114, 20, 118, 11);
        grunts(simulation, map, 93, 4, 98, 25, 101, 10, 107, 17, 112, 24, 115, 13);
        barrels(simulation, map, 94, 14, 100, 17, 109, 12, 115, 25);
        // More of them here, because everything in the quarry outlives a sidearm and a gun lost with
        // a death has to be findable again.
        pickups(simulation, map, PickupKind.ARMOURY_FLAMER, 96, 16, 108, 12);
        pickups(simulation, map, PickupKind.ARMOURY_RAILGUN, 104, 6, 99, 24);
        pickups(simulation, map, PickupKind.ARMOURY_SHOTGUN, 115, 24);

        // THE MIRE: frogs, and almost nothing else. The biome that teaches them, so what it asks is
        // one question at a time - where their stuff has landed, and whether backing off has worked.
        // A handful of grunts so that standing still and reading the floor is not free.
        units(
                simulation,
                map,
                UnitKind.FROG,
                123,
                8,
                125,
                20,
                129,
                13,
                131,
                26,
                134,
                5,
                137,
                18,
                140,
                11,
                143,
                25,
                145,
                7,
                148,
                20);
        grunts(simulation, map, 127, 24, 135, 15, 144, 3, 149, 12);
        rabbits(simulation, map, 130, 4, 141, 27);
        // A gun in the mire, because the ashes are past it and a sidearm is not an answer to them.
        pickups(simulation, map, PickupKind.ARMOURY_SHOTGUN, 132, 21);

        // THE ASHES: everything at once, and frogs underneath all of it. What makes this biome the
        // last one is not that the creatures are worse but that the ground is also against you -
        // ruined floor between you and the brutes, and runners through whatever gap is left.
        units(simulation, map, UnitKind.FROG, 152, 12, 157, 25, 163, 6, 168, 18, 173, 26, 178, 8);
        units(simulation, map, UnitKind.BRUTE, 154, 4, 160, 15, 166, 27, 172, 13, 177, 24);
        units(simulation, map, UnitKind.SPITTER, 153, 19, 160, 9, 170, 5, 175, 24);
        units(simulation, map, UnitKind.RUNNER, 152, 3, 161, 27, 168, 11, 177, 15);
        grunts(simulation, map, 156, 22, 165, 17, 174, 3);
        barrels(simulation, map, 158, 12, 167, 8, 173, 22);
        pickups(simulation, map, PickupKind.ARMOURY_RAILGUN, 162, 15);
        pickups(simulation, map, PickupKind.ARMOURY_FLAMER, 171, 4);

        // Points only. Clearing a level this size of everything alive is a different game.
        Level level = simulation.level();
        level.require(Objective.CAPTURE_POINTS);
    }

    /**
     * Boulders, scattered rather than placed.
     *
     * <p>Round things among the rectangles, and the cheapest variety there is: rock is painted in
     * whole tiles and always will be, so everything that is not tile-shaped has to be an entity. They
     * are laid down before anything else so the scatter has the biome to itself; a grunt that starts
     * inside one is untangled by the solver on the first tick, as a crowd already is.
     *
     * <p>From the simulation's own stream, so the same seed lays the same field every time (pillar 6).
     */
    private static void boulders(Simulation simulation, TileMap map) {
        float radius = simulation.config().obstacle().radius();
        Scatter scatter = new Scatter(map, simulation.rng(), BOULDER_CLEARANCE, BOULDER_WALL_MARGIN);
        // Everything already standing is reserved first. A boulder is immovable and so are a nest
        // and a point, so one landing on either is a knot the solver cannot untie - and a boulder on
        // top of a point would be ground the squad is told to take and cannot stand on.
        for (Entity standing : simulation
                .engine()
                .getEntitiesFor(Family.all(Position.class, Body.class).get())) {
            Position where = standing.getComponent(Position.class);
            scatter.reserve(where.x, where.y, standing.getComponent(Body.class).radius);
        }
        for (int biome = 0; biome < BIOME_COLS.length; biome++) {
            int from = BIOME_COLS[biome];
            int to = BIOME_COLS[biome] + BIOME_WIDTH;
            scatter.scatter(
                    new TileRect(from, FIRST_ROW, to - from, LAST_ROW),
                    BOULDERS_PER_BIOME,
                    radius,
                    simulation::spawnObstacle);
        }
    }

    /** A run of col/row pairs, all of one strain, for the holes that keep sending more of it out. */
    private static void nests(Simulation simulation, TileMap map, UnitKind kind, int... colsAndRows) {
        for (int i = 0; i < colsAndRows.length; i += 2) {
            simulation.spawnNest(kind, map.tileCentreX(colsAndRows[i]), map.tileCentreY(colsAndRows[i + 1]));
        }
    }

    private static void point(Simulation simulation, TileMap map, int col, int row) {
        simulation.spawnControlPoint(map.tileCentreX(col), map.tileCentreY(row));
    }

    /** A run of col/row pairs, because a level is a list of places and reads better as one. */
    private static void units(Simulation simulation, TileMap map, UnitKind kind, int... colsAndRows) {
        for (int i = 0; i < colsAndRows.length; i += 2) {
            simulation.spawnUnit(kind, map.tileCentreX(colsAndRows[i]), map.tileCentreY(colsAndRows[i + 1]));
        }
    }

    private static void grunts(Simulation simulation, TileMap map, int... colsAndRows) {
        units(simulation, map, UnitKind.GRUNT, colsAndRows);
    }

    private static void rabbits(Simulation simulation, TileMap map, int... colsAndRows) {
        for (int i = 0; i < colsAndRows.length; i += 2) {
            simulation.spawnRabbit(map.tileCentreX(colsAndRows[i]), map.tileCentreY(colsAndRows[i + 1]));
        }
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
