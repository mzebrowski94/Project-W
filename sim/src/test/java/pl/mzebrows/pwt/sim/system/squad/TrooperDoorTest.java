package pl.mzebrows.pwt.sim.system.squad;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DOORWAY;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DOORWAY_MIDDLE_X;
import static pl.mzebrows.pwt.sim.AlphaWorlds.WALL_BOTTOM;
import static pl.mzebrows.pwt.sim.AlphaWorlds.WALL_ROW;
import static pl.mzebrows.pwt.sim.AlphaWorlds.WALL_TOP;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.on;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;
import static pl.mzebrows.pwt.sim.AlphaWorlds.walledAtADoorway;

import com.badlogic.ashley.core.Entity;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Door;
import pl.mzebrows.pwt.sim.component.Handle;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Trooper;

/**
 * A trooper opens whatever door his way runs into, on his own (G0069): one trooper a door, the first there, and
 * the rest wait by it and keep a lookout. A commander and that trooper together are no faster than one.
 */
class TrooperDoorTest {

    /** Somewhere the level wants taken, on the far side of the wall: every man's way runs through the door. */
    private static final float BEYOND = 80f;

    private static final float BELOW = 30f;

    /** Out of everybody's way, on the near side. */
    private static final float CORNER = 5f;

    @Test
    void opensADoorHisWayRunsIntoAndGoesThrough() {
        Simulation simulation = beyondTheDoor();
        simulation.spawnDoor(DOORWAY);
        Entity trooper = simulation.spawnTrooper(DOORWAY_MIDDLE_X, BELOW);

        steps(simulation, ticksIn(8f));

        assertThat(count(simulation, Door.class)).as("opened, sunk and gone").isZero();
        assertThat(trooper.getComponent(Position.class).y).isGreaterThan(WALL_TOP);
    }

    @Test
    void givesTheDoorToOneTrooperAndTheOthersWatchTheirSectors() {
        Simulation simulation = beyondTheDoor();
        Entity door = simulation.spawnDoor(DOORWAY);
        List<Entity> squad = List.of(
                simulation.spawnTrooper(DOORWAY_MIDDLE_X - 5f, BELOW),
                simulation.spawnTrooper(DOORWAY_MIDDLE_X, BELOW),
                simulation.spawnTrooper(DOORWAY_MIDDLE_X + 5f, BELOW));

        for (int tick = 0; tick < ticksIn(3f) && door.getComponent(Door.class).trooper == Handle.NOBODY; tick++) {
            simulation.step();
        }
        steps(simulation, 10);

        long opener = door.getComponent(Door.class).trooper;
        assertThat(opener).as("gone to one of them").isNotEqualTo(Handle.NOBODY);
        assertThat(door.getComponent(Door.class).open).as("still being opened").isFalse();
        for (Entity man : squad) {
            if (Handles.of(man) == opener) {
                continue;
            }
            Trooper waiting = man.getComponent(Trooper.class);
            assertThat(waiting.door).as("his way still shut by it").isEqualTo(Handles.of(door));
            assertThat(waiting.sectorSpan).as("watching a sector").isPositive();
            assertThat(man.getComponent(Position.class).y).isLessThan(WALL_BOTTOM);
        }
    }

    @Test
    void opensItNoFasterWithACommanderAtItToo() {
        Simulation simulation = on(walledAtADoorway());
        simulation.spawnControlPoint(DOORWAY_MIDDLE_X, BEYOND);
        Entity door = simulation.spawnDoor(DOORWAY);
        simulation.spawnCommander(DOORWAY_MIDDLE_X + 4f, WALL_BOTTOM - 2f);
        simulation.spawnTrooper(DOORWAY_MIDDLE_X - 4f, WALL_BOTTOM - 2f);
        CommanderInput opening = CommanderInput.IDLE.acting(true);
        int open = ticksIn(simulation.config().door().openSeconds());

        steps(simulation, open - 1, opening);
        assertThat(door.getComponent(Door.class).trooper).as("his too").isNotEqualTo(Handle.NOBODY);
        assertThat(simulation.solid().passableAtTile(DOORWAY.col() + 1, WALL_ROW))
                .isFalse();

        simulation.step(opening);
        assertThat(simulation.solid().passableAtTile(DOORWAY.col() + 1, WALL_ROW))
                .isTrue();
    }

    /** A point beyond a wall with a shut door in it, and a commander out of the way on this side. */
    private static Simulation beyondTheDoor() {
        Simulation simulation = on(walledAtADoorway());
        simulation.spawnControlPoint(DOORWAY_MIDDLE_X, BEYOND);
        simulation.spawnCommander(CORNER, CORNER);
        return simulation;
    }
}
