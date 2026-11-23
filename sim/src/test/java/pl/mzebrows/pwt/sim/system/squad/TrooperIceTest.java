package pl.mzebrows.pwt.sim.system.squad;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DOORWAY;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DOORWAY_MIDDLE_X;
import static pl.mzebrows.pwt.sim.AlphaWorlds.WALL_TOP;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.everyStrain;
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
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.def.LeapDef;

/**
 * A trooper clears ice in his way (G0117): he walks up to it, stands and shoots it down, then goes on through - but
 * only while nothing of the swarm is his to fight, and never ice his way does not run into.
 */
class TrooperIceTest {

    /** Somewhere the level wants taken, on the far side of the wall: his way runs through the ice. */
    private static final float BEYOND = 80f;

    private static final float BELOW = 30f;

    /** Out of everybody's way, on the near side. */
    private static final float CORNER = 5f;

    @Test
    void shootsDownIceHisWayRunsIntoAndGoesThrough() {
        Simulation simulation = on(walledAtADoorway());
        simulation.spawnControlPoint(DOORWAY_MIDDLE_X, BEYOND);
        simulation.spawnCommander(CORNER, CORNER);
        simulation.spawnIce(DOORWAY);
        Entity trooper = simulation.spawnTrooper(DOORWAY_MIDDLE_X, BELOW);

        steps(simulation, ticksIn(12f));

        assertThat(count(simulation, Door.class)).as("shot down").isZero();
        assertThat(trooper.getComponent(Position.class).y).as("and through").isGreaterThan(WALL_TOP);
    }

    @Test
    void shootsTheSwarmBeforeTheIce() {
        SimConfig harmless =
                everyStrain(config(), strain -> strain.withContactDamage(0).withLeap(LeapDef.off()));
        Simulation simulation = on(harmless, walledAtADoorway());
        simulation.spawnControlPoint(DOORWAY_MIDDLE_X, BEYOND);
        simulation.spawnCommander(CORNER, CORNER);
        Entity ice = simulation.spawnIce(DOORWAY);
        Entity trooper = simulation.spawnTrooper(DOORWAY_MIDDLE_X, BELOW);
        Entity alien = simulation.spawnEnemy(DOORWAY_MIDDLE_X + 20f, BELOW - 10f);
        int full = ice.getComponent(Health.class).current;
        boolean fought = false;

        for (int tick = 0; tick < ticksIn(8f) && alien.getComponent(Health.class).current > 0; tick++) {
            simulation.step();
            fought |= trooper.getComponent(Trooper.class).mode == Trooper.Mode.FIGHT;
            assertThat(ice.getComponent(Health.class).current)
                    .as("the ice untouched while the alien stands")
                    .isEqualTo(full);
        }

        assertThat(fought).as("fought it").isTrue();
    }

    @Test
    void leavesAloneIceHisWayDoesNotRunInto() {
        Simulation simulation = on(walledAtADoorway());
        simulation.spawnControlPoint(DOORWAY_MIDDLE_X + 40f, BELOW);
        simulation.spawnCommander(CORNER, CORNER);
        Entity ice = simulation.spawnIce(DOORWAY);
        Entity trooper = simulation.spawnTrooper(DOORWAY_MIDDLE_X - 20f, BELOW);
        int full = ice.getComponent(Health.class).current;

        steps(simulation, ticksIn(8f));

        assertThat(ice.getComponent(Health.class).current).isEqualTo(full);
        assertThat(trooper.getComponent(Trooper.class).clearingIce).isFalse();
    }
}
