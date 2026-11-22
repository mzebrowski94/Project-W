package pl.mzebrows.pwt.sim.scenario;

import pl.mzebrows.pwt.map.Maps;
import pl.mzebrows.pwt.map.Randomness;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.def.Objective;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WorkedSiteKind;

/**
 * A house with a mainframe in it and a shut door (G0111), the commander and his squad outside it to the west, and grunts
 * about the house. Taking the mainframe is the level's one objective.
 *
 * <p>What to look at: the striped ring turning round the mainframe until it is taken, and round the control point in
 * the yard beside the house. The cabinet, its black screen and the red X on it. Left alone, the squad walks to the
 * house, one man opens the door, and one man downloads it while up to four stand round him watching their sectors;
 * a sixth is sent on to the point. Walk in yourself: {@code HOLD [E] DOWNLOAD} at arm's length, then {@code
 * DECRYPTING…} with its fill while the key is held, and everyone sees the loading circle fill on the mainframe. Let go,
 * dash or take a hit and it starts again - and each fresh attempt wakes the grunts round the house. Done, the screen
 * shows a green tick, the ring goes, and crates, caches and perhaps a medkit lie round it.
 */
final class MainframeScenario implements Scenario {

    private static final int COLS = 44;
    private static final int ROWS = 26;
    private static final float TILE = 5f;
    private static final int REGIONS = 3;

    /** The house: its walls' south-west corner and size, in tiles. */
    private static final int HOUSE_COL = 20;

    private static final int HOUSE_ROW = 8;

    private static final int HOUSE_WIDTH = 12;

    private static final int HOUSE_HEIGHT = 10;

    /** Its one way in, three tiles of its west wall, shut. */
    private static final TileRect DOORWAY = new TileRect(HOUSE_COL, 11, 1, 3);

    /** Six men, one past a full crowd of five, so one is seen sent on to the point. */
    private static final int SQUAD = 6;

    /** Where the squad starts: a strip by the west edge. */
    private static final SimConfig.SpawnConfig SPAWN = new SimConfig.SpawnConfig(3, 3, 6);

    /** The grunts round the house, in tiles. */
    private static final int[][] GRUNTS = {{36, 6}, {37, 12}, {36, 20}, {26, 4}, {28, 22}, {40, 16}};

    @Override
    public String name() {
        return "Mainframe";
    }

    @Override
    public String description() {
        return "A mainframe in a house behind a shut door: download it, or watch the squad do it.";
    }

    @Override
    public SimConfig configure(SimConfig base) {
        SimConfig config = Scenario.withoutMuster(base).withMap(new SimConfig.MapConfig(COLS, ROWS, TILE, REGIONS));
        for (UnitKind kind : UnitKind.values()) {
            config = config.withUnit(
                    kind, strain -> strain.withPerception(strain.perception().withAlwaysAggressive(false)));
        }
        return config.withTrooper(config.trooper().withSquadSize(SQUAD)).withSpawn(SPAWN);
    }

    @Override
    public TileMap map(SimConfig config, Randomness randomness) {
        TileMap.Builder builder = TileMap.builder(COLS, ROWS, TILE);
        Maps.partitionRegions(builder, REGIONS, randomness);
        return builder.fill(Terrain.SAND)
                .border(1, Terrain.ROCK)
                .rect(HOUSE_COL, HOUSE_ROW, HOUSE_WIDTH, HOUSE_HEIGHT, Terrain.WALL)
                .rect(HOUSE_COL + 1, HOUSE_ROW + 1, HOUSE_WIDTH - 2, HOUSE_HEIGHT - 2, Terrain.SAND)
                .rect(DOORWAY.col(), DOORWAY.row(), DOORWAY.width(), DOORWAY.height(), Terrain.SAND)
                .build();
    }

    @Override
    public void populate(Simulation simulation) {
        TileMap map = simulation.map();
        // Ground first, as every point is: the yard's point, north-east of the house.
        simulation.spawnControlPoint(map.tileCentreX(38), map.tileCentreY(22));
        // Against the house's north wall, on the tile beside it, halfway along.
        simulation.spawnWorkedSite(
                WorkedSiteKind.MAINFRAME,
                map.tileCentreX(HOUSE_COL + HOUSE_WIDTH / 2),
                map.tileCentreY(HOUSE_ROW + HOUSE_HEIGHT - 2));
        simulation.spawnDoor(DOORWAY);

        deployPlayerSide(simulation);

        for (int[] at : GRUNTS) {
            simulation.spawnUnit(UnitKind.GRUNT, map.tileCentreX(at[0]), map.tileCentreY(at[1]));
        }
        simulation.level().require(Objective.CAPTURE_POINTS);
    }
}
