package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.ashley.core.Entity;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Position;

class MovementSystemTest {

    private static final float TICK = 1f / 60f;

    private static Simulation simulation() {
        return new Simulation(new SimConfig(1L, TICK, 5, 160f, 90f));
    }

    @Test
    void movesAnEntityByItsVelocityEachStep() {
        Simulation simulation = simulation();
        Entity entity = simulation.spawn(0f, 0f, 60f, 0f, 1f);

        simulation.step();

        Position position = entity.getComponent(Position.class);
        assertThat(position.x).isCloseTo(1f, Offset.offset(1e-5f));
        assertThat(position.y).isZero();
    }

    @Test
    void accumulatesMovementAcrossSteps() {
        Simulation simulation = simulation();
        Entity entity = simulation.spawn(0f, 0f, 60f, 30f, 1f);

        for (int i = 0; i < 60; i++) {
            simulation.step();
        }

        Position position = entity.getComponent(Position.class);
        assertThat(position.x).isCloseTo(60f, Offset.offset(0.01f));
        assertThat(position.y).isCloseTo(30f, Offset.offset(0.01f));
    }

    @Test
    void leavesStationaryEntitiesWhereTheyAre() {
        Simulation simulation = simulation();
        Entity entity = simulation.spawn(10f, 20f, 0f, 0f, 1f);

        simulation.step();

        Position position = entity.getComponent(Position.class);
        assertThat(position.x).isEqualTo(10f);
        assertThat(position.y).isEqualTo(20f);
    }

    @Test
    void countsEveryStep() {
        Simulation simulation = simulation();

        simulation.step();
        simulation.step();

        assertThat(simulation.tick()).isEqualTo(2L);
    }
}
