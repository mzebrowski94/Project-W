package pl.mzebrows.pwt.sim.scenario;

import pl.mzebrows.pwt.map.Maps;
import pl.mzebrows.pwt.map.Randomness;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * The plasma cannon in hand from the first tick (G0128), a couple more caches by the start, a boulder field to beam past
 * and a crowd of grunts with brutes among them to throw orbs into. His squad comes too, to stand in the beams' way.
 *
 * <p>What to look at: one green orb a pull and the heat bar to the stop; the orb at twice his pace, its ring flickering
 * green and white, keeping up to three green lines on the nearest aliens as it passes, re-picked as it goes; a line
 * stopping on the first alien or boulder in its way, and passing his men without a mark on them; the orb dimming as its
 * beams land, and going off green - on what it touches, at its flight's end, or spent.
 */
final class PlasmaRangeScenario implements Scenario {

    private static final int COLS = 48;
    private static final int ROWS = 26;
    private static final float TILE = 5f;
    private static final int REGIONS = 3;

    /** The crowd, columns by rows, its south-west grunt's tile and how far apart they stand. */
    private static final int CROWD_COLS = 4;

    private static final int CROWD_ROWS = 5;

    private static final int CROWD_COL = 30;

    private static final int CROWD_ROW = 7;

    private static final int CROWD_PITCH = 2;

    /** Every so many of the crowd is a brute, which an orb's blast leaves standing. */
    private static final int BRUTE_EVERY = 5;

    @Override
    public String name() {
        return "Plasma Range";
    }

    @Override
    public String description() {
        return "The plasma cannon in hand, a squad beside you and a crowd of grunts and brutes to throw orbs into.";
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
        simulation
                .spawnCommander(map.tileCentreX(6), map.tileCentreY(13))
                .getComponent(Weapon.class)
                .set(
                        WeaponKind.PLASMA,
                        simulation.config().weapon(WeaponKind.PLASMA),
                        simulation.config().tickSeconds());
        simulation.spawnPickup(PickupKind.ARMOURY_PLASMA, map.tileCentreX(4), map.tileCentreY(17));
        simulation.spawnPickup(PickupKind.ARMOURY_PLASMA, map.tileCentreX(6), map.tileCentreY(17));
        for (int i = 0; i < 4; i++) {
            simulation.spawnObstacle(map.tileCentreX(20 + i * 2), map.tileCentreY(6 + i * 4));
        }
        int n = 0;
        for (int col = 0; col < CROWD_COLS; col++) {
            for (int row = 0; row < CROWD_ROWS; row++) {
                UnitKind kind = n++ % BRUTE_EVERY == 0 ? UnitKind.BRUTE : UnitKind.GRUNT;
                simulation.spawnUnit(
                        kind,
                        map.tileCentreX(CROWD_COL + col * CROWD_PITCH),
                        map.tileCentreY(CROWD_ROW + row * CROWD_PITCH));
            }
        }
    }
}
