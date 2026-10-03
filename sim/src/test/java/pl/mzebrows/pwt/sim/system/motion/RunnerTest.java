package pl.mzebrows.pwt.sim.system.motion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ENEMY_SPEED;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.weaving;

import com.badlogic.ashley.core.Entity;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * The runner does not come at you in a straight line. The weave is a function of the ground it has
 * covered and of nothing else - no clock, no dice (G0012) - so it is learnable in the way a dodge
 * is, and two of them spawned in different places are out of step with each other without anything
 * being rolled.
 */
class RunnerTest {

    /** Where he stands. Everything walks at him along the lane. */
    private static final float TARGET = 40f;

    private static final float LANE = 50f;

    /** Far enough that nothing arrives during a test, near enough to stay inside the arena. */
    private static final float START = TARGET + 35f;

    private static final int WATCH = 40;

    @Test
    void neverRunsStraightAtHim() {
        Simulation simulation = weaving();
        simulation.spawnCommander(TARGET, LANE);
        Entity runner = simulation.spawnUnit(UnitKind.RUNNER, START, LANE);

        assertThat(worstDeviation(simulation, runner)).isGreaterThan(0.5f);
    }

    @Test
    void leavesAStrainThatDoesNotWeaveWalkingStraight() {
        Simulation simulation = weaving();
        simulation.spawnCommander(TARGET, LANE);
        Entity grunt = simulation.spawnUnit(UnitKind.GRUNT, START, LANE);

        steps(simulation, WATCH);

        assertThat(at(grunt).y).isCloseTo(LANE, Offset.offset(1e-3f));
    }

    /** A weave is a course, not a burst. It must cost nothing and buy nothing in pace. */
    @Test
    void keepsTheSamePaceWhileItWeaves() {
        Simulation simulation = weaving();
        simulation.spawnCommander(TARGET, LANE);
        Entity runner = simulation.spawnUnit(UnitKind.RUNNER, START, LANE);

        steps(simulation, WATCH);

        Velocity velocity = runner.getComponent(Velocity.class);
        float pace = (float) Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y);
        assertThat(pace).isCloseTo(ENEMY_SPEED, Offset.offset(0.01f));
    }

    @Test
    void weavesTheSameWayOnEveryRun() {
        Entity first = runOnce();
        Entity second = runOnce();

        assertThat(at(first).y).isEqualTo(at(second).y);
    }

    /**
     * Two of them are out of step without anything being rolled: the phase comes from where the unit
     * was spawned, so a pack arrives as a scatter rather than as a chorus line.
     */
    @Test
    void doesNotWeaveInLockstepWithAnother() {
        Simulation simulation = weaving();
        simulation.spawnCommander(TARGET, LANE);
        Entity leading = simulation.spawnUnit(UnitKind.RUNNER, START, LANE);
        Entity trailing = simulation.spawnUnit(UnitKind.RUNNER, START + 7f, LANE);

        steps(simulation, WATCH);

        assertThat(at(leading).y).isNotCloseTo(at(trailing).y, within(0.2f));
    }

    private static Entity runOnce() {
        Simulation simulation = weaving();
        simulation.spawnCommander(TARGET, LANE);
        Entity runner = simulation.spawnUnit(UnitKind.RUNNER, START, LANE);
        steps(simulation, WATCH);
        return runner;
    }

    private static Position at(Entity entity) {
        return entity.getComponent(Position.class);
    }

    /** How far off the straight line it ever gets, which is the thing a weave is for. */
    private static float worstDeviation(Simulation simulation, Entity runner) {
        float worst = 0f;
        for (int tick = 0; tick < WATCH; tick++) {
            simulation.step();
            float off = Math.abs(at(runner).y - LANE);
            if (off > worst) {
                worst = off;
            }
        }
        return worst;
    }
}
