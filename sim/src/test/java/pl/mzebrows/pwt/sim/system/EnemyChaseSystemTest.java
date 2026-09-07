package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Velocity;

class EnemyChaseSystemTest {

    @Test
    void movesAnEnemyTowardsTheCommander() {
        Simulation simulation = simulation();
        simulation.spawnCommander(90f, 50f);
        Entity enemy = simulation.spawnEnemy(10f, 50f);

        simulation.step();

        assertThat(enemy.getComponent(Position.class).x).isGreaterThan(10f);
    }

    @Test
    void closesBothAxesAtOnceWhenTheCommanderIsDiagonal() {
        Simulation simulation = simulation();
        simulation.spawnCommander(90f, 90f);
        Entity enemy = simulation.spawnEnemy(10f, 10f);

        simulation.step();

        Position position = enemy.getComponent(Position.class);
        assertThat(position.x).isGreaterThan(10f);
        assertThat(position.y).isGreaterThan(10f);
    }

    @Test
    void standsStillOnceItIsExactlyOnTopOfTheCommander() {
        Simulation simulation = simulation();
        simulation.spawnCommander(50f, 50f);
        Entity enemy = simulation.spawnEnemy(50f, 50f);

        simulation.step();

        Velocity velocity = enemy.getComponent(Velocity.class);
        assertThat(velocity.x).isZero();
        assertThat(velocity.y).isZero();
    }

    @Test
    void leavesEnemiesStandingWhenThereIsNoCommander() {
        Simulation simulation = simulation();
        Entity enemy = simulation.spawnEnemy(10f, 50f);

        simulation.step();

        Velocity velocity = enemy.getComponent(Velocity.class);
        assertThat(velocity.x).isZero();
        assertThat(velocity.y).isZero();
    }
}
