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
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeSystem;

/**
 * Open sand to dash about on (G0107), and the things that change a dash: glue and a thicket to halve it, a wall to
 * stop it, and a handful of grunts at the far side to dash away from. No squad, so the read is the commander alone.
 *
 * <p>What to look at: a tile and a half in a blink along the way he walks, never his aim; with the stick let go, the way he
 * last walked. The thrusters on his back while it runs, the puff where it began, and only a puff on a press with no
 * dash left. The purple pips beside his wounds - two, coming back one at a time. Half the reach through the glue
 * (west, by where he starts) and the thicket (south); the wall (north) stops it dead. A grunt's claws land mid-dash
 * as anywhere: it protects him from nothing.
 *
 * <p><strong>Dash Yard Upgraded</strong> (G0108) is the same yard with Rocketman II and Pantera II held by every seat from
 * the first tick, and a denser pack of grunts to dash into. What to look at: four purple pips coming back faster, and a
 * blue-violet ring where each dash begins - every grunt inside it, in a clear line, dazed for a second; none behind the
 * wall, and never a man of his.
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

    /** The plain yard's grunts, in a line down the far side, and the upgraded yard's pack: columns by rows. */
    private static final int LINE = 5;

    private static final int PACK_COLS = 4;

    private static final int PACK_ROWS = 4;

    /** Where the pack's south-west grunt stands, in tiles, and how far apart they stand. */
    private static final int PACK_COL = 26;

    private static final int PACK_ROW = 7;

    private static final int PACK_PITCH = 2;

    /** Rocketman's and Pantera's last rank, held from the first tick. */
    private static final int RANKS = 2;

    private final boolean upgraded;

    DashYardScenario() {
        this(false);
    }

    /** @param upgraded the yard with Rocketman and Pantera held by every seat, and a pack to dash into */
    DashYardScenario(boolean upgraded) {
        this.upgraded = upgraded;
    }

    @Override
    public String name() {
        return upgraded ? "Dash Yard Upgraded" : "Dash Yard";
    }

    @Override
    public String description() {
        return upgraded
                ? "Dash Yard with Rocketman and Pantera held: four dashes, each stunning the grunts round you."
                : "Dash on open sand, through glue and thicket, into a wall, away from grunts.";
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

        if (!upgraded) {
            for (int i = 0; i < LINE; i++) {
                simulation.spawnUnit(UnitKind.GRUNT, map.tileCentreX(35), map.tileCentreY(6 + i * 3));
            }
            return;
        }
        for (int col = 0; col < PACK_COLS; col++) {
            for (int row = 0; row < PACK_ROWS; row++) {
                simulation.spawnUnit(
                        UnitKind.GRUNT,
                        map.tileCentreX(PACK_COL + col * PACK_PITCH),
                        map.tileCentreY(PACK_ROW + row * PACK_PITCH));
            }
        }
        // Every seat, whoever sits in it: a personal upgrade is held by a slot, and a player may join any of them.
        for (int slot = 0; slot < simulation.capacity(); slot++) {
            UpgradeSystem.hold(simulation.engine(), simulation.config(), UpgradeKind.ROCKETMAN, RANKS, slot);
            UpgradeSystem.hold(simulation.engine(), simulation.config(), UpgradeKind.PANTERA, RANKS, slot);
        }
    }
}
