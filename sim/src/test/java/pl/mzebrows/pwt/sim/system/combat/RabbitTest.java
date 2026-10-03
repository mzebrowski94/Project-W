package pl.mzebrows.pwt.sim.system.combat;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.MELEE_ACTIVE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;
import static pl.mzebrows.pwt.sim.AlphaWorlds.watching;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Feed;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Pickup;
import pl.mzebrows.pwt.sim.component.Stunned;

/**
 * A rabbit is a pickup that runs. Everything about taking one has to work exactly as it does for a
 * mushroom - which is the whole reason it is a pickup and not a third kind of thing - and the one
 * place it differs is that this one can be denied at range.
 */
class RabbitTest {

    private static final float MIDDLE = 50f;

    /** Close enough to be worth crossing to, and far enough that crossing takes visible time. */
    private static final float A_SHORT_WALK = 6f;

    private static final int LONG_ENOUGH_TO_REACH_IT = 30;

    private static final CommanderInput SWING_EAST = new CommanderInput(0f, 0f, 60f, 50f, false, true);

    /** Where the sweep passes through its arc when the commander stands in the middle aiming east. */
    private static final float IN_THE_ARC = 3f;

    private static final int LONG_ENOUGH_FOR_A_SHOT_TO_ARRIVE = 15;

    @Test
    void feedsWhateverCatchesIt() {
        Simulation simulation = watching();
        simulation.spawnRabbit(MIDDLE, MIDDLE);
        Entity grunt = simulation.spawnEnemy(MIDDLE + A_SHORT_WALK, MIDDLE);

        steps(simulation, LONG_ENOUGH_TO_REACH_IT);

        assertThat(grunt.getComponent(Feed.class).count).isEqualTo(1);
    }

    @Test
    void offersTheCommanderNothingAndIsLeftStanding() {
        Simulation simulation = watching();
        Entity rabbit = simulation.spawnRabbit(MIDDLE, MIDDLE);
        simulation.spawnCommander(MIDDLE, MIDDLE);

        steps(simulation, 2);

        assertThat(rabbit.getComponent(Pickup.class).claimed).isFalse();
    }

    /**
     * A sweep reaches one too. A pickup is normally scenery a weapon passes through, and the rabbit is
     * the one that is a target as well as a prize - it would be strange for that to be true of a gun
     * and not of the thing in his other hand.
     */
    @Test
    void canBeDeniedWithTheHandStrike() {
        Simulation simulation = watching();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity rabbit = simulation.spawnRabbit(MIDDLE + IN_THE_ARC, MIDDLE);

        steps(simulation, ticksIn(MELEE_ACTIVE) + 2, SWING_EAST);

        assertThat(rabbit.getComponent(Health.class).current).isLessThanOrEqualTo(0);
        assertThat(commander.getComponent(Stunned.class).active()).isFalse();
    }

    @Test
    void feedsNobodyOnceItHasBeenShot() {
        Simulation simulation = watching();
        simulation.spawnRabbit(MIDDLE, MIDDLE);
        simulation.spawnProjectile(MIDDLE - 10f, MIDDLE, 1f, 0f);
        steps(simulation, LONG_ENOUGH_FOR_A_SHOT_TO_ARRIVE);

        Entity grunt = simulation.spawnEnemy(MIDDLE + 2f, MIDDLE);
        steps(simulation, 3);

        assertThat(grunt.getComponent(Feed.class).count).isZero();
    }
}
