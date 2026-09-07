package pl.mzebrows.pwt.sim.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.ashley.core.Family;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pl.mzebrows.pwt.sim.AlphaWorlds;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Commander;

/**
 * Applies to every scenario in the registry, so a new one cannot be added half-built. These are the
 * things the menu and the harness assume without checking at runtime.
 */
class ScenariosTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("registered")
    void describesItselfForTheMenu(String name, Scenario scenario) {
        assertThat(name).isNotBlank();
        assertThat(scenario.description()).isNotBlank();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("registered")
    void spawnsExactlyOneCommander(String name, Scenario scenario) {
        Simulation simulation = simulationFor(scenario);

        scenario.populate(simulation);

        assertThat(simulation
                        .engine()
                        .getEntitiesFor(Family.all(Commander.class).get()))
                .as("scenario %s must spawn a commander for the player to control", name)
                .hasSize(1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("registered")
    void runsWithoutLeavingAnythingOutsideTheArena(String name, Scenario scenario) {
        Simulation simulation = simulationFor(scenario);
        scenario.populate(simulation);

        for (int step = 0; step < 300; step++) {
            simulation.step();
        }

        assertThat(simulation.tick()).as("scenario %s survived 300 ticks", name).isEqualTo(300L);
    }

    private static Simulation simulationFor(Scenario scenario) {
        SimConfig config = scenario.configure(AlphaWorlds.config());
        return new Simulation(config);
    }

    private static Stream<Arguments> registered() {
        return Scenarios.all().stream().map(scenario -> Arguments.of(scenario.name(), scenario));
    }
}
