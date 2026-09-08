package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.BRUTE_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.MELEE_ACTIVE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.MELEE_DAMAGE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.MELEE_LOCK;
import static pl.mzebrows.pwt.sim.AlphaWorlds.MELEE_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.MELEE_REACH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.countLiving;
import static pl.mzebrows.pwt.sim.AlphaWorlds.harmless;
import static pl.mzebrows.pwt.sim.AlphaWorlds.harmlessLeaping;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Leap;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * What a swing costs whatever it lands on, and what it costs the man swinging.
 *
 * <p>The contrast between the two enemy kinds is the whole point of the mechanic. A hand strike
 * deletes an ordinary enemy, so answering a leap in time simply works. A brute has more health than
 * one swing carries, so it is stunned and thrown instead - and the opening the commander bought
 * himself is the thing he now has to use, because the brute is getting back up.
 */
class StrikeCombatTest {

    private static final CommanderInput SWING_EAST = new CommanderInput(0f, 0f, 60f, 50f, false, true);

    /** Where the sweep passes halfway through its arc when the commander stands at (50, 50) aiming east. */
    private static final float IN_THE_ARC_X = 53f;

    @Test
    void killsAnOrdinaryEnemyOutright() {
        Simulation simulation = harmless();
        simulation.spawnCommander(50f, 50f);
        simulation.spawnEnemy(IN_THE_ARC_X, 50f);

        steps(simulation, ticksIn(MELEE_ACTIVE) + 2, SWING_EAST);

        assertThat(countLiving(simulation, Enemy.class)).isZero();
    }

    @Test
    void leavesABruteStanding() {
        Simulation simulation = harmless();
        simulation.spawnCommander(50f, 50f);
        Entity brute = simulation.spawnBrute(IN_THE_ARC_X, 50f);

        steps(simulation, ticksIn(MELEE_ACTIVE) + 2, SWING_EAST);

        assertThat(brute.getComponent(Health.class).current).isEqualTo(BRUTE_HEALTH - MELEE_DAMAGE);
    }

    @Test
    void holdsABruteStillWhenTheSweepConnects() {
        Simulation simulation = harmless();
        simulation.spawnCommander(50f, 50f);
        Entity brute = simulation.spawnBrute(IN_THE_ARC_X, 50f);

        steps(simulation, ticksIn(MELEE_ACTIVE) + 2, SWING_EAST);

        assertThat(brute.getComponent(Stunned.class).active()).isTrue();
    }

    /** Away from the commander, not away from the sweep - the sweep can be on the brute's far side. */
    @Test
    void throwsASurvivingBruteAwayFromTheCommander() {
        Simulation simulation = harmless();
        simulation.spawnCommander(50f, 50f);
        Entity brute = simulation.spawnBrute(IN_THE_ARC_X, 50f);

        steps(simulation, ticksIn(MELEE_ACTIVE) + 4, SWING_EAST);

        assertThat(brute.getComponent(Position.class).x).isGreaterThan(IN_THE_ARC_X);
    }

    /**
     * Shoving something away is not being caught up with it. A swing the target survives costs the
     * commander nothing but his cooldown - he is still on his feet, still moving, still able to shoot
     * the thing he just pushed off. That opening is what the parry is worth.
     */
    @Test
    void leavesTheCommanderFreeWhenTheTargetSurvives() {
        Simulation simulation = harmless();
        Entity commander = simulation.spawnCommander(50f, 50f);
        Entity brute = simulation.spawnBrute(IN_THE_ARC_X, 50f);

        steps(simulation, ticksIn(MELEE_ACTIVE) + 2, SWING_EAST);

        assertThat(brute.getComponent(Stunned.class).active()).isTrue();
        assertThat(commander.getComponent(Stunned.class).active()).isFalse();
    }

    /** A kill is the other thing entirely: the two of them are tangled together for the lock. */
    @Test
    void holdsTheCommanderStillWhenHisSwingKills() {
        Simulation simulation = harmless();
        Entity commander = simulation.spawnCommander(50f, 50f);
        simulation.spawnEnemy(IN_THE_ARC_X, 50f);

        steps(simulation, ticksIn(MELEE_ACTIVE) + 2, SWING_EAST);

        assertThat(commander.getComponent(Stunned.class).active()).isTrue();
        assertThat(commander.getComponent(Stunned.class).grappling).isTrue();
    }

    @Test
    void letsTheCommanderGoOnceHisOwnLockHasPassed() {
        Simulation simulation = harmless();
        Entity commander = simulation.spawnCommander(50f, 50f);
        simulation.spawnEnemy(IN_THE_ARC_X, 50f);

        steps(simulation, ticksIn(MELEE_ACTIVE + MELEE_LOCK) + 4, SWING_EAST);

        assertThat(commander.getComponent(Stunned.class).active()).isFalse();
    }

