package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TILE;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.component.Position;

/**
 * A crossing is a source of one slot's drafts (G0118): his own commander entering the pass out of a biome, once a
 * ridge, caught up by where he stands, and never waiting on the biome's sites. Here every source plans one draft
 * and the first comes at once ({@code AlphaWorlds}), so a crossing that counts is a draft.
 */
class CrossingTest {

    /** The west edge of each ridge's core, in tiles of the arena. */
    private static final int FIRST_RIDGE = 8;

    private static final int SECOND_RIDGE = 14;

    private static final float ROW = 50f;

    private static final float WEST = 10f;

    @Test
    void earnsHisSlotADraftWhenHisCommanderEntersARidge() {
        Simulation simulation = ridged();
        Entity commander = simulation.spawnCommander(WEST, ROW, 0);
        simulation.step();
        int before = simulation.upgradeView().draftsOf(0);

        standAt(commander, FIRST_RIDGE);
        simulation.step();

        assertThat(before).isZero();
        assertThat(simulation.upgradeView().draftsOf(0)).isEqualTo(1);
    }

    @Test
    void countsARidgeOnceHoweverOftenHeCrossesIt() {
        Simulation simulation = ridged();
        Entity commander = simulation.spawnCommander(WEST, ROW, 0);

        for (int i = 0; i < 3; i++) {
            standAt(commander, FIRST_RIDGE);
            simulation.step();
            standAt(commander, FIRST_RIDGE - 2);
            simulation.step();
        }

        assertThat(simulation.upgradeView().draftsOf(0)).isEqualTo(1);
    }

    /** A commander who comes on east of a ridge has crossed every ridge west of him. */
    @Test
    void catchesUpEveryRidgeWestOfWhereHeComesOn() {
        Simulation simulation = ridged();
        simulation.spawnCommander((SECOND_RIDGE + 2) * TILE, ROW, 0);

        simulation.step();

        assertThat(simulation.upgradeView().draftsOf(0)).isEqualTo(2);
    }

    @Test
    void countsACrossingForHisOwnSlotAlone() {
        Simulation simulation = ridged();
        Entity first = simulation.spawnCommander(WEST, ROW, 0);
        simulation.spawnCommander(WEST, ROW + 5f, 1);
        simulation.step();

        standAt(first, FIRST_RIDGE);
        simulation.step();

        assertThat(simulation.upgradeView().draftsOf(0)).isEqualTo(1);
        assertThat(simulation.upgradeView().draftsOf(1)).isZero();
    }

    /** The floor a run always reaches: a biome's site left untaken holds nothing back. */
    @Test
    void neverWaitsOnTheSitesOfTheBiomeHeLeaves() {
        Simulation simulation = ridged();
        Entity commander = simulation.spawnCommander(WEST, ROW, 0);
        simulation.spawnControlPoint(WEST, ROW + 20f);
        simulation.step();

        standAt(commander, FIRST_RIDGE);
        simulation.step();

        assertThat(simulation.upgradeView().draftsOf(0)).isEqualTo(1);
    }

    @Test
    void plansACrossingForEveryRidgeOfTheMap() {
        Simulation simulation = ridged();
        simulation.spawnCommander(WEST, ROW, 0);
        simulation.spawnControlPoint(WEST, ROW + 20f);

        simulation.step();

        assertThat(DraftTest.drafts(simulation).sources).isEqualTo(3);
    }

    /** The arena, open ground, with two ridges marked across it. */
    private static Simulation ridged() {
        TileMap map =
                AlphaWorlds.ground().ridge(FIRST_RIDGE).ridge(SECOND_RIDGE).build();
        return new Simulation(AlphaWorlds.config(), map);
    }

    /** Puts him in the middle of a column, as the solver would leave him there. */
    private static void standAt(Entity commander, int col) {
        Position at = commander.getComponent(Position.class);
        at.x = (col + 0.5f) * TILE;
    }
}
