package pl.mzebrows.pwt.sim.system.motion;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.KNOCKBACK_DECAY;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.spawnSteered;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Steering;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.component.Velocity;

class DriveSystemTest {

    private static final float HELD_SECONDS = 0.5f;

    @Test
    void movesAUnitTheWayItAskedToGo() {
        Simulation simulation = simulation();
        Entity entity = spawnSteered(simulation, 0f, 0f);
        entity.getComponent(Steering.class).set(60f, 0f);

        simulation.step();

        assertThat(entity.getComponent(Position.class).x).isCloseTo(1f, Offset.offset(1e-5f));
    }

    @Test
    void stopsAUnitThatAsksForNothing() {
        Simulation simulation = simulation();
        Entity entity = spawnSteered(simulation, 0f, 0f);
        entity.getComponent(Steering.class).set(60f, 0f);

        simulation.step();
        simulation.step();

        assertThat(entity.getComponent(Velocity.class).x).isZero();
    }

    @Test
    void spendsADesiredVelocityOnTheTickItWasWritten() {
        Simulation simulation = simulation();
        Entity entity = spawnSteered(simulation, 0f, 0f);
        entity.getComponent(Steering.class).set(60f, 0f);

        simulation.step();

        Steering steering = entity.getComponent(Steering.class);
        assertThat(steering.desiredX).isZero();
        assertThat(steering.desiredY).isZero();
    }

    @Test
    void leavesAHeldUnitToWhateverIsHoldingIt() {
        // Driving it would write a zero over the throw, and StunSystem would then have nothing to bleed.
        Simulation simulation = simulation();
        Entity enemy = simulation.spawnEnemy(50f, 50f);
        enemy.getComponent(Velocity.class).set(60f, 0f);
        enemy.getComponent(Stunned.class).apply(ticksIn(HELD_SECONDS), KNOCKBACK_DECAY);

        simulation.step();

        assertThat(enemy.getComponent(Position.class).x).isGreaterThan(50f);
    }
}
