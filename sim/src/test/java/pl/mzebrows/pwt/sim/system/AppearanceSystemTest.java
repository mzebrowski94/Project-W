package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ENEMY_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.LEAP_HESITATION;
import static pl.mzebrows.pwt.sim.AlphaWorlds.MELEE_LOCK;
import static pl.mzebrows.pwt.sim.AlphaWorlds.harmlessLeaping;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Stunned;

/**
 * Pillar 5, made checkable. Every situation the player has to react to has to be legible from the
 * outside, and the renderer is only ever told what state an entity is in - it never works it out.
 *
 * <p>The precedence tests matter more than the plain ones. Two states are true at once far more often
 * than not - a unit stunned out of a leap is both winding up and held - and showing the wrong one
 * would be worse than showing nothing, because the player would have learned to trust it.
 */
class AppearanceSystemTest {

    @Test
    void showsAUnitDoingNothingInParticularAsNormal() {
        Simulation simulation = simulation();
        simulation.spawnCommander(50f, 50f);
        Entity enemy = simulation.spawnEnemy(80f, 50f);

        simulation.step();

        assertThat(state(enemy)).isEqualTo(Appearance.State.NORMAL);
    }

    @Test
    void showsAUnitWindingUpToLeap() {
        Simulation simulation = harmlessLeaping();
        simulation.spawnCommander(50f, 50f);
        Entity enemy = simulation.spawnEnemy(60f, 50f);

        simulation.step();

        assertThat(state(enemy)).isEqualTo(Appearance.State.WINDING_UP);
    }

    @Test
    void showsAUnitInTheAir() {
        Simulation simulation = harmlessLeaping();
        simulation.spawnCommander(50f, 50f);
        Entity enemy = simulation.spawnEnemy(60f, 50f);

        steps(simulation, ticksIn(LEAP_HESITATION) + 2);

        assertThat(state(enemy)).isEqualTo(Appearance.State.AIRBORNE);
    }

    /** Held beats winding up: what the player needs to know is that it is no longer about to jump. */
    @Test
    void showsAUnitStunnedOutOfItsWindUpAsHeldRatherThanWindingUp() {
        Simulation simulation = harmlessLeaping();
        simulation.spawnCommander(50f, 50f);
        Entity enemy = simulation.spawnEnemy(60f, 50f);
        simulation.step();

        enemy.getComponent(Stunned.class).apply(MELEE_LOCK, 0f);
        simulation.step();

        assertThat(state(enemy)).isEqualTo(Appearance.State.STAGGERED);
    }

    /**
     * The exception at the top of the precedence, and the reason it is there: a commander who has just
     * lost an exchange looks exactly like one who has just won it, for as long as the lock holds. The
     * player finds out when it ends and one of them does not get up.
     */
    @Test
    void showsALostExchangeAsLockedRatherThanDyingWhileTheLockHolds() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(50f, 50f);
        Entity enemy = simulation.spawnEnemy(50f, 50f);

        simulation.step();

        assertThat(commander.getComponent(Health.class).current).isLessThanOrEqualTo(0f);
        assertThat(state(commander)).isEqualTo(Appearance.State.GRAPPLING);
        assertThat(state(enemy)).isEqualTo(Appearance.State.GRAPPLING);
    }

    /** A death with no exchange behind it has nothing to hide, and reads as dead straight away. */
    @Test
    void showsAShotEnemyAsDyingStraightAway() {
        Simulation simulation = simulation();
        simulation.spawnCommander(50f, 50f);
        Entity enemy = simulation.spawnEnemy(70f, 50f);

        enemy.getComponent(Damage.class).add(ENEMY_HEALTH, 0f);
        simulation.step();

        assertThat(enemy.getComponent(Stunned.class).active()).isFalse();
        assertThat(state(enemy)).isEqualTo(Appearance.State.DYING);
    }

    private static Appearance.State state(Entity entity) {
        return entity.getComponent(Appearance.class).state;
    }
}
