package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import pl.mzebrows.pwt.map.Maps;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * How long one step takes as a crowd grows, and how much it allocates - the numbers `docs/performance.md`
 * asks for before and after anything claimed to be faster.
 *
 * <p>Opt-in, because it measures wall time and is only meaningful on an idle machine: {@code
 * PWT_BENCH=1}. Prints one line per crowd size. Three guards, the last two independent of how fast the
 * machine is: the largest crowd fits one tick (in lockstep every peer pays the whole step); the cost grows
 * with the crowd rather than its square (an O(N²) pair loop is the classic regression); and what a step
 * allocates stays near nothing per entity (an allocation inside a per-entity loop is the other one).
 */
@EnabledIfEnvironmentVariable(named = "PWT_BENCH", matches = "1")
class SimulationBenchmarkTest {

    private static final int[] CROWDS = {100, 200, 400, 800};
    private static final int COLS = 120;
    private static final int ROWS = 60;
    private static final float TILE = 5f;
    private static final int SQUAD = 8;
    private static final int WARM_UP_TICKS = 200;
    private static final int MEASURED_TICKS = 100;

    /** Timed windows per crowd; the fastest is kept, since noise only ever adds time. */
    private static final int WINDOWS = 3;

    /**
     * Eight times the crowd may cost at most this many times the time. Linear is about eight: 7.4 to 9.9
     * over four runs on 2026-09-27. One cheap extra pass over every entity per entity measured 16-17, and
     * the quadratic avoidance fixed on 2026-09-26 was forty.
     */
    private static final double MOST_TIME_GROWTH = 13.0;

    /**
     * What a step may allocate, per entity on the field. Per-tick allocation is allowed and is spread thin
     * at this size; a single object per entity per tick is sixteen bytes at least. A step allocated tens of
     * bytes in all on 2026-09-27.
     */
    private static final double MOST_BYTES_PER_ENTITY = 2.0;

    private static final com.sun.management.ThreadMXBean THREADS =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    private record Sample(int crowd, int entities, double milliseconds, double bytes) {}

    private static final Sample[] SAMPLES = new Sample[CROWDS.length];

    @BeforeAll
    static void measureEveryCrowd() {
        for (int i = 0; i < CROWDS.length; i++) {
            SAMPLES[i] = measure(CROWDS[i]);
        }
    }

    @Test
    void theLargestCrowdStepsInsideOneTick() {
        assertThat(largest().milliseconds()).isLessThan(SimConfig.defaults().tickSeconds() * 1000.0);
    }

    @Test
    void theCostGrowsWithTheCrowdNotItsSquare() {
        assertThat(largest().milliseconds() / SAMPLES[0].milliseconds())
                .as("ms per step at %d grunts over %d", largest().crowd(), SAMPLES[0].crowd())
                .isLessThan(MOST_TIME_GROWTH);
    }

    @Test
    void aStepAllocatesNothingPerEntity() {
        assertThat(largest().bytes() / largest().entities())
                .as("bytes per entity per step at %d grunts", largest().crowd())
                .isLessThan(MOST_BYTES_PER_ENTITY);
    }

    private static Sample largest() {
        return SAMPLES[SAMPLES.length - 1];
    }

    /** A commander nobody can kill, his squad, and a crowd of grunts converging on them from everywhere. */
    private static Sample measure(int crowd) {
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

        long thread = Thread.currentThread().threadId();
        long allocatedBefore = THREADS.getThreadAllocatedBytes(thread);
        double milliseconds = Double.MAX_VALUE;
        for (int window = 0; window < WINDOWS; window++) {
            long start = System.nanoTime();
            for (int tick = 0; tick < MEASURED_TICKS; tick++) {
                simulation.step();
            }
            milliseconds = Math.min(milliseconds, (System.nanoTime() - start) / 1e6 / MEASURED_TICKS);
        }
        double bytes =
                (double) (THREADS.getThreadAllocatedBytes(thread) - allocatedBefore) / (MEASURED_TICKS * WINDOWS);

        int entities = simulation.engine().getEntities().size();
        System.out.printf(
                "BENCH crowd=%d entities=%d ms/step=%.3f bytes/step=%.0f%n", crowd, entities, milliseconds, bytes);
        return new Sample(crowd, entities, milliseconds, bytes);
    }
}
