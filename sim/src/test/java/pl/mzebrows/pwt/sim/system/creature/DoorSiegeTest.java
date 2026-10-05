package pl.mzebrows.pwt.sim.system.creature;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DOORWAY;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DOORWAY_MIDDLE_X;
import static pl.mzebrows.pwt.sim.AlphaWorlds.WALL_BOTTOM;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.everyStrain;
import static pl.mzebrows.pwt.sim.AlphaWorlds.godMode;
import static pl.mzebrows.pwt.sim.AlphaWorlds.leapConfig;
import static pl.mzebrows.pwt.sim.AlphaWorlds.on;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;
import static pl.mzebrows.pwt.sim.AlphaWorlds.walledAtADoorway;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Door;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Shake;
import pl.mzebrows.pwt.sim.def.LeapDef;

/**
 * A creature shut out leaps at the door (G0069, amending G0026): it hunts the man behind it by route, the route
 * runs through the door, and a hunter whose way a door shuts leaps at the door - lands its blow, is thrown back,
 * shakes the ground, and goes again until the door gives. Nothing asks who is inside.
 */
class DoorSiegeTest {

    /** Inside: beyond the wall, where the creature cannot see him and its route still leads. */
    private static final float INSIDE = 75f;

    private static final float OUTSIDE = 25f;

    @Test
    void leapsAtTheDoorBetweenItAndTheManItHuntsAndIsThrownBack() {
        Simulation simulation = besieging(leapConfig());
        Entity door = simulation.spawnDoor(DOORWAY);
        Entity creature = simulation.spawnEnemy(DOORWAY_MIDDLE_X, OUTSIDE);
        int full = door.getComponent(Health.class).current;

        int tick = 0;
        while (door.getComponent(Health.class).current == full && tick++ < ticksIn(5f)) {
            simulation.step();
        }

        assertThat(door.getComponent(Health.class).current)
                .as("one leap's worth off it")
                .isEqualTo(full - simulation.config().door().leapDamage());
        assertThat(door.getComponent(Shake.class).fade())
                .as("the ground shaken")
                .isPositive();
        steps(simulation, ticksIn(0.2f));
        assertThat(creature.getComponent(Position.class).y)
                .as("thrown back off it")
                .isLessThan(WALL_BOTTOM - 2f);
    }

    @Test
    void breaksTheDoorInTheEnd() {
        Simulation simulation = besieging(leapConfig());
        simulation.spawnDoor(DOORWAY);
        simulation.spawnEnemy(DOORWAY_MIDDLE_X, OUTSIDE);

        steps(simulation, ticksIn(30f));

        assertThat(count(simulation, Door.class)).isZero();
    }

    @Test
    void waitsAtTheDoorWhenItCannotLeap() {
        Simulation simulation = besieging(LeapDef.off());
        Entity door = simulation.spawnDoor(DOORWAY);
        simulation.spawnEnemy(DOORWAY_MIDDLE_X, OUTSIDE);

        steps(simulation, ticksIn(10f));

        assertThat(door.getComponent(Health.class).current)
                .isEqualTo(simulation.config().door().health());
    }

    /** A wall with a door in it, a commander behind it who cannot be hurt, and creatures that leap so. */
    private static Simulation besieging(LeapDef leap) {
        SimConfig leaping =
                everyStrain(config(), strain -> strain.withContactDamage(0).withLeap(leap));
        Simulation simulation = on(leaping, walledAtADoorway());
        simulation.spawnCommander(DOORWAY_MIDDLE_X, INSIDE);
        godMode(simulation);
        return simulation;
    }
}
