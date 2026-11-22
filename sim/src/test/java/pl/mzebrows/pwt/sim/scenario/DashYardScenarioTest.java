package pl.mzebrows.pwt.sim.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Commander;

/** Dash Yard Upgraded (G0108): the yard with Rocketman and Pantera at their last rank for every seat. */
class DashYardScenarioTest {

    @Test
    void handsEverySeatTheirLastRankOfPanteraFromTheFirstTick() {
        Scenario scenario = Scenarios.looselyNamed("dash-yard-upgraded");
        assertThat(scenario).isNotNull();
        Simulation simulation = Scenarios.world(scenario, SimConfig.defaults());
        int most = simulation.config().commander().dash().most();

        simulation.step(new PlayerInputs().join(1));

        Entity first = Players.inSlot(
                simulation.engine().getEntitiesFor(Family.all(Commander.class).get()), 0);
        Entity second = Players.inSlot(
                simulation.engine().getEntitiesFor(Family.all(Commander.class).get()), 1);
        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(simulation.dashMost(first)).isEqualTo(most + 2);
        assertThat(simulation.dashMost(second)).isEqualTo(most + 2);
    }
}
