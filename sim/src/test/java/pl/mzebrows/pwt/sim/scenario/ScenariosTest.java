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
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Position;

/**
 * Applies to every scenario in the registry, so a new one cannot be added half-built. These are the
 * things the menu and the harness assume without checking at runtime.
 */
class ScenariosTest {

    /**
     * How many seeds every layout rule below is checked at.
     *
     * <p><strong>One seed is not a check.</strong> A scenario may scatter its rock at random and then
     * hand-place a crate on a tile it likes the look of, and whether those two collide is a property
     * of the seed - so a rule verified at one of them is verified nowhere. The shipped
     * {@code sim.seed} is not this fixture's seed either, which is how a level can be wrong in the
     * game and right in the build.
     */
    private static final int SEEDS = 24;

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
        SoftAssertions.assertSoftly(soft -> {
            for (long seed = 0; seed < SEEDS; seed++) {
                Simulation simulation = simulationFor(scenario, seed);
                TileMap map = simulation.map();
                for (Entity entity : bodies(simulation)) {
                    Position position = entity.getComponent(Position.class);
                    float radius = entity.getComponent(Body.class).radius;
                    soft.assertThat(map.isClear(position.x, position.y, radius))
                            .as(
                                    "scenario %s at seed %s put something at %s,%s, which is in or against rock",
                                    name, seed, position.x, position.y)
                            .isTrue();
                }
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
        SoftAssertions.assertSoftly(soft -> {
            for (long seed = 0; seed < SEEDS; seed++) {
                Simulation simulation = simulationFor(scenario, seed);
                TileMap map = simulation.map();
                Position commander = simulation
                        .engine()
                        .getEntitiesFor(Family.all(Commander.class).get())
                        .first()
                        .getComponent(Position.class);
                boolean[] reachable = Maps.reachableFrom(map, map.colAt(commander.x), map.rowAt(commander.y));
                for (Entity entity : bodies(simulation)) {
                    Position position = entity.getComponent(Position.class);
                    int at = map.rowAt(position.y) * map.cols() + map.colAt(position.x);
                    soft.assertThat(reachable[at])
                            .as(
                                    "scenario %s at seed %s sealed something off at %s,%s",
                                    name, seed, position.x, position.y)
                            .isTrue();
                }
            }
        });
    }

    private static ImmutableArray<Entity> bodies(Simulation simulation) {
        return simulation
                .engine()
                .getEntitiesFor(Family.all(Position.class, Body.class).get());
    }

    private static Simulation simulationFor(Scenario scenario) {
        return simulationFor(scenario, SimConfig.defaults().seed());
    }

    /**
     * The same world a player would get on that seed - and from the <strong>shipped</strong> numbers
     * rather than the fixture's.
     *
     * <p>That distinction is the other half of the seed one. The fixture's bodies are round numbers
     * chosen to make arithmetic obvious and are mostly smaller than the ones the game ships; a brute
     * is one world unit across here and two and a half in the game. A level laid out so that nothing
     * touches rock at fixture sizes can have a brute standing in a wall in the game, and no test
     * built on the fixture would ever say so.
     */
    private static Simulation simulationFor(Scenario scenario, long seed) {
        return Scenarios.world(scenario, SimConfig.defaults().withSeed(seed));
    }

    private static Stream<Arguments> registered() {
        return Scenarios.all().stream().map(scenario -> Arguments.of(scenario.name(), scenario));
    }
}
