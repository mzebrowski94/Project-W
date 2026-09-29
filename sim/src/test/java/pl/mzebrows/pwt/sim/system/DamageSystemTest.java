package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ENEMY_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.countLiving;
import static pl.mzebrows.pwt.sim.AlphaWorlds.godMode;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;
import static pl.mzebrows.pwt.sim.AlphaWorlds.unhurt;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Invulnerable;

/**
 * Damage is owed by whatever caused it and settled here, once a tick. Tests write straight to the
 * {@link Damage} component, which is exactly what a contact does - the point of the split is that
 * nothing else needs to know about health, protection or dying.
 */
class DamageSystemTest {

    @Test
    void takesTheDamageOwedOffHealth() {
        Simulation simulation = simulation();
        Entity enemy = simulation.spawnEnemy(50f, 50f);

        owe(enemy, 30);
        simulation.step();

        assertThat(enemy.getComponent(Health.class).current).isEqualTo(ENEMY_HEALTH - 30);
    }

    /** Two hits landing in one tick must both count, whatever order the collision grid found them in. */
    @Test
    void addsUpEveryHitOwedInTheSameTick() {
        Simulation simulation = simulation();
        Entity enemy = simulation.spawnEnemy(50f, 50f);

        owe(enemy, 30);
        owe(enemy, 45);
        simulation.step();

        assertThat(enemy.getComponent(Health.class).current).isEqualTo(ENEMY_HEALTH - 75);
    }

    @Test
    void settlesTheDebtSoItIsNotPaidTwice() {
        Simulation simulation = simulation();
        Entity enemy = simulation.spawnEnemy(50f, 50f);

        owe(enemy, 30);
        simulation.step();
        simulation.step();

        assertThat(enemy.getComponent(Health.class).current).isEqualTo(ENEMY_HEALTH - 30);
    }

    @Test
    void killsWhatRunsOutOfHealth() {
        Simulation simulation = simulation();
        Entity enemy = simulation.spawnEnemy(50f, 50f);

        owe(enemy, ENEMY_HEALTH);
        simulation.step();

        assertThat(countLiving(simulation, Enemy.class)).isZero();
    }

    @Test
    void leavesSomethingAliveOnExactlyLethalMinusOne() {
        Simulation simulation = simulation();
        Entity enemy = simulation.spawnEnemy(50f, 50f);

        owe(enemy, ENEMY_HEALTH - 1);
        simulation.step();

        assertThat(count(simulation, Enemy.class)).isEqualTo(1);
    }

    @Test
    void ignoresDamageWhileInvulnerable() {
        Simulation simulation = simulation();
        Entity enemy = simulation.spawnEnemy(50f, 50f);
        enemy.getComponent(Invulnerable.class).grant(ticksIn(1f));

        owe(enemy, ENEMY_HEALTH);
        simulation.step();

        assertThat(enemy.getComponent(Health.class).current).isEqualTo(ENEMY_HEALTH);
    }

    /**
     * A blow shrugged off is gone, not queued. Carrying it over would kill a unit the moment its
     * protection lapsed, from a hit it already survived.
     */
    @Test
    void forgetsDamageItIgnoredRatherThanSavingItUp() {
        Simulation simulation = simulation();
        Entity enemy = simulation.spawnEnemy(50f, 50f);
        enemy.getComponent(Invulnerable.class).grant(2);

        owe(enemy, ENEMY_HEALTH);
        for (int i = 0; i < 10; i++) {
            simulation.step();
        }

        assertThat(count(simulation, Enemy.class)).isEqualTo(1);
        assertThat(enemy.getComponent(Health.class).current).isEqualTo(ENEMY_HEALTH);
    }

    @Test
    void neverKillsAnythingUnderGodMode() {
        Simulation simulation = simulation();
        godMode(simulation);
        Entity commander = simulation.spawnCommander(50f, 50f);

        owe(commander, 10_000);
        simulation.step();

        assertThat(unhurt(commander)).isTrue();
    }

    private static void owe(Entity entity, int amount) {
        entity.getComponent(Damage.class).add(amount, 0);
    }
}
