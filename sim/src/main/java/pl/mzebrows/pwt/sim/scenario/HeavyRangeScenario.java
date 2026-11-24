package pl.mzebrows.pwt.sim.scenario;

import pl.mzebrows.pwt.map.Maps;
import pl.mzebrows.pwt.map.Randomness;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeSystem;

/**
 * Firepower II held from the first tick (G0127), the three heavy guns lying by the start - a row each of flamers,
 * bazookas and detonators - and something worth hitting harder at the far side: a pack of brutes and a thumper.
 *
 * <p>What to look at: the flamer, the rocket and both limpet blasts each landing 30 % harder - a brute (450) going in
 * fewer flamer pulls, the thumper (2000) taking a rocket's blast for 1300 rather than 1000. No crate here: Firepower
 * stocks none, which the crates of any mission show.
 */
final class HeavyRangeScenario implements Scenario {

    private static final int COLS = 44;
    private static final int ROWS = 24;
    private static final float TILE = 5f;
    private static final int REGIONS = 3;

    /** Firepower's last rank. */
    private static final int RANKS = 2;

    /** How many caches of each gun lie in its row, so a gun can be taken again after another. */
    private static final int CACHES = 3;

    /** The pack of brutes, columns by rows, its south-west brute's tile and how far apart they stand. */
    private static final int PACK_COLS = 3;

    private static final int PACK_ROWS = 3;

    private static final int PACK_COL = 32;

    private static final int PACK_ROW = 8;

    private static final int PACK_PITCH = 3;

    @Override
    public String name() {
        return "Heavy Range";
    }

    @Override
    public String description() {
        return "Firepower II held, the flamer, bazooka and detonator to hand, brutes and a thumper to hit harder.";
    }

    @Override
    public SimConfig configure(SimConfig base) {
        return Scenario.withoutMuster(base).withMap(new SimConfig.MapConfig(COLS, ROWS, TILE, REGIONS));
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
        simulation.spawnCommander(map.tileCentreX(6), map.tileCentreY(12));
        PickupKind[] guns = {PickupKind.ARMOURY_FLAMER, PickupKind.ARMOURY_BAZOOKA, PickupKind.ARMOURY_DETONATOR};
        for (int gun = 0; gun < guns.length; gun++) {
            for (int i = 0; i < CACHES; i++) {
                simulation.spawnPickup(guns[gun], map.tileCentreX(3 + i * 2), map.tileCentreY(16 + gun * 2));
            }
        }
        for (int col = 0; col < PACK_COLS; col++) {
            for (int row = 0; row < PACK_ROWS; row++) {
                simulation.spawnUnit(
                        UnitKind.BRUTE,
                        map.tileCentreX(PACK_COL + col * PACK_PITCH),
                        map.tileCentreY(PACK_ROW + row * PACK_PITCH));
            }
        }
        simulation.spawnUnit(UnitKind.THUMPER, map.tileCentreX(40), map.tileCentreY(18));
        UpgradeSystem.hold(simulation.engine(), simulation.config(), UpgradeKind.FIREPOWER, RANKS);
    }
}
