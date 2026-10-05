package pl.mzebrows.pwt.sim.system.level;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DOORWAY;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DOORWAY_MIDDLE_X;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TROOPER_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.WALL_BOTTOM;
import static pl.mzebrows.pwt.sim.AlphaWorlds.WALL_ROW;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.on;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;
import static pl.mzebrows.pwt.sim.AlphaWorlds.walledAtADoorway;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Action;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Door;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.def.ActionKind;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * A door is a wall that opens (G0069): opened by hand on stabilise's rules, broken by a blast, a leap or a heavy
 * gun and by nothing else, and gone once it is open or broken.
 */
class DoorTest {

    /** Arm's length from the door's face, standing below it. */
    private static final float AT_THE_DOOR = WALL_BOTTOM - 2f;

    private static final CommanderInput OPENING = CommanderInput.IDLE.acting(true);

    /** Fire held at the middle of the doorway, from wherever he stands below it. */
    private static final CommanderInput FIRING_AT_IT =
            new CommanderInput(0f, 0f, DOORWAY_MIDDLE_X, WALL_BOTTOM + 2.5f, true, false);

    // ---- broken ----

    @Test
    void breaksOutrightUnderABlast() {
        Simulation simulation = on(walledAtADoorway());
        simulation.spawnDoor(DOORWAY);

        simulation.spawnBlast(DOORWAY_MIDDLE_X, WALL_BOTTOM - 5f, 8f);
        steps(simulation, 3);

        assertThat(count(simulation, Door.class)).isZero();
        assertThat(simulation.solid().passableAtTile(DOORWAY.col() + 1, WALL_ROW))
                .isTrue();
    }

    /** The rocket ends at the door's face and its blast is on this side of it - and reaches the door. */
    @Test
    void breaksOutrightUnderARocket() {
        Simulation simulation = on(walledAtADoorway());
        simulation.spawnDoor(DOORWAY);
        holding(simulation, WeaponKind.BAZOOKA, WALL_BOTTOM - 15f);

        simulation.step(FIRING_AT_IT);
        steps(simulation, ticksIn(1f));

        assertThat(count(simulation, Door.class)).isZero();
    }

    @Test
    void standsUnderARiflesFire() {
        Simulation simulation = on(walledAtADoorway());
        Entity door = simulation.spawnDoor(DOORWAY);
        holding(simulation, WeaponKind.SIDEARM, WALL_BOTTOM - 10f);

        steps(simulation, ticksIn(3f), FIRING_AT_IT);

        assertThat(door.getComponent(Health.class).current)
                .isEqualTo(simulation.config().door().health());
        assertThat(count(simulation, Door.class)).isOne();
    }

    @Test
    void breaksUnderAHeavyGun() {
        Simulation simulation = on(walledAtADoorway());
        simulation.spawnDoor(DOORWAY);
        holding(simulation, WeaponKind.FLAMER, WALL_BOTTOM - 8f);

        steps(simulation, ticksIn(3f), FIRING_AT_IT);

        assertThat(count(simulation, Door.class)).isZero();
    }

    // ---- opened ----

    @Test
    void offersToOpenAShutDoorWithinArmsLength() {
        Simulation simulation = on(walledAtADoorway());
        simulation.spawnDoor(DOORWAY);
        Entity commander = simulation.spawnCommander(DOORWAY_MIDDLE_X, AT_THE_DOOR);

        simulation.step();

        assertThat(commander.getComponent(Action.class).available).isEqualTo(ActionKind.OPEN_DOOR);
    }

    @Test
    void opensOnTheActionKeyHeldAtItForOpenSeconds() {
        Simulation simulation = on(walledAtADoorway());
        simulation.spawnDoor(DOORWAY);
        simulation.spawnCommander(DOORWAY_MIDDLE_X, AT_THE_DOOR);
        int open = ticksIn(simulation.config().door().openSeconds());

        steps(simulation, open - 1, OPENING);
        assertThat(simulation.solid().passableAtTile(DOORWAY.col() + 1, WALL_ROW))
                .as("a tick short")
                .isFalse();

        simulation.step(OPENING);
        assertThat(simulation.solid().passableAtTile(DOORWAY.col() + 1, WALL_ROW))
                .as("open ground the tick the work is done")
                .isTrue();
    }

    @Test
    void losesTheWorkWhenHeLetsGo() {
        Simulation simulation = on(walledAtADoorway());
        Entity door = simulation.spawnDoor(DOORWAY);
        simulation.spawnCommander(DOORWAY_MIDDLE_X, AT_THE_DOOR);
        int open = ticksIn(simulation.config().door().openSeconds());

        steps(simulation, open - 1, OPENING);
        simulation.step();
        steps(simulation, 1, OPENING);

        assertThat(door.getComponent(Door.class).open).isFalse();
        assertThat(door.getComponent(Door.class).progress()).isLessThan(0.1f);
    }

