package pl.mzebrows.pwt.sim.scenario;

import pl.mzebrows.pwt.map.Maps;
import pl.mzebrows.pwt.map.Randomness;
import pl.mzebrows.pwt.map.Scatter;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.def.UnitDef;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * The default arena: a commander in the middle, obstacles scattered around, enemies further out.
 *
 * <p>Scattered rock, kept well away from the middle so the opening is never a wall in your face. The
 * layout is a partition into regions first and terrain over it, which is the order that produces
 * places rather than noise.
 *
 * <p>Exercises everything at once - movement, aiming, firing, chasing, separation, terrain and arena
 * bounds -
 * which makes it the scenario to open when checking that nothing is obviously broken, and the wrong
 * one for isolating a single system.
 */
final class SkirmishScenario implements Scenario {

    /** Gap left between anything placed and anything already there, in world units. */
    private static final float CLEARANCE = 1.5f;

    /** How far from the commander an enemy must start, so the scenario does not open mid-collision. */
    private static final float ENEMY_STANDOFF = 18f;

    /** Blocks of rock dropped around the edges of the map. */
    private static final int ROCK_PATCHES = 5;

    private static final int PATCH_TILES = 2;

    /** Tiles of open ground kept around the middle, where the commander starts. */
    private static final int CLEAR_OF_CENTRE = 4;

    /** Attempts per patch before giving up on it. A test arena may simply have nowhere left. */
    private static final int PATCH_ATTEMPTS = 16;

    @Override
    public String name() {
        return "Skirmish";
    }

    /** What a region's ground may be. Weighted by repetition: mostly open, some dust, a little growth. */
    private static final Terrain[] GROUNDS = {
        Terrain.PLAIN, Terrain.PLAIN, Terrain.PLAIN, Terrain.DUST, Terrain.DUST, Terrain.ORGANIC
    };

    @Override
    public TileMap map(SimConfig config, Randomness randomness) {
        SimConfig.MapConfig shape = config.map();
        TileMap.Builder builder = TileMap.builder(shape.cols(), shape.rows(), shape.tileSize());
        Maps.partitionRegions(builder, shape.regions(), randomness);

        Terrain[] byRegion = new Terrain[Math.max(1, shape.regions())];
        for (int region = 0; region < byRegion.length; region++) {
            byRegion[region] = GROUNDS[randomness.nextInt(GROUNDS.length)];
        }
        Maps.paintRegions(builder, region -> byRegion[region % byRegion.length]);

        scatterRock(builder, randomness);
        return builder.build();
    }

    @Override
    public String description() {
        return "Everything at once: move, aim, fire, get chased, bump into things.";
    }

    private static void scatterRock(TileMap.Builder builder, Randomness randomness) {
        int centreCol = builder.cols() / 2;
        int centreRow = builder.rows() / 2;
        int colRange = Math.max(1, builder.cols() - PATCH_TILES);
        int rowRange = Math.max(1, builder.rows() - PATCH_TILES);
        for (int patch = 0; patch < ROCK_PATCHES; patch++) {
            for (int attempt = 0; attempt < PATCH_ATTEMPTS; attempt++) {
                int col = randomness.nextInt(colRange);
                int row = randomness.nextInt(rowRange);
                if (Math.abs(col - centreCol) <= CLEAR_OF_CENTRE && Math.abs(row - centreRow) <= CLEAR_OF_CENTRE) {
                    continue;
                }
                builder.rect(col, row, PATCH_TILES, PATCH_TILES, Terrain.ROCK);
                break;
            }
        }
    }

    @Override
    public void populate(Simulation simulation) {
        SimConfig config = simulation.config();
        TileMap map = simulation.map();
        float centreX = map.worldWidth() * 0.5f;
        float centreY = map.worldHeight() * 0.5f;

        simulation.spawnCommander(centreX, centreY);

        float wallMargin = config.commander().radius() * 2f + CLEARANCE;
        Scatter scatter = new Scatter(map, simulation.rng(), CLEARANCE, wallMargin);
        scatter.reserve(centreX, centreY, config.commander().radius() + ENEMY_STANDOFF);

        scatter.scatter(config.obstacle().count(), config.obstacle().radius(), simulation::spawnObstacle);
        UnitDef grunt = config.unit(UnitKind.GRUNT);
        scatter.scatter(grunt.count(), grunt.radius(), (x, y) -> simulation.spawnUnit(UnitKind.GRUNT, x, y));
    }
}
