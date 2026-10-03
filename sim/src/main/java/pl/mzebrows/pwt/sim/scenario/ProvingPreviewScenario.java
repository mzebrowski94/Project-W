package pl.mzebrows.pwt.sim.scenario;

import pl.mzebrows.pwt.map.Randomness;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * What the proving ground page shows in its window (G0060): one of the chosen strain on a patch of sand,
 * with nobody to hunt, going about its business - its gait, its size and its colour, which is what the
 * player needs to recognise it by (pillar 5).
 *
 * <p><strong>Not a level.</strong> No commander, so nothing here is playable and the rules every
 * scenario keeps for the player do not apply; it is built through {@code Scenarios.world} so it is a
 * world like any other, seeded and the same every time it is shown.
 */
final class ProvingPreviewScenario implements Scenario {

    /** The patch, in tiles: about the window's shape, with room to amble. */
    private static final int COLS = 24;

    private static final int ROWS = 14;

    private final UnitKind strain;

    ProvingPreviewScenario(UnitKind strain) {
        this.strain = strain;
    }

    @Override
    public String name() {
        return "Proving Ground preview";
    }

    @Override
    public String description() {
        return "One " + strain + " on sand, for the proving ground page.";
    }

    @Override
    public SimConfig configure(SimConfig base) {
        return Scenario.withoutMuster(base)
                .withMap(base.map().withCols(COLS).withRows(ROWS).withRegions(1));
    }

    /** Sand, walled with rock so whatever ambles stays in the window. */
    @Override
    public TileMap map(SimConfig config, Randomness randomness) {
        SimConfig.MapConfig shape = config.map();
        return TileMap.builder(shape.cols(), shape.rows(), shape.tileSize())
                .fill(Terrain.SAND)
                .border(1, Terrain.ROCK)
                .build();
    }

    @Override
    public void populate(Simulation simulation) {
        TileMap map = simulation.map();
        simulation.spawnUnit(strain, map.worldWidth() * 0.5f, map.worldHeight() * 0.5f);
    }
}
