package pl.mzebrows.pwt.sim.system.motion;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.rabbits;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Position;

/**
 * The dodge is a promise to the player: the same shot from the same place gets the same answer, every
 * time, so leading a rabbit is a skill rather than a coin toss. Every test here is about that promise
 * holding, or about the one thing that limits it - a rabbit committed to one dodge cannot answer the
 * next shot until it lands.
 */
class DodgeSystemTest {

    private static final float LANE = 50f;
    private static final float RABBIT_X = 60f;
    private static final float MUZZLE_X = 40f;

    /** Long enough for a shot to arrive and for the sidestep it provoked to have played out. */
    private static final int UNTIL_IT_ARRIVES = 30;

    private static final float A_CLEAR_SIDESTEP = 1f;

    @Test
    void stepsAsideFromAShotThatWouldHitIt() {
        Simulation simulation = rabbits();
        Entity rabbit = simulation.spawnRabbit(RABBIT_X, LANE);
        simulation.spawnProjectile(MUZZLE_X, LANE, 1f, 0f);

        steps(simulation, UNTIL_IT_ARRIVES);

        assertThat(offLane(rabbit)).isGreaterThan(A_CLEAR_SIDESTEP);
    }

    @Test
    void ignoresAShotThatWasNeverGoingToHitIt() {
        Simulation simulation = rabbits();
        Entity rabbit = simulation.spawnRabbit(RABBIT_X, LANE);
        simulation.spawnProjectile(MUZZLE_X, LANE - 20f, 1f, 0f);

        steps(simulation, UNTIL_IT_ARRIVES);

        assertThat(offLane(rabbit)).isLessThan(A_CLEAR_SIDESTEP);
    }

    @Test
    void ignoresAShotAlreadyPastIt() {
        Simulation simulation = rabbits();
        Entity rabbit = simulation.spawnRabbit(RABBIT_X, LANE);
        simulation.spawnProjectile(RABBIT_X + 10f, LANE, 1f, 0f);

        steps(simulation, UNTIL_IT_ARRIVES);

        assertThat(offLane(rabbit)).isLessThan(A_CLEAR_SIDESTEP);
    }

    @Test
    void answersTheSameShotTheSameWayEveryTime() {
        assertThat(sidestepFrom(LANE + 2f)).isEqualTo(sidestepFrom(LANE + 2f));
        assertThat(sidestepFrom(LANE - 2f)).isEqualTo(sidestepFrom(LANE - 2f));
    }

    @Test
    void breaksAwayFromTheSideTheShotIsOn() {
        assertThat(sidestepFrom(LANE + 2f)).isLessThan(0f);
        assertThat(sidestepFrom(LANE - 2f)).isGreaterThan(0f);
    }

    @Test
    void willNotAnswerASecondShotWhileItIsCommittedToTheFirst() {
        Simulation simulation = rabbits();
        Entity rabbit = simulation.spawnRabbit(RABBIT_X, LANE);
        simulation.spawnProjectile(MUZZLE_X, LANE, 1f, 0f);
        steps(simulation, 2);
        float committed = signedOffLane(rabbit);

        simulation.spawnProjectile(RABBIT_X, LANE - 20f, 0f, 1f);
        steps(simulation, 6);

        assertThat(signedOffLane(rabbit) - committed).isGreaterThan(A_CLEAR_SIDESTEP);
    }

    /** How far off the lane a rabbit ends up when a shot comes down it from a given height. */
    private static float sidestepFrom(float muzzleY) {
        Simulation simulation = rabbits();
        Entity rabbit = simulation.spawnRabbit(RABBIT_X, LANE);
        simulation.spawnProjectile(MUZZLE_X, muzzleY, 1f, 0f);

        steps(simulation, UNTIL_IT_ARRIVES);

        return signedOffLane(rabbit);
    }

    private static float signedOffLane(Entity entity) {
        return entity.getComponent(Position.class).y - LANE;
    }

    private static float offLane(Entity entity) {
        return Math.abs(signedOffLane(entity));
    }
}
