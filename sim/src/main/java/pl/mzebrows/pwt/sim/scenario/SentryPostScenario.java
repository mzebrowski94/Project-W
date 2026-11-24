package pl.mzebrows.pwt.sim.scenario;

import pl.mzebrows.pwt.map.Maps;
import pl.mzebrows.pwt.map.Randomness;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * Two sentries standing ahead of him from the first tick (G0129), a boulder or two between them and the far end, and a
 * pack coming in lots from the east: grunts with runners among them, a brute in the last lot.
 *
 * <p>What to look at: two small light-grey squares, clearly smaller than a turret, coming round fast onto the nearest
 * alien in their reach and firing the turret's blue at it, passing his men without a mark; the pack turning onto them as
 * onto a man, and taking one apart; each one's time left shrinking on it; and each going off - destroyed, or at about a
 * minute - in a small blast and a stun on the aliens round it, never on his men.
 */
final class SentryPostScenario implements Scenario {

    private static final int COLS = 48;
    private static final int ROWS = 26;
    private static final float TILE = 5f;
    private static final int REGIONS = 3;

    /** Where the pack is put down: the far end, so it has ground to cross into the sentries' reach. */
    private static final TileRect EAST_GATE = new TileRect(42, 10, 4, 6);

    @Override
    public String name() {
        return "Sentry Post";
    }

    @Override
    public String description() {
        return "Two sentries standing ahead of you and a pack coming at them from the east.";
    }

    @Override
    public SimConfig configure(SimConfig base) {
        return base.withMap(new SimConfig.MapConfig(COLS, ROWS, TILE, REGIONS));
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
        simulation.spawnCommander(map.tileCentreX(6), map.tileCentreY(13));
        // Ahead of him and apart, so the pack meets them first and each covers its own half of the approach.
        simulation.spawnSentry(map.tileCentreX(14), map.tileCentreY(9), 0);
        simulation.spawnSentry(map.tileCentreX(14), map.tileCentreY(17), 0);
        simulation.spawnObstacle(map.tileCentreX(26), map.tileCentreY(8));
        simulation.spawnObstacle(map.tileCentreX(28), map.tileCentreY(18));
        simulation
                .waves()
                .add(2f, UnitKind.GRUNT, 8, EAST_GATE)
                .add(6f, UnitKind.RUNNER, 5, EAST_GATE)
                .add(8f, UnitKind.GRUNT, 10, EAST_GATE)
                .add(8f, UnitKind.BRUTE, 2, EAST_GATE);
    }
}
