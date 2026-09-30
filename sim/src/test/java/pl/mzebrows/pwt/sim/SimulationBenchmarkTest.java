package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import java.lang.management.ManagementFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import pl.mzebrows.pwt.map.Maps;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.component.Upgrades;
import pl.mzebrows.pwt.sim.def.AiChance;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.upgrade.Modifiers;
import pl.mzebrows.pwt.sim.upgrade.UpgradeDef;
import pl.mzebrows.pwt.sim.upgrade.UpgradeKind;

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

    /**
     * The share of the largest crowd's step the fold may take with every upgrade and adaptation held at its
     * top rank, reckoned as {@value #READS_PER_ENTITY} reads per entity per step. Every read folds the
     * ledger afresh (ADR 0017); a cache in front of {@code Modifiers} waits in the backlog for this line.
     */
    private static final double MOST_FOLD_SHARE = 0.10;

    /**
     * Reads per entity per step the fold's share is reckoned at: the pace every driven unit is read for,
     * and a chance. A shot, a hit and a crate are read for far less often.
     */
    private static final int READS_PER_ENTITY = 2;

    /** Passes over the crowd a fold timing takes, half of them a pace and half a chance. */
    private static final int FOLD_PASSES = 200;

    private static final com.sun.management.ThreadMXBean THREADS =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    private record Sample(int crowd, int entities, double milliseconds, double bytes) {}

    /** One read of the port with everything held, timed over the largest crowd, and what it allocated. */
    private record Fold(double nanosPerRead, double bytesPerRead) {}

    private static final Sample[] SAMPLES = new Sample[CROWDS.length];

    private static Fold fold = new Fold(0.0, 0.0);

    @BeforeAll
    static void measureEveryCrowd() {
        for (int i = 0; i < CROWDS.length; i++) {
            SAMPLES[i] = measure(CROWDS[i]);
        }
        fold = measureTheFold(CROWDS[CROWDS.length - 1]);
    }

    @Test
    void theFoldTakesASmallShareOfAStepWithEverythingHeld() {
        double perStep = fold.nanosPerRead() * READS_PER_ENTITY * largest().entities() / 1e6;

        assertThat(perStep / largest().milliseconds())
                .as("the fold's share of a %d-grunt step", largest().crowd())
                .isLessThan(MOST_FOLD_SHARE);
    }

    @Test
    void theFoldAllocatesNothing() {
        assertThat(fold.bytesPerRead()).as("bytes per read of the port").isLessThan(1.0);
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

    /**
     * Every upgrade and adaptation held at its top rank, then the pace and a chance read for every entity
     * of the crowd, over and over: what one read of the port costs when the ledger is as long as it gets.
     * Timed without stepping, so what the upgrades change about the fight is not counted as their cost.
     */
    private static Fold measureTheFold(int crowd) {
        Simulation simulation = crowd(crowd, 10);
        holdEverything(simulation, DraftTest.ledger(simulation));
        Modifiers modifiers = new Modifiers(simulation.engine(), simulation.config());
        ImmutableArray<Entity> entities = simulation.engine().getEntities();
        double sink = 0.0;
        for (int pass = 0; pass < FOLD_PASSES; pass++) {
            sink += readEveryone(modifiers, entities);
        }
        long thread = Thread.currentThread().threadId();
        long allocatedBefore = THREADS.getThreadAllocatedBytes(thread);
        long start = System.nanoTime();
        for (int pass = 0; pass < FOLD_PASSES; pass++) {
            sink += readEveryone(modifiers, entities);
        }
        long nanos = System.nanoTime() - start;
        long bytes = THREADS.getThreadAllocatedBytes(thread) - allocatedBefore;
        double reads = 2.0 * FOLD_PASSES * entities.size();
        System.out.printf(
                "BENCH fold crowd=%d held=%d ns/read=%.1f bytes/read=%.2f (sink %.0f)%n",
                crowd, DraftTest.ledger(simulation).held.size, nanos / reads, bytes / reads, sink);
        return new Fold(nanos / reads, bytes / reads);
    }

    private static double readEveryone(Modifiers modifiers, ImmutableArray<Entity> entities) {
        double sum = 0.0;
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            sum += modifiers.pace(entity, 1f) + modifiers.chance(entity, AiChance.AID_A_TROOPER, 0.5f);
        }
        return sum;
    }

    private static void holdEverything(Simulation simulation, Upgrades ledger) {
        for (UpgradeKind kind : UpgradeKind.values()) {
            UpgradeDef def = simulation.config().upgrades().of(kind);
            ledger.held.add(new Upgrades.Held(kind, def.personal() ? 0 : Upgrades.SHARED, def.ranks()));
        }
    }

    /** A commander nobody can kill, his squad, and a crowd of grunts converging on them from everywhere. */
    private static Sample measure(int crowd) {
        Simulation simulation = crowd(crowd, 0);
        long thread = Thread.currentThread().threadId();
        long allocatedBefore = THREADS.getThreadAllocatedBytes(thread);
        double milliseconds = Double.MAX_VALUE;
        for (int window = 0; window < WINDOWS; window++) {
            milliseconds = Math.min(milliseconds, window(simulation));
        }
        double bytes =
                (double) (THREADS.getThreadAllocatedBytes(thread) - allocatedBefore) / (MEASURED_TICKS * WINDOWS);

        int entities = simulation.engine().getEntities().size();
        System.out.printf(
                "BENCH crowd=%d entities=%d ms/step=%.3f bytes/step=%.0f%n", crowd, entities, milliseconds, bytes);
        return new Sample(crowd, entities, milliseconds, bytes);
    }

    /** Milliseconds per step, over one window. */
    private static double window(Simulation simulation) {
        long start = System.nanoTime();
        for (int tick = 0; tick < MEASURED_TICKS; tick++) {
            simulation.step();
        }
        return (System.nanoTime() - start) / 1e6 / MEASURED_TICKS;
    }

    /**
     * The benchmark's world, warmed up: {@code crowd} aliens converging on a commander and his squad, one
     * in every {@code spittersEvery} a spitter (zero for none).
     */
    private static Simulation crowd(int crowd, int spittersEvery) {
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
                    spittersEvery > 0 && i % spittersEvery == 0 ? UnitKind.SPITTER : UnitKind.GRUNT,
                    rng.nextFloat(TILE, map.worldWidth() - TILE),
                    rng.nextFloat(TILE, map.worldHeight() - TILE));
        }
        for (int tick = 0; tick < WARM_UP_TICKS; tick++) {
            simulation.step();
        }
        return simulation;
    }
}
