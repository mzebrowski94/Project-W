package pl.mzebrows.pwt.sim.collision;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DOORWAY;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DOORWAY_MIDDLE_X;
import static pl.mzebrows.pwt.sim.AlphaWorlds.WALL_BOTTOM;
import static pl.mzebrows.pwt.sim.AlphaWorlds.WALL_ROW;
import static pl.mzebrows.pwt.sim.AlphaWorlds.WALL_TOP;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.on;
import static pl.mzebrows.pwt.sim.AlphaWorlds.walledAtADoorway;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Door;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;

/**
 * A shut door is solid ground, not a body (ADR 0022): one place answers "solid now?" - the terrain and every shut
 * doorway - and the solver's clamp, a shot into rock, a blast's line and a clear line all ask it. A route still runs
 * through it; nothing here is about routes.
 */
class ShutDoorTest {

    @Test
    void holdsACrowdDrivenIntoAShutDoorAsAWallDoes() {
        Simulation simulation = on(walledAtADoorway());
        simulation.spawnDoor(DOORWAY);
        simulation.spawnCommander(DOORWAY_MIDDLE_X, 46f);
        for (int i = 0; i < 8; i++) {
            simulation.spawnEnemy(DOORWAY_MIDDLE_X - 7f + i * 2f, 40f);
        }

        steps(simulation, 240, 0f, 1f);

        SoftAssertions.assertSoftly(soft -> {
            for (Entity unit : simulation
                    .engine()
                    .getEntitiesFor(Family.all(Position.class, Body.class)
                            .one(Commander.class, Enemy.class)
                            .get())) {
                soft.assertThat(unit.getComponent(Position.class).y)
                        .as("nobody past the shut door")
                        .isLessThan(WALL_BOTTOM);
            }
        });
    }

    @Test
    void letsEverybodyThroughTheDoorwayOnceItsDoorIsOpen() {
        Simulation simulation = on(walledAtADoorway());
        simulation.spawnDoor(DOORWAY).getComponent(Door.class).open = true;
        Entity commander = simulation.spawnCommander(DOORWAY_MIDDLE_X, 46f);

        steps(simulation, 60, 0f, 1f);

        assertThat(commander.getComponent(Position.class).y).isGreaterThan(WALL_TOP);
    }

    @Test
    void stopsAShotThatFliesIntoAShutDoorAtItsFace() {
        Simulation simulation = on(walledAtADoorway());
        simulation.spawnDoor(DOORWAY);
        simulation.spawnProjectile(DOORWAY_MIDDLE_X, 45f, 0f, 1f);

        steps(simulation, 10, 0f, 0f);

        assertThat(count(simulation, Projectile.class)).isZero();
    }

    /** The blast catches nobody behind the door; what it does to the door itself is the door's. */
    @Test
    void stopsABlastsClearLineAtAShutDoor() {
        Simulation simulation = on(walledAtADoorway());
        simulation.spawnDoor(DOORWAY);
        Entity behind = simulation.spawnEnemy(DOORWAY_MIDDLE_X, 60f);
        int health = behind.getComponent(Health.class).current;

        simulation.spawnBlast(DOORWAY_MIDDLE_X, 45f, 20f);
        steps(simulation, 2, 0f, 0f);

        assertThat(behind.getComponent(Health.class).current).isEqualTo(health);
        assertThat(behind.getComponent(Damage.class).landed).isFalse();
    }

    @Test
    void answersEveryTileOfAShutDoorSolidAndAnOpenOneNot() {
        Simulation simulation = on(walledAtADoorway());
        Entity door = simulation.spawnDoor(DOORWAY);
        simulation.step();

        Solid solid = simulation.solid();
        for (int col = DOORWAY.col(); col < DOORWAY.endCol(); col++) {
            assertThat(solid.passableAtTile(col, WALL_ROW)).as("column %d", col).isFalse();
        }
        assertThat(solid.clearLine(DOORWAY_MIDDLE_X, 45f, DOORWAY_MIDDLE_X, 60f))
                .isFalse();
        assertThat(simulation.map().passableAtTile(10, WALL_ROW))
                .as("the map itself still has the doorway")
                .isTrue();

        door.getComponent(Door.class).open = true;
        simulation.step();

        assertThat(solid.passableAtTile(10, WALL_ROW)).isTrue();
        assertThat(solid.clearLine(DOORWAY_MIDDLE_X, 45f, DOORWAY_MIDDLE_X, 60f))
                .isTrue();
    }

    private static void steps(Simulation simulation, int count, float moveX, float moveY) {
        for (int i = 0; i < count; i++) {
            simulation.step(new CommanderInput(moveX, moveY, 0f, 0f, false, false));
        }
    }
}
