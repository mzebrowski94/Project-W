package pl.mzebrows.pwt.sim.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.ashley.core.Family;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Emplacement;

/** Spire Fen (G0113): three spires on the mire, and the squad by its west edge out of their wake. */
class SpireFenScenarioTest {

    @Test
    void standsThreeSpiresAsleepWithTheSquadOutOfTheirWake() {
        Scenario scenario = Scenarios.looselyNamed("spire-fen");
        assertThat(scenario).isNotNull();

        Simulation simulation = Scenarios.world(scenario, SimConfig.defaults());
        simulation.step();

        var spires =
                simulation.engine().getEntitiesFor(Family.all(Emplacement.class).get());
        assertThat(spires.size()).isEqualTo(3);
        assertThat(simulation
                        .engine()
                        .getEntitiesFor(Family.all(Commander.class).get())
                        .size())
                .isPositive();
        for (var spire : spires) {
            assertThat(spire.getComponent(Emplacement.class).awake).isFalse();
        }
    }
}
