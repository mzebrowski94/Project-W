package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import pl.mzebrows.pwt.map.Maps;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * How long one step takes as a crowd grows - the number `docs/performance.md` asks for before and after
 * anything claimed to be faster.
 *
 * <p>Opt-in, because it measures wall time and is only meaningful on an idle machine: {@code
 * PWT_BENCH=1}. Prints one line per crowd size. Fails if the largest crowd no longer fits one tick,
 * because in lockstep every peer pays the whole step.
 */
@EnabledIfEnvironmentVariable(named = "PWT_BENCH", matches = "1")
class SimulationBenchmarkTest {

    private static final int[] CROWDS = {100, 200, 400, 800};
    private static final int COLS = 120;
    private static final int ROWS = 60;
    private static final float TILE = 5f;
    private static final int SQUAD = 8;
    private static final int WARM_UP_TICKS = 200;
    private static final int MEASURED_TICKS = 300;

    @Test
    void theLargestCrowdStepsInsideOneTick() {
        double largest = 0;
        for (int crowd : CROWDS) {
            largest = millisecondsPerStep(crowd);
        }

        assertThat(largest).isLessThan(SimConfig.defaults().tickSeconds() * 1000.0);
    }

    /** A commander nobody can kill, his squad, and a crowd of grunts converging on them from everywhere. */
    private static double millisecondsPerStep(int crowd) {
        TileMap map = Maps.uniform(COLS, ROWS, TILE, Terrain.PLAIN);
        Simulation simulation = new Simulation(SimConfig.defaults(), map);
        simulation.level().commandersInvulnerable = true;
        float middleX = map.worldWidth() / 2f;
        float middleY = map.worldHeight() / 2f;
        simulation.spawnCommander(middleX, middleY);
        for (int i = 0; i < SQUAD; i++) {
            simulation.spawnTrooper(middleX - SQUAD / 2f + i, middleY - TILE);
        }
        Rng rng = new Rng(crowd);
        for (int i = 0; i < crowd; i++) {
            simulation.spawnUnit(
                    UnitKind.GRUNT,
                    rng.nextFloat(TILE, map.worldWidth() - TILE),
                    rng.nextFloat(TILE, map.worldHeight() - TILE));
        }
        for (int tick = 0; tick < WARM_UP_TICKS; tick++) {
            simulation.step();
        }

        long start = System.nanoTime();
        for (int tick = 0; tick < MEASURED_TICKS; tick++) {
            simulation.step();
        }
        double milliseconds = (System.nanoTime() - start) / 1e6 / MEASURED_TICKS;

        System.out.printf(
                "BENCH crowd=%d entities=%d ms/step=%.3f%n",
                crowd, simulation.engine().getEntities().size(), milliseconds);
        return milliseconds;
    }
}
