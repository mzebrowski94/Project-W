package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentType;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Bits;
import com.badlogic.gdx.utils.ObjectMap;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pl.mzebrows.pwt.sim.scenario.Scenario;
import pl.mzebrows.pwt.sim.scenario.Scenarios;

/**
 * An entity is made of the same components at the end of every tick it lives through (ADR 0013).
 *
 * <p>{@code ArchitectureTest} already says only the {@code Spawner} may change what an entity is made
 * of. This says when: at spawn, and never after - {@code Dead} excepted, which the reaper clears
 * before the tick ends. A component added to a living entity would put it at the end of that family
 * instead of in its spawn place, which no snapshot can reproduce, and nothing but a drop-in would
 * notice.
 */
class CompositionTest {

    private static final int STEPS = 600;

    @ParameterizedTest(name = "{0}")
    @MethodSource("registeredScenarios")
    void keepsWhatEveryEntityIsMadeOfFromTheTickItAppears(String name, Scenario scenario) {
        Simulation simulation = Scenarios.world(scenario, SimConfig.defaults().withSeed(20_260_906L));
        ObjectMap<Entity, Bits> madeOf = new ObjectMap<>();
        simulation.engine().addEntityListener(new EntityListener() {
            @Override
            public void entityAdded(Entity entity) {}

            @Override
            public void entityRemoved(Entity entity) {
                madeOf.remove(entity);
            }
        });

        for (int step = 0; step < STEPS; step++) {
            simulation.step(ScriptedPlay.input(step));
            ImmutableArray<Entity> entities = simulation.engine().getEntities();
            for (int i = 0; i < entities.size(); i++) {
                Entity entity = entities.get(i);
                Bits now = madeOf(entity);
                Bits first = madeOf.get(entity);
                if (first == null) {
                    madeOf.put(entity, now);
                } else {
                    assertThat(now)
                            .as("scenario %s, tick %d: an entity changed what it is made of", name, step)
                            .isEqualTo(first);
                }
            }
        }
    }

    private static Stream<Arguments> registeredScenarios() {
        return Scenarios.all().stream().map(scenario -> Arguments.of(scenario.name(), scenario));
    }

    private static Bits madeOf(Entity entity) {
        Bits bits = new Bits();
        ImmutableArray<Component> components = entity.getComponents();
        for (int i = 0; i < components.size(); i++) {
            bits.set(ComponentType.getIndexFor(components.get(i).getClass()));
        }
        return bits;
    }
}
