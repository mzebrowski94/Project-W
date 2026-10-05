package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pl.mzebrows.pwt.sim.def.TierKind;
import pl.mzebrows.pwt.sim.scenario.Scenario;
import pl.mzebrows.pwt.sim.scenario.Scenarios;

/**
 * The load-bearing test of the whole architecture. If this ever goes red, deterministic replay and
 * lockstep online play are gone - so treat a failure here as a design bug, not a flake.
 *
 * <p>Compares whole worlds - every field of every component and every random stream, as a snapshot
 * writes them (ADR 0014) - not just where things stand: a desync starts in a field nobody draws.
 */
class SimulationDeterminismTest {

    private static final long SEED = 20_260_906L;
    private static final int ENTITIES = 200;
    private static final int STEPS = 600;

    @Test
    void twoRunsFromTheSameSeedProduceIdenticalWorlds() {
        assertThat(runBodies(SEED)).isEqualTo(runBodies(SEED));
    }

    @Test
    void aDifferentSeedProducesADifferentWorld() {
        assertThat(runBodies(SEED)).isNotEqualTo(runBodies(1L));
    }

    /**
     * Covers every scenario the menu can launch, so a new one cannot quietly introduce a
     * non-reproducible spawn. This is the test that actually exercises the systems; the bare-body
     * runs above only exercise movement.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("registeredScenarios")
    void everyScenarioReplaysIdentically(String name, Scenario scenario) {
        assertThat(runScenario(scenario))
                .as("scenario %s is not reproducible", name)
                .isEqualTo(runScenario(scenario));
    }

    /** The menu's, and a mission on hard - the most going on, built from the seed by its own streams (G0065). */
    private static Stream<Arguments> registeredScenarios() {
        Scenario mission = Scenarios.mission(TierKind.HARD);
        return Stream.concat(
                Scenarios.all().stream().map(scenario -> Arguments.of(scenario.name(), scenario)),
                Stream.of(Arguments.of(mission.name(), mission)));
    }

    private static byte[] runBodies(long seed) {
        Simulation simulation = new Simulation(configSeeded(seed));
        Rng rng = simulation.rng();

        for (int i = 0; i < ENTITIES; i++) {
            AlphaWorlds.spawnBody(
                    simulation,
                    rng.nextFloat(0f, simulation.map().worldWidth()),
                    rng.nextFloat(0f, simulation.map().worldHeight()),
                    rng.nextFloat(-30f, 30f),
                    rng.nextFloat(-30f, 30f),
                    rng.nextFloat(0.5f, 2f));
        }

        for (int step = 0; step < STEPS; step++) {
            simulation.step();
        }
        return WorldCodec.snapshot(simulation);
    }

    private static byte[] runScenario(Scenario scenario) {
        Simulation simulation = Scenarios.world(scenario, configSeeded(SEED));

        for (int step = 0; step < STEPS; step++) {
            simulation.step(ScriptedPlay.input(step));
        }
        return WorldCodec.snapshot(simulation);
    }

    /**
     * The shipped numbers, seeded for the replay. Restating them here as a literal would mean this
     * test stopped covering what the game actually runs the first time one of them changed.
     */
    private static SimConfig configSeeded(long seed) {
        return SimConfig.defaults().withSeed(seed);
    }
}