    /**
     * The other half of the knockback rule, and the one that makes a lethal answer read as a clinch:
     * a blow that kills throws nothing. The body stops where it was struck and stays there, which
     * with a reach this short means it stops against the commander.
     */
    @Test
    void throwsNothingWhenTheBlowKills() {
        Simulation simulation = harmless();
        simulation.spawnCommander(50f, 50f);
        Entity enemy = simulation.spawnEnemy(IN_THE_ARC_X, 50f);
        steps(simulation, ticksIn(MELEE_ACTIVE) + 2, SWING_EAST);
        float diedAt = enemy.getComponent(Position.class).x;

        steps(simulation, 3);

        assertThat(enemy.getComponent(Velocity.class).x).isZero();
        assertThat(enemy.getComponent(Position.class).x).isEqualTo(diedAt);
    }

    /**
     * The clinch, measured. A body cannot have been struck from further away than the sweep reaches,
     * and nothing moves it afterwards - so a lethal answer to a leap always ends with the two of them
     * within a swing of each other rather than a corpse sailing off.
     */
    @Test
    void leavesTheBodyOfALeaperWithinArmsReach() {
        Simulation simulation = lethalLeaping();
        Entity commander = simulation.spawnCommander(50f, 50f);
        Entity enemy = simulation.spawnEnemy(56f, 50f);

        stepUntilAirborne(simulation, enemy);
        steps(simulation, ticksIn(MELEE_ACTIVE) + 4, SWING_EAST);

        assertThat(distanceBetween(commander, enemy)).isLessThanOrEqualTo(MELEE_REACH + MELEE_RADIUS + 1f);
    }

    /** One swing, one blow. A sweep that mowed through a crowd would make the counter free. */
    @Test
    void landsAtMostOneBlowPerSwing() {
        Simulation simulation = harmless();
        simulation.spawnCommander(50f, 50f);
        simulation.spawnEnemy(IN_THE_ARC_X, 50f);
        simulation.spawnEnemy(50f, IN_THE_ARC_X);

        steps(simulation, ticksIn(MELEE_ACTIVE) + 2, SWING_EAST);

        assertThat(countLiving(simulation, Enemy.class)).isEqualTo(1);
    }

    @Test
    void neverHurtsTheCommanderWhoSwungIt() {
        Simulation simulation = harmless();
        Entity commander = simulation.spawnCommander(50f, 50f);

        steps(simulation, ticksIn(MELEE_ACTIVE) + 2, SWING_EAST);

        assertThat(commander.getComponent(Health.class).current).isEqualTo(commander.getComponent(Health.class).max);
    }

    /**
     * The mechanic end to end: something commits to a leap, the commander answers it once it is
     * committed, and he is still standing afterwards. If this fails the parts work and the game does
     * not.
     *
     * <p>He waits for the jump rather than swinging from the first tick, and that is the design, not a
     * convenience for the test: the swing has a cooldown longer than the leap, so a player who mashes
     * the button is on cooldown when it matters. {@link #cannotSimplySwingFromTheOutset()} holds that
     * line.
     */
    @Test
    void answersALeapOnceItIsCommitted() {
        Simulation simulation = lethalLeaping();
        simulation.spawnCommander(50f, 50f);
        Entity enemy = simulation.spawnEnemy(56f, 50f);

        stepUntilAirborne(simulation, enemy);
        steps(simulation, ticksIn(MELEE_ACTIVE) + 4, SWING_EAST);

        assertThat(count(simulation, Commander.class)).isEqualTo(1);
        assertThat(countLiving(simulation, Enemy.class)).isZero();
    }

    /** Easy to learn, hard to master: the button is not the answer, the timing is. */
    @Test
    void cannotSimplySwingFromTheOutset() {
        Simulation simulation = lethalLeaping();
        Entity commander = simulation.spawnCommander(50f, 50f);
        simulation.spawnEnemy(56f, 50f);

        steps(simulation, 40, SWING_EAST);

        assertThat(commander.getComponent(Health.class).current).isLessThanOrEqualTo(0f);
    }

    /** Steps until the enemy has left the ground, so a test can act on the window the player gets. */
    private static void stepUntilAirborne(Simulation simulation, Entity enemy) {
        for (int i = 0; i < 60; i++) {
            if (enemy.getComponent(Leap.class).airborne()) {
                return;
            }
            simulation.step();
        }
        throw new IllegalStateException("the enemy never left the ground");
    }

    /** A commander who does nothing is killed by the same leap, so the test above is testing the swing. */
    @Test
    void isKilledByTheSameLeapIfHeDoesNothing() {
        Simulation simulation = lethalLeaping();
        Entity commander = simulation.spawnCommander(50f, 50f);
        simulation.spawnEnemy(56f, 50f);

        steps(simulation, 40);

        assertThat(commander.getComponent(Health.class).current).isLessThanOrEqualTo(0f);
    }

    private static float distanceBetween(Entity first, Entity second) {
        Position a = first.getComponent(Position.class);
        Position b = second.getComponent(Position.class);
        float dx = b.x - a.x;
        float dy = b.y - a.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static Simulation lethalLeaping() {
        Simulation harmlessWorld = harmlessLeaping();
        return new Simulation(harmlessWorld.config().withUnit(UnitKind.GRUNT, grunt -> grunt.withContactDamage(1000f)));
    }
}
