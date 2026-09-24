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
import pl.mzebrows.pwt.sim.component.Level;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.def.Objective;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * A march east through seven biomes, two control points in each, and a stand at the end of it.
 *
 * <p>The long level: a thousand and eighty world units across, which is about seven screens, and the first
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
 *       The biome that punishes walking past things - and the first one with real <strong>thicket</strong>
 *       in it, which is ground he wades through and the things chasing him barely notice. Runners
 *       and undergrowth are the same lesson from both ends: you cannot outrun this, so stop trying.
 *   <li><strong>The quarry.</strong> Heavy rock, brutes, spitters behind them and barrels among them.
 *       Everything the first three taught, in the least room.
 *   <li><strong>The mire.</strong> Standing water, reeds and frogs. Nothing here chases you far -
 *       back off and one loses you - but what they throw ruins the ground, and the ground was
 *       already slow. The biome is about where you are standing rather than about what is coming at
 *       you, and it is where backing off stops being free. The one that teaches you to read the
 *       floor.
 *   <li><strong>The ashes.</strong> Burnt ground, and everything at once: brutes in the way,
 *       spitters behind them, runners through the gaps and frogs ruining the ground under all of it.
 *       The biome that asks for all five.
 *   <li><strong>The redoubt.</strong> A wide, bare square with ground to take in the middle of it and
 *       four guns standing round that ground. <strong>Taking it is what starts the fight</strong>:
 *       the level has been holding a wave schedule back all march, and the tick the bar fills a post
 *       goes down where you are standing and they start coming in from all four corners at once.
 *       Nothing here teaches anything new - it asks whether you can still do all of it with your back
 *       to a wall you chose.
 * </ul>
 *
 * <p><strong>The last biome is the only one you have to hold.</strong> Everywhere else a point taken
 * is a point taken; here the level is not done until the last wave has been seen in and the ground
 * around the post is clear, which is what the post's own watch measures - "nothing hostile standing"
 * cannot mean seven screens with nests still working four biomes back.
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

    private static final int COLS = 216;
    private static final int ROWS = 30;
    private static final float TILE = 5f;

    /**
     * Three bands per biome rather than one.
     *
     * <p>A region is shaded slightly apart from its neighbours, so one region per biome makes each
     * biome a flat sheet of its own colour - four screens of that reads as a spreadsheet. Three means
     * the ground changes under you as you march as well as when you cross a wall.
     */
    private static final int REGIONS = 21;

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
    private static final int WALL_SIX = 180;

    /** Each wall's gap: first row, and how many rows tall. Different heights, so the route snakes. */
    private static final int GAP_ONE_ROW = 3;

    private static final int GAP_TWO_ROW = 22;
    private static final int GAP_THREE_ROW = 13;
    private static final int GAP_FOUR_ROW = 24;
    private static final int GAP_FIVE_ROW = 8;
    private static final int GAP_SIX_ROW = 14;
    private static final int GAP_HEIGHT = 4;

    /** The middle of the redoubt: the ground you take, and then stand on. */
    private static final int REDOUBT_COL = 197;

    private static final int REDOUBT_ROW = 15;

    /**
     * How far around the post has to be clear before the level counts as held, in world units.
     *
     * <p>Comfortably past the far corners of the square - a creature standing in one of them is very
     * much still a problem - and comfortably short of the ashes on the other side of the wall, where
     * there are nests still working and a march is not a sweep.
     */
    private static final float REDOUBT_WATCH = 118f;

    /** The four mouths of the last biome. They come in from every corner at once. */
    private static final TileRect NORTH_WEST_GATE = new TileRect(183, 2, 4, 4);

    private static final TileRect NORTH_EAST_GATE = new TileRect(209, 2, 4, 4);

    private static final TileRect SOUTH_WEST_GATE = new TileRect(183, 23, 4, 4);

    private static final TileRect SOUTH_EAST_GATE = new TileRect(209, 23, 4, 4);

    private static final int SQUAD_SIZE = 5;

    /** The first column of each biome, and how wide one is, for scattering inside it. */
    private static final int[] BIOME_COLS = {
        FIRST_ROW, WALL_ONE + 1, WALL_TWO + 1, WALL_THREE + 1, WALL_FOUR + 1, WALL_FIVE + 1
    };

    private static final int BIOME_WIDTH = WALL_ONE - 2;

    private static final int BOULDERS_PER_BIOME = 7;

    /** Gap left round a boulder, and how far one stays off a wall or rock - wide enough for a brute to pass. */
    private static final float BOULDER_CLEARANCE = 7f;

    private static final float BOULDER_WALL_MARGIN = 7f;

    @Override
    public String name() {
        return "Adventure";
    }

    @Override
    public String description() {
        return "Seven biomes east, and a last one you take and then have to hold against what comes in behind you.";
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
        builder.rect(WALL_FIVE, FIRST_ROW, WALL_SIX - WALL_FIVE, LAST_ROW, Terrain.ASH);
        // The redoubt is bare dust rather than more ash: you have to be able to see across it, and it
        // has to read as somewhere else the moment you come through the last gap.
        builder.rect(WALL_SIX, FIRST_ROW, COLS - WALL_SIX, LAST_ROW, Terrain.DUST);

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
        builder.rect(188, 6, 4, 3, Terrain.ASH);
        builder.rect(204, 21, 4, 3, Terrain.ASH);
        builder.rect(196, 25, 3, 3, Terrain.ASH);
        builder.rect(210, 9, 3, 4, Terrain.PLAIN);
        thickets(builder);
        paintRegions(builder);

        builder.border(1, Terrain.ROCK);
        wall(builder, WALL_ONE, GAP_ONE_ROW);
        wall(builder, WALL_TWO, GAP_TWO_ROW);
        wall(builder, WALL_THREE, GAP_THREE_ROW);
        wall(builder, WALL_FOUR, GAP_FOUR_ROW);
        wall(builder, WALL_FIVE, GAP_FIVE_ROW);
        wall(builder, WALL_SIX, GAP_SIX_ROW);
        rock(builder);
        return builder.build();
    }

    /**
     * The undergrowth: ground he wades through and the things that live in it barely notice.
     *
     * <p>Painted before the walls and the outcrops rather than after, so rock always wins where the
     * two meet and a thicket can never be something that has quietly grown over a route. It cannot
     * close one off in any case - a thicket is passable ground and every flow field, every
     * reachability check and every spawn treats it as the open ground it is - so what it does to a
     * level is add a cost to a line rather than remove the line.
     *
     * <p><strong>It arrives in the dust as one patch and means nothing there</strong>, which is the
     * point: the biome whose job is teaching that where you stand matters is where he should first
     * walk into some and feel himself slow down, with nothing chasing him. It is worth something in
     * the growth, where runners are already the argument against trying to outrun anything; it is
     * worth a great deal in the mire, where backing off a frog is the whole answer to one and the
     * reeds are what make backing off cost something; and it is scattered through the ashes because
     * that biome asks for everything at once.
     *
     * <p>The quarry and the redoubt have none on purpose. The quarry's problem is rock and the room
     * it leaves, and the redoubt is ground you have to be able to see all of and move across freely
     * while it is being taken off you.
     */
    private static void thickets(TileMap.Builder builder) {
        // The dust: one patch, off the line between the points, and nothing in it.
        builder.rect(44, 16, 4, 5, Terrain.THICKET);

        // The growth: enough of it to change how the biome is crossed.
        builder.rect(63, 5, 6, 7, Terrain.THICKET);
        builder.rect(72, 17, 7, 6, Terrain.THICKET);
        builder.rect(82, 6, 5, 6, Terrain.THICKET);

        // The mire: reeds. Kept off the control point at 128,6 - a point is a thing the squad has to
        // stand still on, and one that also has to be waded to is two problems before he has met
        // either.
        builder.rect(124, 9, 6, 6, Terrain.THICKET);
        builder.rect(132, 17, 8, 7, Terrain.THICKET);
        builder.rect(142, 8, 6, 9, Terrain.THICKET);

        // The ashes: scrub coming back through the burnt ground, in patches rather than in bands.
        builder.rect(152, 20, 6, 6, Terrain.THICKET);
        builder.rect(160, 3, 6, 5, Terrain.THICKET);
        builder.rect(170, 19, 7, 6, Terrain.THICKET);
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

        // The redoubt: four short pieces, well off the middle and well off the corners. They are
        // there so a square this wide is not a billiard table and so there is somewhere to put your
        // back - not to break it into rooms, because what the last biome asks is whether you can hold
        // ground you can see all of.
        builder.rect(188, 12, 2, 3, Terrain.ROCK);
        builder.rect(205, 16, 2, 3, Terrain.ROCK);
        builder.rect(196, 5, 3, 1, Terrain.ROCK);
        builder.rect(197, 24, 3, 1, Terrain.ROCK);
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
        // The last one, and the only one that is more than a point taken: capturing it puts a post
        // down where it stands and lets go of the schedule the level has been holding back all march.
        simulation
                .spawnControlPoint(map.tileCentreX(REDOUBT_COL), map.tileCentreY(REDOUBT_ROW))
                .getComponent(ControlPoint.class)
                .holdAfterwards(REDOUBT_WATCH);

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
        nests(simulation, map, UnitKind.FROG, 153, 7, 161, 22, 172, 21);
        nests(simulation, map, UnitKind.BRUTE, 165, 10, 171, 26);
        nests(simulation, map, UnitKind.RUNNER, 158, 3);
        // None in the redoubt. What comes at the post arrives on a schedule and from the corners; a
        // hole in the middle of the ground you are holding would be a second fight nobody asked for.

        deployPlayerSide(simulation);

        boulders(simulation, map);

        // The plain: enough grunts to be a fight, and rabbits to deny before something eats them.
        //
        // HALF AS MANY AS IT HAD, AND NONE OF THEM WITHIN SIGHT OF THE SPAWN. The spawn area is
        // columns two to five, and this biome used to start at column six with ten grunts in it - so
        // a squad that had been on the field for two seconds was already in a fight it had not
        // chosen, on the one biome whose whole job is that nothing here is a problem yet. Nothing
        // now stands west of column fourteen, which is about a screen's worth of walking, and there
        // are five rather than ten because the first thing the march teaches is the order keys and
        // you cannot learn a key while you are being bitten.
        grunts(simulation, map, 14, 9, 17, 20, 21, 13, 24, 6, 27, 23);
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
        // The beam is deliberately NOT here: it is the fourth gun and it arrives two biomes later,
        // where there is finally something for a shot to go through.
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
        // THE FIRST BEAM, and the quarry is where it is first worth having. Everything here outlives
        // a sidearm, and the beam is the gun that answers a thing rather than a crowd: it holds one
        // brute at half pace while the squad works on it, and what it leaves behind is worth more
        // than what it killed. In the least room on the march, that is a real answer.
        pickups(simulation, map, PickupKind.ARMOURY_LASER, 111, 20);

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
        // And a beam, because the mire is where the ground stops being safe: what it buys here is a
        // frog dropped before it has closed, and a body left where it fell turned into something the
        // rest of them have to walk round.
        pickups(simulation, map, PickupKind.ARMOURY_LASER, 144, 16);

        // THE ASHES: everything at once, and frogs underneath all of it. What makes this biome the
        // last one is not that the creatures are worse but that the ground is also against you -
        // ruined floor between you and the brutes, and runners through whatever gap is left.
        units(simulation, map, UnitKind.FROG, 152, 12, 157, 25, 163, 6, 168, 18, 172, 26, 166, 8);
        units(simulation, map, UnitKind.BRUTE, 154, 4, 160, 15, 166, 27, 172, 11, 172, 24);
        units(simulation, map, UnitKind.SPITTER, 153, 19, 160, 9, 170, 5, 167, 22);
        units(simulation, map, UnitKind.RUNNER, 152, 3, 161, 27, 168, 11, 172, 16);
        grunts(simulation, map, 156, 22, 165, 17, 171, 3);
        // MORE OF THEM HERE THAN ANYWHERE. The ashes are the last biome you fight through rather than
        // in, and a barrel is the only thing in the game that answers a brute and three runners at
        // once - so the ground you are backing across has answers lying on it if you can reach one.
        // Since G0035 every one of them leaves the ground burning afterwards, which is the other half
        // of what makes setting one off a decision about where you are standing.
        barrels(simulation, map, 158, 12, 167, 8, 173, 22, 155, 26, 163, 20, 173, 16, 177, 6, 178, 27);
        // The first mines on the march, and deliberately only a handful: the ashes are where the
        // player learns what one is worth before the redoubt hands him a field of them. Nothing on
        // his side sets one off, so they are his to lay across whatever he is backing away from.
        mines(simulation, map, 159, 22, 164, 11, 166, 19, 173, 12);
        pickups(simulation, map, PickupKind.ARMOURY_RAILGUN, 162, 15);
        pickups(simulation, map, PickupKind.ARMOURY_FLAMER, 171, 4);

        redoubt(simulation, map);

        // Both, and the second is what the last biome is for. Points, because clearing a level this
        // size of everything alive is a different game and a slower one; and the post, because the
        // ground the march ends on is ground you are told to hold rather than ground you tick off.
        // The post does not exist until the last point is taken, so this cannot be met early.
        Level level = simulation.level();
        level.require(Objective.CAPTURE_POINTS);
        level.require(Objective.HOLD_POINT);
    }

    /**
     * The last biome: four guns round the ground you take, a minefield in front of the corners, and
     * a schedule that does not start until you take it.
     *
     * <p><strong>Everything here is laid before the fighting rather than during it</strong>, because
     * the whole of the decision is where you spend the quiet march up to the bar. The guns are far
     * enough apart that one man cannot work two of them and near enough the post that getting off one
     * and onto another is a run rather than a journey. The mines sit between the corners and the
     * middle, where everything that arrives has to cross - and nothing on the player's side sets one
     * off, so walking out to haul one somewhere better is a real thing to do with the time before the
     * first wave (G0021).
     *
     * <p>The waves come from all four corners, which is the difference between this and the Defend
     * arena: there is no side to face, so the squad has to be spread and the commander has to choose
     * which corner he is the answer to. They are held back until the point is taken, so the player
     * decides when the level's last fight starts.
     */
    private static void redoubt(Simulation simulation, TileMap map) {
        // Four guns round the middle, at the corners of a square rather than on the axes: a gun on
        // the axis covers one gate, and a gun on a diagonal covers two.
        turret(simulation, map, REDOUBT_COL - 5, REDOUBT_ROW - 5);
        turret(simulation, map, REDOUBT_COL + 5, REDOUBT_ROW - 5);
        turret(simulation, map, REDOUBT_COL - 5, REDOUBT_ROW + 5);
        turret(simulation, map, REDOUBT_COL + 5, REDOUBT_ROW + 5);

        // Across the approaches from each corner, not around the post: a ring of mines round the
        // ground you are standing on is a ring you eventually get shoved into.
        mines(
                simulation,
                map,
                188,
                7,
                191,
                9,
                186,
                11,
                190,
                5,
                206,
                7,
                203,
                9,
                208,
                11,
                204,
                5,
                188,
                23,
                191,
                21,
                186,
                19,
                190,
                25,
                206,
                23,
                203,
                21,
                208,
                19,
                204,
                25,
                193,
                15,
                201,
                15,
                197,
                10,
                197,
                20);

        // Barrels in the open between the guns, where a wave has to come past them.
        barrels(simulation, map, 192, 12, 202, 12, 192, 18, 202, 18, 197, 7, 197, 23, 185, 15, 209, 15);

        // A gun worth having for the last fight, behind the post rather than in front of it - it is
        // for the man who loses his in the first wave, not a reward for arriving.
        pickups(simulation, map, PickupKind.ARMOURY_RAILGUN, 194, 15);
        pickups(simulation, map, PickupKind.ARMOURY_FLAMER, 200, 15);

        // What comes in, and from where. Every lot arrives at a corner, and the corners alternate, so
        // there is never a side of the square that is safe for long. Runners in among the slow ones
        // for the reason Defend has them: they arrive first and pull the squad off the line the
        // brutes are walking up. Held back until the point is taken.
        simulation
                .waves()
                .holdBack()
                .add(6f, UnitKind.GRUNT, 10, NORTH_WEST_GATE)
                .add(5f, UnitKind.GRUNT, 10, SOUTH_EAST_GATE)
                .add(4f, UnitKind.RUNNER, 8, NORTH_EAST_GATE)
                .add(7f, UnitKind.SPITTER, 5, SOUTH_WEST_GATE)
                .add(4f, UnitKind.RUNNER, 8, SOUTH_WEST_GATE)
                .add(8f, UnitKind.BRUTE, 5, NORTH_EAST_GATE)
                .add(5f, UnitKind.FROG, 6, SOUTH_EAST_GATE)
                .add(4f, UnitKind.RUNNER, 10, NORTH_WEST_GATE)
                .add(8f, UnitKind.GRUNT, 12, SOUTH_WEST_GATE)
                .add(4f, UnitKind.SPITTER, 5, NORTH_WEST_GATE)
                .add(8f, UnitKind.BRUTE, 6, SOUTH_EAST_GATE)
                .add(4f, UnitKind.RUNNER, 12, NORTH_EAST_GATE);
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
        // Off the rock as well as off the edge: a boulder nearly flush against a wall left a slot
        // narrower than a man, and troopers routed along the rock walked into it and stayed there.
        Scatter scatter = new Scatter(map, simulation.rng(), BOULDER_CLEARANCE, BOULDER_WALL_MARGIN)
                .keepingOffRock(BOULDER_WALL_MARGIN);
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

    /** A run of col/row pairs, for the things that lie in the open waiting to be walked into. */
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
