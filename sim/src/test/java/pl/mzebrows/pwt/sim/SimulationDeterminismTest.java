package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.scenario.Scenario;
import pl.mzebrows.pwt.sim.scenario.Scenarios;

/**
 * The load-bearing test of the whole architecture. If this ever goes red, deterministic replay and
 * any future lockstep networking are gone - so treat a failure here as a design bug, not a flake.
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

    private static Stream<Arguments> registeredScenarios() {
        return Scenarios.all().stream().map(scenario -> Arguments.of(scenario.name(), scenario));
    }

    private static float[] runBodies(long seed) {
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
        return snapshot(simulation);
    }

    private static float[] runScenario(Scenario scenario) {
        Simulation simulation = Scenarios.world(scenario, configSeeded(SEED));

        for (int step = 0; step < STEPS; step++) {
            simulation.step(scriptedInput(step));
        }
        return snapshot(simulation);
    }

    /**
     * A commander walking in a circle, firing most ticks and swinging every so often - enough to
     * exercise every system, the seeded wind-up before a leap among them.
     */
    private static CommanderInput scriptedInput(int step) {
        double angle = step * 0.05;
        return new CommanderInput(
                (float) StrictMath.cos(angle), (float) StrictMath.sin(angle), 80f, 45f, step % 7 != 0, step % 11 == 0);
    }

    /**
     * The shipped numbers, seeded for the replay. Restating them here as a literal would mean this
     * test stopped covering what the game actually runs the first time one of them changed.
     */
    private static SimConfig configSeeded(long seed) {
        return SimConfig.defaults().withSeed(seed);
    }

    private static float[] snapshot(Simulation simulation) {
        ImmutableArray<Entity> entities =
                simulation.engine().getEntitiesFor(Family.all(Position.class).get());
        float[] snapshot = new float[entities.size() * 2];
        for (int i = 0; i < entities.size(); i++) {
            Position position = entities.get(i).getComponent(Position.class);
            snapshot[i * 2] = position.x;
            snapshot[i * 2 + 1] = position.y;
        }
        return snapshot;
    }
}
