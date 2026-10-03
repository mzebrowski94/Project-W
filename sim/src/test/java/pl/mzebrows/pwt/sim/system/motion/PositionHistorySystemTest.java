package pl.mzebrows.pwt.sim.system.motion;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.COMMANDER_SPEED;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TICK;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;

import com.badlogic.ashley.core.Entity;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.PreviousPosition;

/**
 * Rendering interpolates between the previous position and the current one. If this history is ever
 * wrong the game does not misbehave - it just looks wrong, which is the kind of bug that survives a
 * long time, so it is worth pinning down here.
 */
class PositionHistorySystemTest {

    private static final Offset<Float> TOLERANCE = Offset.offset(1e-4f);

    @Test
    void remembersWhereAnEntityWasBeforeTheTickMovedIt() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(50f, 50f);

        simulation.step(new CommanderInput(1f, 0f, 0f, 0f, false, false));

        PreviousPosition previous = commander.getComponent(PreviousPosition.class);
        assertThat(previous.x).isCloseTo(50f, TOLERANCE);
        assertThat(commander.getComponent(Position.class).x).isCloseTo(50f + COMMANDER_SPEED * TICK, TOLERANCE);
    }

    @Test
    void movesTheHistoryForwardEveryTick() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(50f, 50f);
        CommanderInput walking = new CommanderInput(1f, 0f, 0f, 0f, false, false);

        simulation.step(walking);
        simulation.step(walking);

        PreviousPosition previous = commander.getComponent(PreviousPosition.class);
        assertThat(previous.x).isCloseTo(50f + COMMANDER_SPEED * TICK, TOLERANCE);
    }

    @Test
    void startsANewEntityWithNoHistoryOfItsOwn() {
        Simulation simulation = simulation();

        Entity enemy = simulation.spawnEnemy(30f, 40f);

        PreviousPosition previous = enemy.getComponent(PreviousPosition.class);
        assertThat(previous.x).isEqualTo(30f);
        assertThat(previous.y).isEqualTo(40f);
    }
}
