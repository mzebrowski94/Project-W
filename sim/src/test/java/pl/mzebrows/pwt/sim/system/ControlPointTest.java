package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.POINT_AGGRO_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.POINT_CAPTURE_SECONDS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.POINT_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.harmless;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;
import static pl.mzebrows.pwt.sim.AlphaWorlds.watching;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Level;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.def.Objective;

/**
 * Taking ground, and what the level makes of it.
 *
 * <p>The reset is the rule most worth pinning. Dwell that quietly persists turns a point into a
 * number the player is tracking across minutes, and it removes the only reason an alien has to push
 * a trooper off one.
 */
class ControlPointTest {

    private static final float MIDDLE = 50f;

    /** Far enough away that a unit put here is neither capturing nor within earshot of the point. */
    private static final float ELSEWHERE = 95f;

    private static final Offset<Float> TOLERANCE = Offset.offset(1e-3f);

    @Test
    void takesAPointHeldForLongEnough() {
        Simulation simulation = harmless();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity point = simulation.spawnControlPoint(MIDDLE, MIDDLE);

        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) + 2);

        assertThat(point.getComponent(ControlPoint.class).captured).isTrue();
    }

    @Test
    void leavesAPointNobodyIsStandingOnAlone() {
        Simulation simulation = harmless();
        simulation.spawnCommander(ELSEWHERE, ELSEWHERE);
        Entity point = simulation.spawnControlPoint(MIDDLE, MIDDLE);

        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) + 2);

        assertThat(point.getComponent(ControlPoint.class).captured).isFalse();
        assertThat(point.getComponent(ControlPoint.class).heldSeconds).isZero();
    }

    /** 2.d, and the whole reason holding one is a fight rather than an errand. */
    @Test
    void startsTheCaptureAgainWhenTheLastCapturerLeaves() {
        Simulation simulation = harmless();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity point = simulation.spawnControlPoint(MIDDLE, MIDDLE);
        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS * 0.5f));
        assertThat(point.getComponent(ControlPoint.class).heldSeconds).isGreaterThan(0f);

        commander.getComponent(Position.class).set(ELSEWHERE, ELSEWHERE);
        simulation.step();

        assertThat(point.getComponent(ControlPoint.class).heldSeconds).isZero();
        assertThat(point.getComponent(ControlPoint.class).captured).isFalse();
    }

    /** Pillar 5: a point filling up rides the same channel as a unit filling up, so it is drawn. */
    @Test
    void publishesHowFarAlongACaptureIs() {
        Simulation simulation = harmless();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity point = simulation.spawnControlPoint(MIDDLE, MIDDLE);

        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS * 0.5f));

        assertThat(point.getComponent(Appearance.class).charge).isBetween(0.3f, 0.7f);
    }

    @Test
    void showsATakenPointAsFullRatherThanAsUntouched() {
        Simulation simulation = harmless();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity point = simulation.spawnControlPoint(MIDDLE, MIDDLE);

        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) + 2);

        assertThat(point.getComponent(Appearance.class).charge).isCloseTo(1f, TOLERANCE);
    }

    /** 2.b. Taking ground is loud, and what it wakes comes to look. */
    @Test
    void sendsWhatIsNearbyToSeeWhoIsOnThePoint() {
        Simulation simulation = watching();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnControlPoint(MIDDLE, MIDDLE);
        Entity enemy = simulation.spawnEnemy(MIDDLE + POINT_AGGRO_RADIUS - 2f, MIDDLE);

        simulation.step();

        Perception perception = enemy.getComponent(Perception.class);
        assertThat(perception.provoked || perception.aggro).isTrue();
    }

    @Test
    void leavesWhatIsOutOfEarshotAlone() {
        Simulation simulation = watching();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnControlPoint(MIDDLE, MIDDLE);
        Entity enemy = simulation.spawnEnemy(ELSEWHERE, ELSEWHERE);

        simulation.step();

        assertThat(enemy.getComponent(Perception.class).provoked).isFalse();
    }

    @Test
    void takesNoNoticeOfSomebodyStandingOutsideTheRadius() {
        Simulation simulation = harmless();
        simulation.spawnCommander(MIDDLE + POINT_RADIUS + 3f, MIDDLE);
        Entity point = simulation.spawnControlPoint(MIDDLE, MIDDLE);

        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) + 2);

        assertThat(point.getComponent(ControlPoint.class).captured).isFalse();
    }

    @Test
    void meetsTheCaptureObjectiveOnceEveryPointIsTaken() {
        Simulation simulation = harmless();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnControlPoint(MIDDLE, MIDDLE);
        level(simulation).require(Objective.CAPTURE_POINTS);

        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) + 2);

        assertThat(level(simulation).isMet(Objective.CAPTURE_POINTS)).isTrue();
        assertThat(level(simulation).complete()).isTrue();
    }

    @Test
    void leavesTheCaptureObjectiveUnmetWhileAnyPointIsStillHeldAgainstYou() {
        Simulation simulation = harmless();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnControlPoint(MIDDLE, MIDDLE);
        simulation.spawnControlPoint(ELSEWHERE, ELSEWHERE);
        level(simulation).require(Objective.CAPTURE_POINTS);

        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) + 2);

        assertThat(level(simulation).isMet(Objective.CAPTURE_POINTS)).isFalse();
        assertThat(level(simulation).complete()).isFalse();
    }

    /** A map with no points on it has not taken them all - it has nothing to take. */
    @Test
    void neverMeetsTheCaptureObjectiveOnAMapWithNoPoints() {
        Simulation simulation = harmless();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        level(simulation).require(Objective.CAPTURE_POINTS);

        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) + 2);

        assertThat(level(simulation).isMet(Objective.CAPTURE_POINTS)).isFalse();
    }

    @Test
    void meetsTheClearObjectiveOnlyWhenNothingIsLeftStanding() {
        Simulation simulation = harmless();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity enemy = simulation.spawnEnemy(ELSEWHERE, ELSEWHERE);
        level(simulation).require(Objective.CLEAR_ENEMIES);
        simulation.step();
        assertThat(level(simulation).isMet(Objective.CLEAR_ENEMIES)).isFalse();

        enemy.getComponent(pl.mzebrows.pwt.sim.component.Damage.class)
                .add(pl.mzebrows.pwt.sim.AlphaWorlds.ENEMY_HEALTH, 0f);
        simulation.step();

        assertThat(level(simulation).isMet(Objective.CLEAR_ENEMIES)).isTrue();
    }

    /**
     * Both objectives at once, which is the case the whole set-of-objectives shape exists for. Taking
     * the points is not enough while something is still alive to argue about it.
     */
    @Test
    void requiresEveryObjectiveALevelNames() {
        Simulation simulation = harmless();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnControlPoint(MIDDLE, MIDDLE);
        simulation.spawnEnemy(ELSEWHERE, ELSEWHERE);
        level(simulation).require(Objective.CAPTURE_POINTS);
        level(simulation).require(Objective.CLEAR_ENEMIES);

        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) + 2);

        assertThat(level(simulation).isMet(Objective.CAPTURE_POINTS)).isTrue();
        assertThat(level(simulation).complete()).isFalse();
    }

    /** A level that names nothing is never finished, rather than finished before it starts. */
    @Test
    void neverCompletesALevelThatNamesNoObjectives() {
        Simulation simulation = harmless();
        simulation.spawnCommander(MIDDLE, MIDDLE);

        simulation.step();

        assertThat(level(simulation).complete()).isFalse();
    }

    private static Level level(Simulation simulation) {
        return simulation
                .engine()
                .getEntitiesFor(Family.all(Level.class).get())
                .get(0)
                .getComponent(Level.class);
    }
}
