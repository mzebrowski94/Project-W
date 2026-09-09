package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.COMMANDER_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DETECTION_RANGE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.KNOCKBACK_DECAY;
import static pl.mzebrows.pwt.sim.AlphaWorlds.LOSE_RANGE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.watching;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Invulnerable;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Stunned;

class PerceptionSystemTest {

    private static final float MIDDLE = 50f;
    private static final float HELD_SECONDS = 0.5f;

    @Test
    void leavesAUnitUnawareWhileTheCommanderIsOutOfRange() {
        Simulation simulation = watching();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity enemy = simulation.spawnEnemy(MIDDLE + LOSE_RANGE + 1f, MIDDLE);

        simulation.step();

        assertThat(aggro(enemy)).isFalse();
    }

    @Test
    void wakesAUnitWhenTheCommanderComesInsideItsDetectionRange() {
        Simulation simulation = watching();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity enemy = simulation.spawnEnemy(MIDDLE + DETECTION_RANGE - 1f, MIDDLE);

        simulation.step();

        assertThat(aggro(enemy)).isTrue();
    }

    @Test
    void keepsHuntingBetweenTheTwoRangesOnceItHasNoticedHim() {
        Simulation simulation = watching();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity enemy = simulation.spawnEnemy(MIDDLE + DETECTION_RANGE - 1f, MIDDLE);

        simulation.step();
        commander.getComponent(Position.class).set(MIDDLE - LOSE_RANGE + DETECTION_RANGE, MIDDLE);
        simulation.step();

        assertThat(aggro(enemy)).isTrue();
    }

    @Test
    void losesHimOnceHeIsPastTheLoseRange() {
        Simulation simulation = watching();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity enemy = simulation.spawnEnemy(MIDDLE + DETECTION_RANGE - 1f, MIDDLE);

        simulation.step();
        commander.getComponent(Position.class).set(1f, 1f);
        simulation.step();

        assertThat(aggro(enemy)).isFalse();
    }

    @Test
    void neverSleepsWhenTheStrainIsAlwaysAggressive() {
        Simulation simulation = simulation();
        simulation.spawnCommander(1f, 1f);
        Entity enemy = simulation.spawnEnemy(99f, 99f);

        simulation.step();

        assertThat(aggro(enemy)).isTrue();
    }

    @Test
    void losesHimWhenHeGoesDown() {
        Simulation simulation = watching();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity enemy = simulation.spawnEnemy(MIDDLE + DETECTION_RANGE - 1f, MIDDLE);
        simulation.step();

        commander.getComponent(Damage.class).add(COMMANDER_HEALTH, 0f);
        commander.getComponent(Invulnerable.class).remainingSeconds = 0f;
        steps(simulation, 2);

        assertThat(aggro(enemy)).isFalse();
    }

    @Test
    void noticesHimAtExactlyTheDetectionRange() {
        Simulation simulation = watching();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity enemy = simulation.spawnEnemy(MIDDLE + DETECTION_RANGE, MIDDLE);

        simulation.step();

        assertThat(aggro(enemy)).isTrue();
    }

    @Test
    void keepsWhatItKnewWhileItIsHeld() {
        Simulation simulation = watching();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity enemy = simulation.spawnEnemy(MIDDLE + DETECTION_RANGE - 1f, MIDDLE);
        simulation.step();

        enemy.getComponent(Stunned.class).apply(HELD_SECONDS, KNOCKBACK_DECAY);
        commander.getComponent(Position.class).set(1f, 1f);
        simulation.step();

        assertThat(aggro(enemy)).isTrue();
    }

    private static boolean aggro(Entity entity) {
        return entity.getComponent(Perception.class).aggro;
    }
}
