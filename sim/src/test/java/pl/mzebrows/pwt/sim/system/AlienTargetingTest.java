package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TROOPER_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.shootingStraight;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * Who an alien comes for. It used to be the commander and only the commander, which left a trooper
 * standing alone in the open perfectly safe; now it is whoever on the player's side is nearest, and
 * the commander is on that list rather than above it.
 *
 * <p>Being off your feet takes you off the list, which is the rule that keeps a rescue reachable:
 * nothing stands over a man on the ground, and nothing finishes one it had not noticed.
 */
class AlienTargetingTest {

    private static final float LANE = 50f;

    private static final float WEST = 10f;

    private static final float MIDDLE = 55f;

    private static final float EAST = 75f;

    /** Long enough to see which way it set off, short enough that it has not arrived. */
    private static final int WATCH = 12;

    @Test
    void comesForWhoeverIsNearestRatherThanForTheCommander() {
        Simulation simulation = simulation();
        simulation.spawnCommander(WEST, LANE);
        simulation.spawnTrooper(EAST, LANE);
        Entity grunt = simulation.spawnUnit(UnitKind.GRUNT, MIDDLE, LANE);

        steps(simulation, WATCH);

        assertThat(at(grunt).x).isGreaterThan(MIDDLE);
    }

    @Test
    void comesForTheCommanderWhenHeIsTheNearest() {
        Simulation simulation = simulation();
        simulation.spawnCommander(EAST, LANE);
        simulation.spawnTrooper(WEST, LANE);
        Entity grunt = simulation.spawnUnit(UnitKind.GRUNT, MIDDLE, LANE);

        steps(simulation, WATCH);

        assertThat(at(grunt).x).isGreaterThan(MIDDLE);
    }

    /** Nothing stands over a man on the ground - and that is what leaves somebody able to reach him. */
    @Test
    void leavesAManWhoIsDownForWhoeverIsStillOnHisFeet() {
        Simulation simulation = simulation();
        simulation.spawnCommander(WEST, LANE);
        Entity trooper = simulation.spawnTrooper(EAST, LANE);
        Entity grunt = simulation.spawnUnit(UnitKind.GRUNT, MIDDLE, LANE);

        putDown(trooper);
        steps(simulation, WATCH);

        assertThat(at(grunt).x).isLessThan(MIDDLE);
    }

    @Test
    void takesUpWithWhoeverIsLeftWhenTheOneItWasAfterGoesDown() {
        Simulation simulation = simulation();
        simulation.spawnCommander(WEST, LANE);
        Entity trooper = simulation.spawnTrooper(EAST, LANE);
        Entity grunt = simulation.spawnUnit(UnitKind.GRUNT, MIDDLE, LANE);

        steps(simulation, WATCH);
        float wentEast = at(grunt).x;
        putDown(trooper);
        steps(simulation, WATCH);

        assertThat(wentEast).isGreaterThan(MIDDLE);
        assertThat(at(grunt).x).isLessThan(wentEast);
    }

    /** A gun on the player's side is on the list too: the aliens come and take the guns off you. */
    @Test
    void comesForATurretAsReadilyAsForAMan() {
        Simulation simulation = simulation();
        simulation.spawnCommander(WEST, LANE);
        simulation.spawnTurret(EAST, LANE);
        Entity grunt = simulation.spawnUnit(UnitKind.GRUNT, MIDDLE, LANE);

        steps(simulation, WATCH);

        assertThat(at(grunt).x).isGreaterThan(MIDDLE);
    }

    /** The firing line shoots what it is hunting, and what it is hunting is no longer only him. */
    @Test
    void shootsTheNearestManRatherThanOnlyTheCommander() {
        Simulation simulation = shootingStraight();
        simulation.spawnCommander(WEST, LANE);
        Entity trooper = simulation.spawnTrooper(MIDDLE + 6f, LANE);
        simulation.spawnUnit(UnitKind.SPITTER, MIDDLE, LANE);

        steps(simulation, ticksIn(1.5f));

        assertThat(trooper.getComponent(Health.class).current).isLessThan(TROOPER_HEALTH);
    }

    private static Position at(Entity entity) {
        return entity.getComponent(Position.class);
    }

    private static void putDown(Entity entity) {
        entity.getComponent(Damage.class).add(TROOPER_HEALTH, 0, false);
    }
}
