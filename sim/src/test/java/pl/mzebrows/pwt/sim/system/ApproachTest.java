package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.APPROACH_CLOSE_RANGE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ENEMY_SPEED;
import static pl.mzebrows.pwt.sim.AlphaWorlds.spreading;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;

import com.badlogic.ashley.core.Entity;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Approach;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * A pack does not come at one man down one line.
 *
 * <p>Everything hunting him used to be steered at the same point, and a flow field's gradient in the
 * open is grid-shaped, so a pack crossing open ground converged onto his row and arrived along it,
 * each one shoving the one in front. Each creature now closes on a point of its own beside him.
 *
 * <p>Which side it takes is seeded from where it was spawned rather than rolled (G0012's argument,
 * applied to an approach), so the same level fans out the same way on every run. Most of the tests
 * below set that side outright: <em>which</em> way a particular grunt goes is not the rule, and a test
 * that depended on it would be asserting the seeding function rather than the behaviour. The seeding
 * has two tests of its own at the bottom.
 *
 * <p>The offset is spent by the time anything is close, which is what keeps this a spread rather than
 * an orbit: the last of every approach is straight at him.
 */
class ApproachTest {

    /** Where he stands. Everything walks at him along the lane. */
    private static final float TARGET = 30f;

    private static final float LANE = 50f;

    /** Comfortably past twice the close range, so a creature starting here is at full swing. */
    private static final float START = TARGET + 40f;

    private static final int WATCH = 25;

    /** Inside the close range, where the swing has run out and there is nothing left to be off by. */
    private static final float CLOSE_START = TARGET + APPROACH_CLOSE_RANGE - 2f;

    @Test
    void closesOnAPointBesideHimRatherThanOnHim() {
        Simulation simulation = spreading();
        simulation.spawnCommander(TARGET, LANE);
        Entity grunt = toItsLeft(simulation.spawnUnit(UnitKind.GRUNT, START, LANE));

        steps(simulation, WATCH);

        assertThat(at(grunt).y).isLessThan(LANE - 1f);
    }

    @Test
    void leavesAStrainThatDoesNotSpreadWalkingStraightAtHim() {
        Simulation simulation = spreading();
        simulation.spawnCommander(TARGET, LANE);
        Entity brute = simulation.spawnUnit(UnitKind.BRUTE, START, LANE);

        steps(simulation, WATCH);

        assertThat(at(brute).y).isCloseTo(LANE, Offset.offset(1e-3f));
    }

    /**
     * The half that keeps it a spread rather than an orbit. Something already inside the close range
     * has no swing left and walks at the man himself, however wide it was told to close.
     */
    @Test
    void walksStraightAtHimForTheLastOfTheApproach() {
        Simulation simulation = spreading();
        simulation.spawnCommander(TARGET, LANE);
        Entity grunt = toItsLeft(simulation.spawnUnit(UnitKind.GRUNT, CLOSE_START, LANE));

        simulation.step();

        assertThat(grunt.getComponent(Velocity.class).y).isCloseTo(0f, Offset.offset(1e-3f));
    }

    /** A line to one side rather than a detour: it must cost nothing and buy nothing in pace. */
    @Test
    void keepsTheSamePaceWhileItSpreads() {
        Simulation simulation = spreading();
        simulation.spawnCommander(TARGET, LANE);
        Entity grunt = toItsLeft(simulation.spawnUnit(UnitKind.GRUNT, START, LANE));

        steps(simulation, WATCH);

        Velocity velocity = grunt.getComponent(Velocity.class);
        float pace = (float) Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y);
        assertThat(pace).isCloseTo(ENEMY_SPEED, Offset.offset(0.01f));
    }

    /** The whole point: two of them told apart arrive on opposite sides rather than in a queue. */
    @Test
    void bringsTwoInDownOppositeSidesRatherThanOneBehindTheOther() {
        Simulation simulation = spreading();
        simulation.spawnCommander(TARGET, LANE);
        Entity leading = toItsLeft(simulation.spawnUnit(UnitKind.GRUNT, START, LANE));
        Entity trailing = toItsRight(simulation.spawnUnit(UnitKind.GRUNT, START + 6f, LANE));

        steps(simulation, WATCH);

        assertThat(at(leading).y).isLessThan(LANE);
        assertThat(at(trailing).y).isGreaterThan(LANE);
    }

    /**
     * And nothing is rolled for which side: two put down in different places take different lines,
     * which is what makes a pack fan out without a number being drawn (G0012).
     */
    @Test
    void givesTwoSpawnedApartLinesOfTheirOwn() {
        Simulation simulation = spreading();
        Entity leading = simulation.spawnUnit(UnitKind.GRUNT, START, LANE);
        Entity trailing = simulation.spawnUnit(UnitKind.GRUNT, START + 6f, LANE);

        assertThat(bearing(leading)).isNotEqualTo(bearing(trailing));
    }

    @Test
    void takesTheSameLineOnEveryRun() {
        assertThat(at(runOnce()).y).isEqualTo(at(runOnce()).y);
    }

    private static Entity runOnce() {
        Simulation simulation = spreading();
        simulation.spawnCommander(TARGET, LANE);
        Entity grunt = simulation.spawnUnit(UnitKind.GRUNT, START, LANE);
        steps(simulation, WATCH);
        return grunt;
    }

    /**
     * Sends it round one side at full swing, so a test can say which way it should have gone.
     * Positive is the creature's own left, and everything here is walking west, so its left is the
     * low side of the lane.
     */
    private static Entity toItsLeft(Entity enemy) {
        enemy.getComponent(Approach.class).bearing = 1f;
        return enemy;
    }

    private static Entity toItsRight(Entity enemy) {
        enemy.getComponent(Approach.class).bearing = -1f;
        return enemy;
    }

    private static float bearing(Entity enemy) {
        return enemy.getComponent(Approach.class).bearing;
    }

    private static Position at(Entity entity) {
        return entity.getComponent(Position.class);
    }
}
