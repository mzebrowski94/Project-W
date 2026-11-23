package pl.mzebrows.pwt.sim.scenario;

import pl.mzebrows.pwt.map.Maps;
import pl.mzebrows.pwt.map.Randomness;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.def.Cycle;
import pl.mzebrows.pwt.sim.def.EmplacementKind;
import pl.mzebrows.pwt.sim.def.Objective;

/**
 * A patch of mire with three spires on it (G0113), the commander and his squad by its west edge, and a control point
 * past the spires to take - the level's one objective. Nothing else on the field.
 *
 * <p>What to look at: the dark grey-green columns with their lighter caps, still while nobody is within 45 of them.
 * Walk closer and each starts firing a small pale-green bead a second, one bearing at a time round a cycle of four to
 * six compass points that never changes - watch the faint tick on its edge, which shows the bearing it fires next.
 * The beads hurt only the men; stand still and learn the gaps, then walk through them to the point.
 */
final class SpireFenScenario implements Scenario {

    private static final int COLS = 44;
    private static final int ROWS = 26;
    private static final float TILE = 5f;
    private static final int REGIONS = 3;

    /** Where the squad starts: a strip by the west edge, out of every spire's wake. */
    private static final SimConfig.SpawnConfig SPAWN = new SimConfig.SpawnConfig(2, 3, 6);

    /** The three spires, in tiles. */
    private static final int[][] SPIRES = {{19, 8}, {23, 17}, {29, 11}};

    /** The point past them, in tiles. */
    private static final int POINT_COL = 39;

    private static final int POINT_ROW = 13;

    /**
     * Their cycles, one a spire, laid by hand where a mission deals them - so the scenario is the same every time it is
     * judged. Four, five and six steps, each one to all four of the directions.
     */
    private static final Cycle[] CYCLES = {
        Cycle.of(
                Cycle.NORTH, Cycle.EAST | Cycle.WEST, Cycle.SOUTH, Cycle.NORTH | Cycle.EAST | Cycle.SOUTH | Cycle.WEST),
        Cycle.of(Cycle.EAST, Cycle.NORTH | Cycle.SOUTH, Cycle.WEST, Cycle.EAST | Cycle.SOUTH | Cycle.WEST, Cycle.NORTH),
        Cycle.of(
                Cycle.SOUTH | Cycle.WEST,
                Cycle.NORTH,
                Cycle.EAST | Cycle.WEST,
                Cycle.NORTH | Cycle.EAST | Cycle.SOUTH,
                Cycle.WEST,
                Cycle.NORTH | Cycle.EAST | Cycle.SOUTH | Cycle.WEST)
    };

    @Override
    public String name() {
        return "Spire Fen";
    }

    @Override
    public String description() {
        return "Three spires on the mire, each firing its own fixed cycle while you are near: learn it and walk through.";
    }

    @Override
    public SimConfig configure(SimConfig base) {
        return Scenario.withoutMuster(base)
                .withMap(new SimConfig.MapConfig(COLS, ROWS, TILE, REGIONS))
                .withSpawn(SPAWN);
    }

    @Override
    public TileMap map(SimConfig config, Randomness randomness) {
        TileMap.Builder builder = TileMap.builder(COLS, ROWS, TILE);
        Maps.partitionRegions(builder, REGIONS, randomness);
        return builder.fill(Terrain.MIRE).border(1, Terrain.ROCK).build();
    }

    @Override
    public void populate(Simulation simulation) {
        TileMap map = simulation.map();
        // Ground first, as every point is.
        simulation.spawnControlPoint(map.tileCentreX(POINT_COL), map.tileCentreY(POINT_ROW));
        for (int i = 0; i < SPIRES.length; i++) {
            simulation.spawnEmplacement(
                    EmplacementKind.SPIRE, map.tileCentreX(SPIRES[i][0]), map.tileCentreY(SPIRES[i][1]), CYCLES[i]);
        }
        deployPlayerSide(simulation);
        simulation.level().require(Objective.CAPTURE_POINTS);
    }
}
