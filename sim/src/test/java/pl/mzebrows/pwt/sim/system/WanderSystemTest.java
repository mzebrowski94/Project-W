package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DETECTION_RANGE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.WANDER_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.wandering;
import static pl.mzebrows.pwt.sim.AlphaWorlds.watching;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Position;

class WanderSystemTest {

    private static final float MIDDLE = 50f;
    private static final int A_WHILE = 300;
    private static final int A_LONG_WHILE = 900;

    /** How far past its radius a unit may drift: it turns for home on arrival, it does not stop dead. */
    private static final float SLACK = 5f;

    /** Well outside its patch, so walking home is a journey rather than a step. */
    private static final float DRAGGED = 30f;

    @Test
    void movesAnIdleUnitAwayFromWhereItWasSpawned() {
        Simulation simulation = wandering();
        Entity enemy = simulation.spawnEnemy(MIDDLE, MIDDLE);

        steps(simulation, A_WHILE);

        assertThat(distanceFrom(enemy, MIDDLE, MIDDLE)).isGreaterThan(0f);
    }

    @Test
    void keepsAnIdleUnitNearWhereItWasSpawned() {
        Simulation simulation = wandering();
        Entity enemy = simulation.spawnEnemy(MIDDLE, MIDDLE);

        for (int i = 0; i < A_LONG_WHILE; i++) {
            simulation.step();
            assertThat(distanceFrom(enemy, MIDDLE, MIDDLE)).isLessThan(WANDER_RADIUS + SLACK);
        }
    }

    @Test
    void standsStillWhenWanderingIsSwitchedOff() {
        Simulation simulation = watching();
        Entity enemy = simulation.spawnEnemy(MIDDLE, MIDDLE);

        steps(simulation, A_WHILE);

        assertThat(distanceFrom(enemy, MIDDLE, MIDDLE)).isZero();
    }

    @Test
    void leavesAUnitThatIsHuntingToTheHunt() {
        Simulation simulation = wandering();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity enemy = simulation.spawnEnemy(MIDDLE + DETECTION_RANGE - 1f, MIDDLE);

        steps(simulation, 30);

        assertThat(distanceFrom(enemy, MIDDLE, MIDDLE)).isLessThan(DETECTION_RANGE - 1f);
    }

    @Test
    void walksBackToItsSpawnPointWhenItFindsItselfAwayFromIt() {
        Simulation simulation = wandering();
        Entity enemy = simulation.spawnEnemy(MIDDLE, MIDDLE);
        enemy.getComponent(Position.class).set(MIDDLE + DRAGGED, MIDDLE);

        steps(simulation, A_LONG_WHILE);

        assertThat(distanceFrom(enemy, MIDDLE, MIDDLE)).isLessThan(WANDER_RADIUS + SLACK);
    }

    private static float distanceFrom(Entity entity, float x, float y) {
        Position position = entity.getComponent(Position.class);
        float dx = position.x - x;
        float dy = position.y - y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