    @Test
    void losesTheWorkWhenAWoundLandsOnHim() {
        Simulation simulation = on(walledAtADoorway());
        Entity door = simulation.spawnDoor(DOORWAY);
        Entity commander = simulation.spawnCommander(DOORWAY_MIDDLE_X, AT_THE_DOOR);
        int open = ticksIn(simulation.config().door().openSeconds());

        steps(simulation, open - 1, OPENING);
        commander.getComponent(Damage.class).land(0, Harm.WOUND);
        simulation.step(OPENING);

        assertThat(door.getComponent(Door.class).open).isFalse();
        assertThat(door.getComponent(Door.class).workTicks).isZero();
    }

    @Test
    void losesTheWorkWhenHeGoesDown() {
        Simulation simulation = on(walledAtADoorway());
        Entity door = simulation.spawnDoor(DOORWAY);
        Entity commander = simulation.spawnCommander(DOORWAY_MIDDLE_X, AT_THE_DOOR);
        int open = ticksIn(simulation.config().door().openSeconds());

        steps(simulation, open - 1, OPENING);
        commander.getComponent(Damage.class).land(0, Harm.DOWN);
        simulation.step(OPENING);

        assertThat(door.getComponent(Door.class).open).isFalse();
        assertThat(door.getComponent(Door.class).workTicks).isZero();
    }

    /** A door is last on a tie (G0069): what was on the list before it keeps the key. */
    @Test
    void offersAManDownBesideHimBeforeADoorAsNear() {
        Simulation simulation = on(walledAtADoorway());
        simulation.spawnDoor(DOORWAY);
        Entity commander = simulation.spawnCommander(DOORWAY_MIDDLE_X, AT_THE_DOOR);
        Entity trooper = simulation.spawnTrooper(DOORWAY_MIDDLE_X, AT_THE_DOOR - 5f);
        trooper.getComponent(Damage.class).land(0, Harm.DOWN);
        simulation.step();

        // As far from his surface as the door is: the door's face is 2 from his middle, each man is 1 across.
        trooper.getComponent(Position.class).set(DOORWAY_MIDDLE_X, AT_THE_DOOR - 2f - TROOPER_RADIUS);
        simulation.step();

        assertThat(commander.getComponent(Action.class).available).isEqualTo(ActionKind.STABILISE);
    }

    @Test
    void leavesTheFieldOnceItHasSunk() {
        Simulation simulation = on(walledAtADoorway());
        simulation.spawnDoor(DOORWAY);
        simulation.spawnCommander(DOORWAY_MIDDLE_X, AT_THE_DOOR);
        int open = ticksIn(simulation.config().door().openSeconds());
        int sink = ticksIn(simulation.config().door().sinkSeconds());

        steps(simulation, open, OPENING);
        assertThat(count(simulation, Door.class)).as("sinking, seen").isOne();

        steps(simulation, sink + 1);
        assertThat(count(simulation, Door.class)).as("gone").isZero();
    }

    // ---- seen ----

    /** The bar is the door's (G0069): green, over it, filling while he holds the key - nothing fills on him. */
    @Test
    void showsItsOwnBarFillingWhileItIsOpened() {
        Simulation simulation = on(walledAtADoorway());
        Entity door = simulation.spawnDoor(DOORWAY);
        Entity commander = simulation.spawnCommander(DOORWAY_MIDDLE_X, AT_THE_DOOR);

        steps(simulation, ticksIn(1f), OPENING);

        Appearance seen = door.getComponent(Appearance.class);
        assertThat(seen.chargeLook).isEqualTo(Look.MENDING);
        assertThat(seen.charge).isBetween(0.2f, 0.5f);
        assertThat(commander.getComponent(Appearance.class).charge)
                .as("nothing on him")
                .isZero();
    }

    @Test
    void isSeenSinkingOnceOpened() {
        Simulation simulation = on(walledAtADoorway());
        Entity door = simulation.spawnDoor(DOORWAY);
        simulation.spawnCommander(DOORWAY_MIDDLE_X, AT_THE_DOOR);
        int open = ticksIn(simulation.config().door().openSeconds());
        int sink = ticksIn(simulation.config().door().sinkSeconds());

        steps(simulation, open, OPENING);
        steps(simulation, sink / 2);

        assertThat(door.getComponent(Appearance.class).squareFill).isStrictlyBetween(0f, 1f);
    }

    private static Entity holding(Simulation simulation, WeaponKind gun, float y) {
        Entity commander = simulation.spawnCommander(DOORWAY_MIDDLE_X, y);
        commander
                .getComponent(Weapon.class)
                .set(gun, simulation.config().weapon(gun), simulation.config().tickSeconds());
        return commander;
    }
}
