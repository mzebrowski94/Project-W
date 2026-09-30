package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ENEMY_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.countLiving;
import static pl.mzebrows.pwt.sim.AlphaWorlds.godMode;
import static pl.mzebrows.pwt.sim.AlphaWorlds.harmless;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ordering;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;
import static pl.mzebrows.pwt.sim.AlphaWorlds.unhurt;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Invulnerable;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Shield;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.Order;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.SlickDef;
import pl.mzebrows.pwt.sim.def.WeaponKind;

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

    /**
     * <strong>A hit that owes nothing leaves nothing behind.</strong> Venom's shot does nothing to a man
     * - the spatter is the weapon - so where it came from must not outlive the tick it landed on: the
     * next real hit turns him towards where <em>that</em> came from, not towards a frog long gone (G0016).
     */
    @Test
    void forgetsWhereAHitThatOwedNothingCameFrom() {
        Simulation simulation = new Simulation(spatterless(harmless().config()));
        simulation.spawnCommander(50f, 60f);
        Entity trooper = simulation.spawnTrooper(50f, 50f);
        simulation.step(ordering(Order.DEFENCE));

        simulation.spawnProjectile(20f, 50f, 1f, 0f, WeaponKind.VENOM, Side.ALIEN);
        steps(simulation, 40);
        simulation.spawnProjectile(50f, 20f, 0f, 1f, Side.ALIEN);
        steps(simulation, 40);

        Trooper man = trooper.getComponent(Trooper.class);
        assertThat(man.alertRemaining).isPositive();
        assertThat(man.alertX).isEqualTo(50f);
        assertThat(man.alertY).isEqualTo(20f);
    }

    /**
     * <strong>A hit that costs him nothing still lands</strong> (G0057). Venom's shot wounds nobody - the
     * spatter is the weapon - but a man it hits turns towards the frog that spat it.
     */
    @Test
    void turnsAManTowardsAHitThatCostHimNothing() {
        Simulation simulation = new Simulation(spatterless(harmless().config()));
        simulation.spawnCommander(50f, 60f);
        Entity trooper = simulation.spawnTrooper(50f, 50f);
        simulation.step(ordering(Order.DEFENCE));

        simulation.spawnProjectile(20f, 50f, 1f, 0f, WeaponKind.VENOM, Side.ALIEN);
        steps(simulation, 40);

        Trooper man = trooper.getComponent(Trooper.class);
        assertThat(unhurt(trooper)).isTrue();
        assertThat(man.alertRemaining).isPositive();
        assertThat(man.alertX).isEqualTo(20f);
        assertThat(man.alertY).isEqualTo(50f);
    }

    /** And it takes his shield with it: a shield takes one hit of any harm, none included (G0053, G0057). */
    @Test
    void breaksAShieldWithAHitThatCostsHimNothing() {
        Simulation simulation = new Simulation(spatterless(harmless().config()));
        Entity commander = simulation.spawnCommander(50f, 50f);
        commander.getComponent(Shield.class).raise();

        simulation.spawnProjectile(20f, 50f, 1f, 0f, WeaponKind.VENOM, Side.ALIEN);
        steps(simulation, 40);

        assertThat(commander.getComponent(Shield.class).up).isFalse();
        assertThat(unhurt(commander)).isTrue();
    }

    /** An alien is provoked by any hit that landed, whatever it owed (G0016, G0057). */
    @Test
    void provokesAnAlienWithAHitThatOwedNothing() {
        Simulation simulation = simulation();
        Entity enemy = simulation.spawnEnemy(50f, 50f);

        Damage damage = enemy.getComponent(Damage.class);
        damage.land(0, Harm.NONE);
        damage.cameFrom(80f, 50f);
        simulation.step();

        Perception perception = enemy.getComponent(Perception.class);
        assertThat(perception.provoked).isTrue();
        assertThat(perception.provokedX).isEqualTo(80f);
        assertThat(enemy.getComponent(Health.class).current).isEqualTo(ENEMY_HEALTH);
    }

    /** Venom whose shot is all there is to it, so a test about the shot is only about the shot. */
    private static SimConfig spatterless(SimConfig config) {
        return config.withWeapons(config.weapons().map(WeaponKind.VENOM, gun -> gun.withSlicks(SlickDef.off())));
    }

    private static void owe(Entity entity, int amount) {
        entity.getComponent(Damage.class).land(amount, Harm.KILL);
    }
}
