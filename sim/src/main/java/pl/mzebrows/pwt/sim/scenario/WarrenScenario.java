package pl.mzebrows.pwt.sim.scenario;

import pl.mzebrows.pwt.map.Maps;
import pl.mzebrows.pwt.map.Randomness;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * Rock you cannot walk through: a walled arena split into two chambers by staggered walls, with a
 * dead-end alcove on the left and a block to circle on the right.
 *
 * <p>Exercises impassable terrain and nothing else. What to look at: walk along a wall and check the
 * commander slides rather than catching every five units; back into the alcove and let a crowd follow
 * you in; shoot at rock and watch the shots stop; and see whether a brute can follow you through the
 * gaps at all - it is 5.2 across and a tile is 5, so a one-tile crack takes grunts and refuses brutes.
 *
 * <p>It will also show the thing terrain makes worse rather than better: enemies still walk straight
 * at the commander, so they jam against a wall corner instead of going round it. That is not this
 * scenario failing - it is the pathing question arriving, and it is deliberately not answered here.
 *
 * <p>Fixes its own map size rather than taking the caller's, because the layout is hand-laid and its
 * shape <em>is</em> the scenario.
 */
final class WarrenScenario implements Scenario {

    private static final int COLS = 32;
    private static final int ROWS = 18;
    private static final float TILE = 5f;
    private static final int REGIONS = 4;

    @Override
    public String name() {
        return "Warren";
    }

    @Override
    public String description() {
        return "Walls: slide along one, back a crowd into a dead end, and shoot at rock.";
    }

    @Override
    public SimConfig configure(SimConfig base) {
        return base.withMap(new SimConfig.MapConfig(COLS, ROWS, TILE, REGIONS));
    }

    @Override
    public TileMap map(SimConfig config, Randomness randomness) {
        TileMap.Builder builder = TileMap.builder(COLS, ROWS, TILE);
        Maps.partitionRegions(builder, REGIONS, randomness);
        return builder.border(1, Terrain.ROCK)
                .rect(11, 1, 1, 11, Terrain.ROCK)
                .rect(21, 6, 1, 11, Terrain.ROCK)
                .rect(3, 4, 5, 1, Terrain.ROCK)
                .rect(7, 4, 1, 4, Terrain.ROCK)
                .rect(25, 8, 3, 3, Terrain.ROCK)
                .build();
    }

    @Override
    public void populate(Simulation simulation) {
        TileMap map = simulation.map();
        simulation.spawnCommander(map.tileCentreX(5), map.tileCentreY(11));

        for (int i = 0; i < 6; i++) {
            simulation.spawnUnit(UnitKind.GRUNT, map.tileCentreX(24 + i % 3), map.tileCentreY(3 + i / 3));
        }
        simulation.spawnUnit(UnitKind.BRUTE, map.tileCentreX(28), map.tileCentreY(14));
        simulation.spawnUnit(UnitKind.BRUTE, map.tileCentreX(15), map.tileCentreY(14));
    }
}
