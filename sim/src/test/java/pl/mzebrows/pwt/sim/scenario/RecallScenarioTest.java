package pl.mzebrows.pwt.sim.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.ashley.core.Family;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Trooper;

/** Recall (G0121): a hidden scenario that starts on an empty reserve with a few troopers, to die in. */
class RecallScenarioTest {

    @Test
    void startsOnAnEmptyReserveWithFourTroopersAndNoMuster() {
        Scenario scenario = Scenarios.looselyNamed("recall");
        assertThat(scenario).isNotNull();

        Simulation simulation = Scenarios.world(scenario, SimConfig.defaults());

        assertThat(simulation.level().reserve).isZero();
        assertThat(simulation.config().reserve().musterSeconds()).isZero();
        assertThat(simulation
                        .engine()
                        .getEntitiesFor(Family.all(Trooper.class).get())
                        .size())
                .isEqualTo(4);
    }
}
