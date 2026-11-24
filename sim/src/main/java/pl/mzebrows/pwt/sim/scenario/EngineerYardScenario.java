package pl.mzebrows.pwt.sim.scenario;

import pl.mzebrows.pwt.map.Maps;
import pl.mzebrows.pwt.map.Randomness;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeSystem;

/**
 * Engineer held by every seat from the first tick (G0129), a rock outcrop to set sentries down against, two grenade
 * caches behind him, and a pack coming in lots from the east.
 *
 * <p>What to look at: the grenade key setting a small light-grey sentry down just in front of him rather than
 * throwing, one grenade a sentry and on the grenade's wait; nothing set down - and nothing spent - with the key pressed
 * into the rock; the grenade slot's pips drawn as sentries, in their grey, the count the same; a cache refilling them.
 * <strong>Engineer Yard II</strong> is the same yard at Engineer's second rank: the sentries' fire visibly quicker.
 */
final class EngineerYardScenario implements Scenario {

    private static final int COLS = 48;
    private static final int ROWS = 26;
    private static final float TILE = 5f;
    private static final int REGIONS = 3;

    /** Where the pack is put down: the far end, so there is time to set sentries down before it arrives. */
    private static final TileRect EAST_GATE = new TileRect(42, 10, 4, 6);

    private final int ranks;

    /** @param ranks Engineer's rank every seat holds from the first tick: one or two */
    EngineerYardScenario(int ranks) {
        this.ranks = ranks;
    }

    @Override
    public String name() {
        return ranks > 1 ? "Engineer Yard II" : "Engineer Yard";
    }

    @Override
    public String description() {
        return ranks > 1
                ? "Engineer II held: your grenade key sets down sentries that fire faster, and a pack is coming."
                : "Engineer held: your grenade key sets a sentry down for a grenade, and a pack is coming.";
    }

    @Override
    public SimConfig configure(SimConfig base) {
        return base.withMap(new SimConfig.MapConfig(COLS, ROWS, TILE, REGIONS));
    }

    @Override
    public TileMap map(SimConfig config, Randomness randomness) {
        TileMap.Builder builder = TileMap.builder(COLS, ROWS, TILE);
        Maps.partitionRegions(builder, REGIONS, randomness);
        return builder.fill(Terrain.SAND)
                .border(1, Terrain.ROCK)
                // An outcrop just east of where he starts, to press the key into.
                .rect(10, 15, 2, 4, Terrain.ROCK)
                .build();
    }

    @Override
    public void populate(Simulation simulation) {
        TileMap map = simulation.map();
        simulation.spawnCommander(map.tileCentreX(6), map.tileCentreY(13));
        simulation.spawnPickup(PickupKind.ARMOURY_GRENADES, map.tileCentreX(3), map.tileCentreY(10));
        simulation.spawnPickup(PickupKind.ARMOURY_GRENADES, map.tileCentreX(3), map.tileCentreY(16));
        simulation
                .waves()
                .add(6f, UnitKind.GRUNT, 8, EAST_GATE)
                .add(8f, UnitKind.RUNNER, 6, EAST_GATE)
                .add(10f, UnitKind.GRUNT, 12, EAST_GATE)
                .add(10f, UnitKind.BRUTE, 3, EAST_GATE);
        // Every seat, whoever sits in it: a personal upgrade is held by a slot, and a player may join any of them.
        for (int slot = 0; slot < simulation.capacity(); slot++) {
            UpgradeSystem.hold(simulation.engine(), simulation.config(), UpgradeKind.ENGINEER, ranks, slot);
        }
    }
}
