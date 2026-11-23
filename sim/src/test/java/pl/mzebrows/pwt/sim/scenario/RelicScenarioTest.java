package pl.mzebrows.pwt.sim.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.ashley.core.Family;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Emplacement;
import pl.mzebrows.pwt.sim.component.WorkedSite;
import pl.mzebrows.pwt.sim.def.WorkedSiteKind;

/** Relic (G0114): one relic on open sand, its four wardens silent round it, and the squad by the west edge. */
class RelicScenarioTest {

    @Test
    void standsOneRelicWithFourSilentWardensAndTheSquadBesideIt() {
        Scenario scenario = Scenarios.looselyNamed("relic");
        assertThat(scenario).isNotNull();

        Simulation simulation = Scenarios.world(scenario, SimConfig.defaults());
        simulation.step();

        var relics =
                simulation.engine().getEntitiesFor(Family.all(WorkedSite.class).get());
        assertThat(relics.size()).isEqualTo(1);
        assertThat(relics.first().getComponent(WorkedSite.class).kind).isEqualTo(WorkedSiteKind.RELIC);
        var wardens =
                simulation.engine().getEntitiesFor(Family.all(Emplacement.class).get());
        assertThat(wardens.size()).isEqualTo(4);
        for (var warden : wardens) {
            assertThat(warden.getComponent(Emplacement.class).awake).isFalse();
        }
        assertThat(simulation
                        .engine()
                        .getEntitiesFor(Family.all(Commander.class).get())
                        .size())
                .isPositive();
    }
}
