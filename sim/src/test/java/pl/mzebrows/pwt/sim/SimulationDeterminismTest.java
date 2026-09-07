package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Velocity;

/**
 * The load-bearing test of the whole architecture. If this ever goes red, deterministic replay and
 * any future lockstep networking are gone - so treat a failure here as a design bug, not a flake.
 */
class SimulationDeterminismTest {

    private static final SimConfig CONFIG = new SimConfig(20_260_906L, 1f / 60f, 5, 160f, 90f);
    private static final int ENTITIES = 200;
    private static final int STEPS = 600;

    @Test
    void twoRunsFromTheSameSeedProduceIdenticalWorlds() {
        assertThat(runScenario()).isEqualTo(runScenario());
    }

    @Test
    void aDifferentSeedProducesADifferentWorld() {
        SimConfig other = new SimConfig(1L, CONFIG.tickSeconds(), 5, CONFIG.worldWidth(), CONFIG.worldHeight());

        assertThat(runScenario(CONFIG)).isNotEqualTo(runScenario(other));
    }

    private static float[] runScenario() {
        return runScenario(CONFIG);
    }

    private static float[] runScenario(SimConfig config) {
        Simulation simulation = new Simulation(config);
        Rng rng = simulation.rng();

        for (int i = 0; i < ENTITIES; i++) {
            simulation.spawn(
                    rng.nextFloat(0f, config.worldWidth()),
                    rng.nextFloat(0f, config.worldHeight()),
                    rng.nextFloat(-30f, 30f),
                    rng.nextFloat(-30f, 30f),
                    rng.nextFloat(0.5f, 2f));
        }

        for (int step = 0; step < STEPS; step++) {
            simulation.step();
        }

        ImmutableArray<Entity> entities = simulation
                .engine()
                .getEntitiesFor(Family.all(Position.class, Velocity.class).get());
        float[] snapshot = new float[entities.size() * 2];
        for (int i = 0; i < entities.size(); i++) {
            Position position = entities.get(i).getComponent(Position.class);
            snapshot[i * 2] = position.x;
            snapshot[i * 2 + 1] = position.y;
        }
        return snapshot;
    }
}
