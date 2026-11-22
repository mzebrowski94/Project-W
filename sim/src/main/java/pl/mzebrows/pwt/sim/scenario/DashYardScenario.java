package pl.mzebrows.pwt.sim.scenario;

import pl.mzebrows.pwt.map.Maps;
import pl.mzebrows.pwt.map.Randomness;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.SlickDef;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * Open sand to dash about on (G0107), and the things that change a dash: glue and a thicket to halve it, a wall to
 * stop it, and a handful of grunts at the far side to dash away from. No squad, so the read is the commander alone.
 *
 * <p>What to look at: three tiles in a blink along the way he walks, never his aim; with the stick let go, the way he
 * last walked. The thrusters on his back while it runs, the puff where it began, and only a puff on a press with no
 * dash left. The purple pips beside his wounds - two, coming back one at a time. Half the reach through the glue
 * (west, by where he starts) and the thicket (south); the wall (north) stops it dead. A grunt's claws land mid-dash
 * as anywhere: it protects him from nothing.
 */
final class DashYardScenario implements Scenario {

    private static final int COLS = 40;
    private static final int ROWS = 24;
    private static final float TILE = 5f;
    private static final int REGIONS = 3;

    /** Long past any visit, so the glue is there whenever he comes back to it. */
    private static final float GLUE_SECONDS = 3600f;

    /** The glue puddle's south-west corner, how far across it runs and how far apart its circles are, in world units. */
    private static final float GLUE_WEST = 12f;

    private static final float GLUE_SOUTH = 48f;

    private static final float GLUE_ACROSS = 18f;

    private static final float GLUE_PITCH = 3f;

    @Override
    public String name() {
        return "Dash Yard";
    }

    @Override
    public String description() {
        return "Dash on open sand, through glue and thicket, into a wall, away from grunts.";
    }

    @Override
    public SimConfig configure(SimConfig base) {
        return Scenario.withoutMuster(base).withMap(new SimConfig.MapConfig(COLS, ROWS, TILE, REGIONS));
    }

    @Override
    public TileMap map(SimConfig config, Randomness randomness) {
        TileMap.Builder builder = TileMap.builder(COLS, ROWS, TILE);
        Maps.partitionRegions(builder, REGIONS, randomness);
        return builder.fill(Terrain.SAND)
                .border(1, Terrain.ROCK)
                // A wall to dash into, north of where he starts.
                .rect(6, 16, 10, 1, Terrain.ROCK)
                // A thicket to dash across, south of where he starts.
                .rect(6, 3, 8, 5, Terrain.THICKET)
                .build();
    }

    @Override
    public void populate(Simulation simulation) {
        TileMap map = simulation.map();
        simulation.spawnCommander(map.tileCentreX(10), map.tileCentreY(11));

        SlickDef glue = simulation
                .config()
                .weapon(WeaponKind.GLOB)
                .slicks()
                .withSecondsMax(GLUE_SECONDS)
                .withSeconds(GLUE_SECONDS);
        // A puddle of glue west of him, circles overlapping, wide enough to start a dash in and stay in.
        for (float x = GLUE_WEST; x <= GLUE_WEST + GLUE_ACROSS; x += GLUE_PITCH) {
            for (float y = GLUE_SOUTH; y <= GLUE_SOUTH + GLUE_ACROSS; y += GLUE_PITCH) {
                simulation.spawnSlick(x, y, glue, Side.ALIEN);
            }
        }

        for (int i = 0; i < 5; i++) {
            simulation.spawnUnit(UnitKind.GRUNT, map.tileCentreX(35), map.tileCentreY(6 + i * 3));
        }
    }
}
