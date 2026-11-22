package pl.mzebrows.pwt.sim.scenario;

import java.util.List;
import pl.mzebrows.pwt.map.House;
import pl.mzebrows.pwt.map.Maps;
import pl.mzebrows.pwt.map.Randomness;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.def.Objective;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * One shut house with a house pack of three jaguars in it (G0112), the commander and his squad outside it to the west.
 * Killing them is the level's one objective.
 *
 * <p>What to look at: the three strong-purple bodies wandering about inside the house and never out of it, while
 * nobody is near. Walk within a grunt's detection of them and they notice you through the walls, go to the door and
 * leap it down - a shake each time - and come out weaving, leaping fast in threes, and stopping a moment to spit a
 * locked line of three small purple globs that hurt nobody and leave glue. Open the door yourself and they are out
 * at once.
 */
final class JaguarHouseScenario implements Scenario {

    private static final int COLS = 44;
    private static final int ROWS = 26;
    private static final float TILE = 5f;
    private static final int REGIONS = 3;

    /** The house: its walls, in tiles, as a mission lays one. */
    private static final TileRect WALLS = new TileRect(22, 8, 11, 9);

    /** Its one way in, three tiles of its west wall, shut. */
    private static final TileRect DOORWAY = new TileRect(WALLS.col(), 11, 1, 3);

    private static final House HOUSE = new House(WALLS, List.of(DOORWAY));

    /** Where the squad starts: a strip by the west edge, out of the jaguars' notice. */
    private static final SimConfig.SpawnConfig SPAWN = new SimConfig.SpawnConfig(3, 3, 6);

    /** The three jaguars, in tiles of the house's inside. */
    private static final int[][] JAGUARS = {{25, 10}, {28, 14}, {31, 11}};

    @Override
    public String name() {
        return "Jaguar House";
    }

    @Override
    public String description() {
        return "Three jaguars shut in a house: they wander inside until they notice you, then leap the door down.";
    }

    /** Everything unaware, as a mission puts it down. */
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
        return builder.fill(Terrain.PLAIN)
                .border(1, Terrain.ROCK)
                .rect(WALLS.col(), WALLS.row(), WALLS.width(), WALLS.height(), Terrain.WALL)
                .rect(WALLS.col() + 1, WALLS.row() + 1, WALLS.width() - 2, WALLS.height() - 2, Terrain.PLAIN)
                .rect(DOORWAY.col(), DOORWAY.row(), DOORWAY.width(), DOORWAY.height(), Terrain.PLAIN)
                .build();
    }

    @Override
    public void populate(Simulation simulation) {
        simulation.spawnDoor(DOORWAY);
        deployPlayerSide(simulation);
        TileMap map = simulation.map();
        for (int[] at : JAGUARS) {
            HousePacks.spawn(
                    simulation,
                    HousePacks.in(HOUSE, map, UnitKind.JAGUAR, map.tileCentreX(at[0]), map.tileCentreY(at[1]), 0));
        }
        simulation.level().require(Objective.CLEAR_ENEMIES);
    }
}
