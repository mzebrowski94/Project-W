package pl.mzebrows.pwt.sim.scenario;

import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * An empty reserve, four troopers and a crowd of grunts coming (G0121): somewhere to die and be brought back by a
 * recall. No muster, so the four are all there is.
 *
 * <p>What to look at: each time he goes, the trooper nearest the spawn area - the rally point, with no point taken -
 * rises and fades in pale cyan where he stood, never falling as a death does, and the panel says {@code RECALL} while
 * he comes back there. The squad shrinks by one each time. With no trooper left on his feet the panel says
 * {@code NO RESERVE}, and the next death loses the level.
 */
final class RecallScenario implements Scenario {

    private static final int SQUAD = 4;

    /** Grunts in a block on the far side, enough to kill him more than once. */
    private static final int GRUNT_COLS = 3;

    private static final int GRUNT_ROWS = 4;

    /** The block's west column and its south row, as a share of the arena, and how far apart they stand, in tiles. */
    private static final float GRUNTS_FROM = 0.7f;

    private static final float GRUNTS_SOUTH = 0.3f;

    private static final int GRUNT_PITCH = 2;

    @Override
    public String name() {
        return "Recall";
    }

    @Override
    public String description() {
        return "No reserve, four troopers: each death recalls the trooper nearest the rally point to bring you back.";
    }

    @Override
    public SimConfig configure(SimConfig base) {
        SimConfig quiet = Scenario.withoutMuster(base);
        return quiet.withReserve(quiet.reserve().withStart(0))
                .withTrooper(base.trooper().withSquadSize(SQUAD));
    }

    @Override
    public void populate(Simulation simulation) {
        deployPlayerSide(simulation);
        TileMap map = simulation.map();
        int west = (int) (map.cols() * GRUNTS_FROM);
        int south = (int) (map.rows() * GRUNTS_SOUTH);
        for (int col = 0; col < GRUNT_COLS; col++) {
            for (int row = 0; row < GRUNT_ROWS; row++) {
                simulation.spawnUnit(
                        UnitKind.GRUNT,
                        map.tileCentreX(west + col * GRUNT_PITCH),
                        map.tileCentreY(south + row * GRUNT_PITCH));
            }
        }
    }
}
