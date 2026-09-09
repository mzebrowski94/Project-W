package pl.mzebrows.pwt.sim.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import java.util.stream.Stream;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pl.mzebrows.pwt.map.Maps;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.AlphaWorlds;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Position;

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

        for (int step = 0; step < 300; step++) {
            simulation.step();
        }

        assertThat(simulation.tick()).as("scenario %s survived 300 ticks", name).isEqualTo(300L);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("registered")
    void spawnsNothingInsideRock(String name, Scenario scenario) {
        Simulation simulation = simulationFor(scenario);
        TileMap map = simulation.map();

        SoftAssertions.assertSoftly(soft -> {
            for (Entity entity : bodies(simulation)) {
                Position position = entity.getComponent(Position.class);
                soft.assertThat(map.isClear(position.x, position.y, entity.getComponent(Body.class).radius))
                        .as(
                                "scenario %s put something at %s,%s, which is in or against rock",
                                name, position.x, position.y)
                        .isTrue();
            }
        });
    }

    /**
     * A level nobody can cross is not a level. Hand-laid walls and scattered rock can both seal a
     * pocket off by accident, and nothing else here would notice - the scenario still spawns, still
     * runs its 300 ticks, and is simply unplayable.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("registered")
    void leavesEverySpawnReachableFromTheCommander(String name, Scenario scenario) {
        Simulation simulation = simulationFor(scenario);
        TileMap map = simulation.map();
        Position commander = simulation
                .engine()
                .getEntitiesFor(Family.all(Commander.class).get())
                .first()
                .getComponent(Position.class);
        boolean[] reachable = Maps.reachableFrom(map, map.colAt(commander.x), map.rowAt(commander.y));

        SoftAssertions.assertSoftly(soft -> {
            for (Entity entity : bodies(simulation)) {
                Position position = entity.getComponent(Position.class);
                int at = map.rowAt(position.y) * map.cols() + map.colAt(position.x);
                soft.assertThat(reachable[at])
                        .as("scenario %s sealed something off at %s,%s", name, position.x, position.y)
                        .isTrue();
            }
        });
    }

    private static ImmutableArray<Entity> bodies(Simulation simulation) {
        return simulation
                .engine()
                .getEntitiesFor(Family.all(Position.class, Body.class).get());
    }

    private static Simulation simulationFor(Scenario scenario) {
        return Scenarios.world(scenario, AlphaWorlds.config());
    }

    private static Stream<Arguments> registered() {
        return Scenarios.all().stream().map(scenario -> Arguments.of(scenario.name(), scenario));
    }
}
