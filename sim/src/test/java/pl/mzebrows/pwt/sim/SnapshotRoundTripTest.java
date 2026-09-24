package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pl.mzebrows.pwt.sim.scenario.Scenario;
import pl.mzebrows.pwt.sim.scenario.Scenarios;

/**
 * A world written down and read back into a blank one is the same world, and stays the same world
 * (ADR 0014). The load-bearing test of drop-in and of healing a desync, and the one that proves every
 * {@code @NotState} cache really is a pure function of its key: the restored world starts with every
 * cache empty, and has to arrive at exactly what the original does.
 */
class SnapshotRoundTripTest {

    private static final int BEFORE = 300;
    private static final int AFTER = 300;

    @ParameterizedTest(name = "{0}")
    @MethodSource("registeredScenarios")
    void restoresAWorldThatStaysInStepWithTheOriginal(String name, Scenario scenario) {
        SimConfig config = SimConfig.defaults().withSeed(20_260_906L);
        Simulation original = Scenarios.world(scenario, config);
        for (int step = 0; step < BEFORE; step++) {
            original.step(ScriptedPlay.input(step));
        }

        Simulation restored = Scenarios.blank(scenario, config);
        WorldCodec.restore(restored, WorldCodec.snapshot(original));

        assertThat(WorldCodec.snapshot(restored))
                .as("%s read back and written again", name)
                .isEqualTo(WorldCodec.snapshot(original));
        assertThat(familyOrders(original, restored))
                .as("%s families", name)
                .isEqualTo(familyOrders(original, original));
        for (int step = BEFORE; step < BEFORE + AFTER; step++) {
            original.step(ScriptedPlay.input(step));
            restored.step(ScriptedPlay.input(step));
            assertThat(WorldCodec.hash(restored))
                    .as("%s at tick %d", name, step)
                    .isEqualTo(WorldCodec.hash(original));
        }
        assertThat(familyOrders(original, restored))
                .as("%s families at the end", name)
                .isEqualTo(familyOrders(original, original));
    }

    private static Stream<Arguments> registeredScenarios() {
        return Scenarios.all().stream().map(scenario -> Arguments.of(scenario.name(), scenario));
    }

    /**
     * Every family the original holds, as the spawn-order positions of its members in the given world.
     * A family the restored world has not asked for yet is built on asking, from the engine's own list -
     * which is how it would be built the first time a system asked for it.
     */
    private static List<String> familyOrders(Simulation original, Simulation world) {
        List<Entity> all = new ArrayList<>();
        for (Entity entity : world.engine().getEntities()) {
            all.add(entity);
        }
        List<String> orders = new ArrayList<>();
        for (Family family : families(original.engine()).keys()) {
            StringBuilder order = new StringBuilder(String.valueOf(family.getIndex())).append(':');
            for (Entity member : world.engine().getEntitiesFor(family)) {
                order.append(all.indexOf(member)).append(',');
            }
            orders.add(order.toString());
        }
        orders.sort(String::compareTo);
        return orders;
    }

    @SuppressWarnings("unchecked")
    private static ObjectMap<Family, Array<Entity>> families(Engine engine) {
        try {
            Field managerField = Engine.class.getDeclaredField("familyManager");
            managerField.setAccessible(true);
            Object manager = managerField.get(engine);
            Field familiesField = manager.getClass().getDeclaredField("families");
            familiesField.setAccessible(true);
            return (ObjectMap<Family, Array<Entity>>) familiesField.get(manager);
        } catch (ReflectiveOperationException e) {
            throw new LinkageError("Ashley's family manager moved", e);
        }
    }
}
