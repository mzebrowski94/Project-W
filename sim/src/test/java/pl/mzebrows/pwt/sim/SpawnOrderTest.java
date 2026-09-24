package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.component.Dead;
import pl.mzebrows.pwt.sim.component.Obstacle;
import pl.mzebrows.pwt.sim.component.Position;

class SpawnOrderTest {

    private static final Family OBSTACLES =
            Family.all(Obstacle.class, Position.class).get();

    @Test
    void keepsTheSurvivorsInSpawnOrderWhenOthersDieOutOfOrder() {
        Simulation simulation = AlphaWorlds.simulation();
        List<Entity> spawned = spawnFive(simulation);
        ImmutableArray<Entity> obstacles = simulation.engine().getEntitiesFor(OBSTACLES);

        kill(simulation, spawned.get(0));
        kill(simulation, spawned.get(2));
        simulation.step();

        List<Entity> survivors = List.of(spawned.get(1), spawned.get(3), spawned.get(4));
        assertThat(listOf(obstacles)).containsExactlyElementsOf(survivors);
        assertThat(listOf(simulation.engine().getEntities())).containsSubsequence(survivors);
    }

    @Test
    void keepsAFamilyAskedForAfterTheWorldWasBuiltInSpawnOrderToo() {
        Simulation simulation = AlphaWorlds.simulation();
        List<Entity> spawned = spawnFive(simulation);
        ImmutableArray<Entity> late =
                simulation.engine().getEntitiesFor(Family.all(Obstacle.class).get());

        kill(simulation, spawned.get(1));
        simulation.step();

        assertThat(listOf(late)).containsExactly(spawned.get(0), spawned.get(2), spawned.get(3), spawned.get(4));
    }

    private static List<Entity> spawnFive(Simulation simulation) {
        List<Entity> spawned = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            spawned.add(simulation.spawnObstacle(20f + i * 10f, 20f));
        }
        simulation.step();
        return spawned;
    }

    private static void kill(Simulation simulation, Entity entity) {
        entity.add(simulation.engine().createComponent(Dead.class));
    }

    private static List<Entity> listOf(ImmutableArray<Entity> entities) {
        List<Entity> list = new ArrayList<>();
        for (int i = 0; i < entities.size(); i++) {
            list.add(entities.get(i));
        }
        return list;
    }
}
