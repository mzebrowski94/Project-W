package pl.mzebrows.pwt.sim.scenario;

import pl.mzebrows.pwt.map.Maps;
import pl.mzebrows.pwt.map.Randomness;
import pl.mzebrows.pwt.map.Scatter;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.UnitDef;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * A map three screens across and two deep, so the view has to follow you (G0008).
 *
 * <p>Exercises the camera and nothing else. What to look at: whether walking feels like moving
 * through a place rather than dragging a window around; whether the view leans far enough ahead to
 * see what you are walking into; whether it sticks properly at the edges instead of showing the void;
 * and what <kbd>Tab</kbd> is actually worth - the pulled-back view should be for reading the field,
 * not for playing from.
 *
 * <p>The armouries are the far side of the map on purpose. They are the reason to cross it, which is
 * the only way to find out whether crossing it is any fun.
 */
final class ExpanseScenario implements Scenario {

    private static final int COLS = 96;
    private static final int ROWS = 36;
    private static final float TILE = 5f;
    private static final int REGIONS = 12;

    private static final int GRUNTS = 26;
    private static final int BRUTES = 4;
    private static final int OBSTACLES = 24;

    /** Blocks of rock, three tiles square, scattered before the pockets they make are sealed off. */
    private static final int ROCK_PATCHES = 26;

    private static final int PATCH_TILES = 3;

    /** Where the commander starts, in tiles. Also where reachability is measured from. */
    private static final int START_COL = 6;

    private static final int START_ROW = 18;

    /** Kept clear of rock so the level never opens with a wall in your face. */
    private static final int CLEAR_OF_START = 5;

    private static final float CLEARANCE = 2f;
    private static final float STANDOFF = 30f;

    /**
     * How wide the band of the far east an armoury is laid in, in tiles.
     *
     * <p>A band rather than a tile. This level generates its rock at random and then seals whatever
     * pockets that made, so a tile chosen by hand is a tile that is <em>sometimes</em> inside a wall
     * and sometimes walled off - which is exactly what happened to both crates at seeds 5 and 9, and
     * what no single-seed test could see. Scattering into the band asks the map where there is room
     * instead of telling it, and every tile still passable after the seal is reachable by
     * construction, so the crate is always both outside rock and walkable to.
     */
    private static final int ARMOURY_BAND = 10;

    /** One crate in the northern half of that band and one in the southern, so the two are a choice. */
    private static final int ARMOURIES_PER_HALF = 1;

    private static final Terrain[] GROUNDS = {
        Terrain.PLAIN, Terrain.PLAIN, Terrain.PLAIN, Terrain.DUST, Terrain.DUST, Terrain.ORGANIC
    };

    @Override
    public String name() {
        return "Expanse";
    }

    @Override
    public String description() {
        return "Three screens across: the view follows you, and Tab pulls it back.";
    }

    @Override
    public SimConfig configure(SimConfig base) {
        return Scenario.withoutMuster(base)
                .withMap(new SimConfig.MapConfig(COLS, ROWS, TILE, REGIONS))
                .withUnit(UnitKind.GRUNT, grunt -> grunt.withCount(GRUNTS))
                .withUnit(UnitKind.BRUTE, brute -> brute.withCount(BRUTES))
                .withObstacle(base.obstacle().withCount(OBSTACLES));
    }

    @Override
    public TileMap map(SimConfig config, Randomness randomness) {
        TileMap.Builder builder = TileMap.builder(COLS, ROWS, TILE);
        Maps.partitionRegions(builder, REGIONS, randomness);

        Terrain[] byRegion = new Terrain[REGIONS];
        for (int region = 0; region < REGIONS; region++) {
            byRegion[region] = GROUNDS[randomness.nextInt(GROUNDS.length)];
        }
        Maps.paintRegions(builder, region -> byRegion[region % REGIONS]);

        builder.border(1, Terrain.ROCK);
        for (int patch = 0; patch < ROCK_PATCHES; patch++) {
            int col = randomness.nextInt(COLS - PATCH_TILES);
            int row = randomness.nextInt(ROWS - PATCH_TILES);
            if (Math.abs(col - START_COL) <= CLEAR_OF_START && Math.abs(row - START_ROW) <= CLEAR_OF_START) {
                continue;
            }
            builder.rect(col, row, PATCH_TILES, PATCH_TILES, Terrain.ROCK);
        }
        return Maps.sealUnreachable(builder, START_COL, START_ROW, Terrain.ROCK).build();
    }

    @Override
    public void populate(Simulation simulation) {
        SimConfig config = simulation.config();
        TileMap map = simulation.map();
        float startX = map.tileCentreX(START_COL);
        float startY = map.tileCentreY(START_ROW);

        simulation.spawnCommander(startX, startY);

        float wallMargin = config.commander().radius() * 2f + CLEARANCE;
        Scatter scatter = new Scatter(map, simulation.rng(), CLEARANCE, wallMargin);
        scatter.reserve(startX, startY, config.commander().radius() + STANDOFF);

        armouries(simulation, scatter, config);

        scatter.scatter(config.obstacle().count(), config.obstacle().radius(), simulation::spawnObstacle);
        for (UnitKind kind : new UnitKind[] {UnitKind.GRUNT, UnitKind.BRUTE}) {
            UnitDef def = config.unit(kind);
            scatter.scatter(def.count(), def.radius(), (x, y) -> simulation.spawnUnit(kind, x, y));
        }
    }

    /**
     * The two crates that are the reason to cross the map, one in each half of the far east.
     *
     * <p>Through the same {@code Scatter} everything else on this level goes through, and before the
     * obstacles, so a crate is reserved ground rather than something a boulder can be dropped on.
     */
    private static void armouries(Simulation simulation, Scatter scatter, SimConfig config) {
        float radius = config.pickup(PickupKind.ARMOURY_RAILGUN).radius();
        int col = COLS - ARMOURY_BAND - 1;
        int half = ROWS / 2;
        scatter.scatter(
                new TileRect(col, 1, ARMOURY_BAND, half - 1),
                ARMOURIES_PER_HALF,
                radius,
                (x, y) -> simulation.spawnPickup(PickupKind.ARMOURY_RAILGUN, x, y));
        scatter.scatter(
                new TileRect(col, half, ARMOURY_BAND, half - 1),
                ARMOURIES_PER_HALF,
                radius,
                (x, y) -> simulation.spawnPickup(PickupKind.ARMOURY_RAILGUN, x, y));
    }
}
