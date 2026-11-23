package pl.mzebrows.pwt.sim.scenario;

import pl.mzebrows.pwt.map.Maps;
import pl.mzebrows.pwt.map.Randomness;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.def.Objective;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WorkedSiteKind;

/**
 * Open sand with one relic on it and its four wardens round it (G0114), the commander and his squad by the west edge,
 * and a few runners about. Taking the relic is the level's one objective.
 *
 * <p>What to look at: the gold square turning over its dark plinth, the striped ring round it, and the four pale oval
 * wardens on its diagonals - silent. Walk in and {@code HOLD [E] EXTRACT} at arm's length, then {@code EXTRACTING…}:
 * the first attempt wakes the wardens, and from then on every 0.75 s each fires a bead north, east, south and west,
 * whether anybody is at the relic or not - lanes crossing the ring. Square on a compass point, just outside the ring, a
 * man is out of every lane and still within reach. Each fresh attempt is heard far off, so the runners come. Six
 * seconds held and it is taken: the square is gone, the plinth stays, the wardens fall silent, crates and caches lie
 * round it - and two or three snakes come up out of the sand round it.
 */
final class RelicScenario implements Scenario {

    private static final int COLS = 44;
    private static final int ROWS = 26;
    private static final float TILE = 5f;
    private static final int REGIONS = 3;

    /** Where the squad starts: a strip by the west edge. */
    private static final SimConfig.SpawnConfig SPAWN = new SimConfig.SpawnConfig(3, 3, 6);

    /** The relic, in tiles: the middle of the arena. */
    private static final int RELIC_COL = 24;

    private static final int RELIC_ROW = 13;

    /** The runners about it, in tiles: within the relic's rouse, and out of its ring. */
    private static final int[][] RUNNERS = {{36, 5}, {38, 13}, {36, 21}, {28, 3}, {28, 23}};

    @Override
    public String name() {
        return "Relic";
    }

    @Override
    public String description() {
        return "A desert relic under four wardens: extract it through their crossing fire, then meet what comes up.";
    }

    @Override
    public SimConfig configure(SimConfig base) {
        SimConfig config = Scenario.withoutMuster(base).withMap(new SimConfig.MapConfig(COLS, ROWS, TILE, REGIONS));
        for (UnitKind kind : UnitKind.values()) {
            config = config.withUnit(
                    kind, strain -> strain.withPerception(strain.perception().withAlwaysAggressive(false)));
        }
        return config.withSpawn(SPAWN);
    }

    @Override
    public TileMap map(SimConfig config, Randomness randomness) {
        TileMap.Builder builder = TileMap.builder(COLS, ROWS, TILE);
        Maps.partitionRegions(builder, REGIONS, randomness);
        return builder.fill(Terrain.SAND).border(1, Terrain.ROCK).build();
    }

    @Override
    public void populate(Simulation simulation) {
        TileMap map = simulation.map();
        // Ground first, as every site is: its wardens come with it.
        simulation.spawnWorkedSite(WorkedSiteKind.RELIC, map.tileCentreX(RELIC_COL), map.tileCentreY(RELIC_ROW));
        deployPlayerSide(simulation);
        for (int[] at : RUNNERS) {
            simulation.spawnUnit(UnitKind.RUNNER, map.tileCentreX(at[0]), map.tileCentreY(at[1]));
        }
        simulation.level().require(Objective.CAPTURE_POINTS);
    }
}
