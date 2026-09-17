package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.avoiding;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.def.PickupKind;

class AvoidanceSystemTest {

    private static final float LANE = 50f;
    private static final float START = 20f;
    private static final float BLOCKER = 50f;
    private static final float TARGET = 80f;

    /** Far enough short of the blocker that a unit only touching it would still be dead straight. */
    private static final int WELL_BEFORE_CONTACT = 40;

    private static final float A_CLEAR_DEVIATION = 1f;

    @Test
    void leansOffALineThatWouldRunIntoSomethingLongBeforeItGetsThere() {
        Simulation simulation = avoiding();
        simulation.spawnCommander(TARGET, LANE);
        Entity enemy = simulation.spawnEnemy(START, LANE);
        simulation.spawnObstacle(BLOCKER, LANE);

        steps(simulation, WELL_BEFORE_CONTACT);

        assertThat(offLane(enemy)).isGreaterThan(A_CLEAR_DEVIATION);
    }

    @Test
    void walksDeadStraightWhenNothingIsInTheWay() {
        Simulation simulation = avoiding();
        simulation.spawnCommander(TARGET, LANE);
        Entity enemy = simulation.spawnEnemy(START, LANE);

        steps(simulation, WELL_BEFORE_CONTACT);

        assertThat(offLane(enemy)).isLessThan(A_CLEAR_DEVIATION);
    }

    @Test
    void neverAvoidsTheThingItIsHunting() {
        Simulation simulation = avoiding();
        simulation.spawnCommander(TARGET, LANE);
        Entity enemy = simulation.spawnEnemy(START, LANE);

        steps(simulation, WELL_BEFORE_CONTACT);

        assertThat(enemy.getComponent(Position.class).x).isGreaterThan(START);
    }

    @Test
    void getsPastABlockerRatherThanGrindingIntoIt() {
        Simulation simulation = avoiding();
        simulation.spawnCommander(TARGET, LANE);
        Entity enemy = simulation.spawnEnemy(START, LANE);
        simulation.spawnObstacle(BLOCKER, LANE);

        steps(simulation, 100);

        assertThat(enemy.getComponent(Position.class).x).isGreaterThan(BLOCKER);
    }

    @Test
    void walksStraightOverItsOwnDinner() {
        Simulation simulation = avoiding();
        simulation.spawnCommander(TARGET, LANE);
        Entity enemy = simulation.spawnEnemy(START, LANE);
        simulation.spawnPickup(PickupKind.MUSHROOM, BLOCKER, LANE);

        steps(simulation, WELL_BEFORE_CONTACT);

        assertThat(offLane(enemy)).isLessThan(A_CLEAR_DEVIATION);
    }

    @Test
    void leansOffABarrelAsReadilyAsOffARock() {
        Simulation simulation = avoiding();
        simulation.spawnCommander(TARGET, LANE);
        Entity enemy = simulation.spawnEnemy(START, LANE);
        simulation.spawnBarrel(BLOCKER, LANE);

        steps(simulation, WELL_BEFORE_CONTACT);

        assertThat(offLane(enemy)).isGreaterThan(A_CLEAR_DEVIATION);
    }

    private static float offLane(Entity entity) {
        return Math.abs(entity.getComponent(Position.class).y - LANE);
    }
}
