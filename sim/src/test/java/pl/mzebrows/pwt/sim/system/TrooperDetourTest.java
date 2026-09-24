package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ground;
import static pl.mzebrows.pwt.sim.AlphaWorlds.on;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Trooper;

/**
 * What a trooper does when it has stopped getting anywhere.
 *
 * <p>The route knows rock and not entities; steering knows entities and not rock. Where a boulder sits
 * nearly flush against a wall the two are blind together, and a man walked into the slot between them
 * and stayed there for the rest of the level. Asking the route again gives the same route - it is a
 * function of terrain - so what gets him out has to be something else: noticing he has stopped making
 * ground, and backing off to one side for a moment.
 */
class TrooperDetourTest {

    /** A wall along the whole of one row, and the line the trooper walks just under it. */
    private static final int WALL_ROW = 10;

    private static final float LANE = 48f;

    @Test
    void backsOutOfASlotItHasWalkedIntoAndGoesRoundInstead() {
        Simulation simulation =
                on(ground().rect(0, WALL_ROW, 20, 1, Terrain.ROCK).build());
        simulation.spawnCommander(95f, 95f);
        simulation.spawnControlPoint(90f, LANE);
        // Its top edge a unit short of the wall: a slot half as wide as the man walking into it.
        simulation.spawnObstacle(50f, LANE - 1f);
        Entity trooper = simulation.spawnTrooper(10f, LANE);

        steps(simulation, ticksIn(10f));

        assertThat(trooper.getComponent(Position.class).x).isGreaterThan(80f);
    }

    /** A man walking somewhere, and then standing on it, is never mistaken for a man who is stuck. */
    @Test
    void takesNoDetourOnAnOrdinaryWalkOrWhileItStandsStillOnPurpose() {
        Simulation simulation = on(ground().build());
        simulation.spawnCommander(95f, 95f);
        simulation.spawnControlPoint(60f, LANE);
        Entity trooper = simulation.spawnTrooper(10f, LANE);

        steps(simulation, ticksIn(10f));

        assertThat(trooper.getComponent(Trooper.class).mode).isEqualTo(Trooper.Mode.SECURE);
        assertThat(trooper.getComponent(Trooper.class).detours).isZero();
    }
}
