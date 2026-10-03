package pl.mzebrows.pwt.sim.system.commander;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.BRUTE_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.COMMANDER_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ENEMY_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.MELEE_ACTIVE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.MELEE_DAMAGE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.MELEE_LOCK;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.countLiving;
import static pl.mzebrows.pwt.sim.AlphaWorlds.harmless;
import static pl.mzebrows.pwt.sim.AlphaWorlds.harmlessLeaping;
import static pl.mzebrows.pwt.sim.AlphaWorlds.killed;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;
import static pl.mzebrows.pwt.sim.AlphaWorlds.unhurt;

import com.badlogic.ashley.core.Entity;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Invulnerable;
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

    /**
     * A protected target is not held. Its blow is recorded and settled to nothing like any other, but the
     * hold lands on the spot rather than being settled, so it is the one thing the sweep itself refuses.
     */
    @Test
    void leavesAProtectedBruteFreeWhenTheSweepConnects() {
        Simulation simulation = harmless();
        simulation.spawnCommander(50f, 50f);
        Entity brute = simulation.spawnBrute(IN_THE_ARC_X, 50f);
        brute.getComponent(Invulnerable.class).grant(ticksIn(MELEE_ACTIVE) * 4);

        steps(simulation, ticksIn(MELEE_ACTIVE) + 2, SWING_EAST);

        assertThat(brute.getComponent(Stunned.class).active()).isFalse();
        assertThat(brute.getComponent(Health.class).current).isEqualTo(BRUTE_HEALTH);
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

    /**
     * A kill on something already coming at him is the other thing entirely: the two of them are
     * tangled together for the lock, and for that beat neither of them is going anywhere.
     */
    @Test
    void tanglesHimUpWhenHisSweepKillsSomethingMidLeap() {
        Simulation simulation = lethalLeaping();
        Entity commander = simulation.spawnCommander(50f, 50f);
        Entity enemy = simulation.spawnEnemy(56f, 50f);

        stepUntilAirborne(simulation, enemy);
        steps(simulation, ticksIn(MELEE_ACTIVE) + 2, SWING_EAST);

        assertThat(commander.getComponent(Stunned.class).active()).isTrue();
        assertThat(commander.getComponent(Stunned.class).grappling).isTrue();
    }

    /**
     * A kill on something that was only walking is a swat, and a swat costs him nothing but the
     * cooldown. The lock is what answering a committed attack earns; spending it on the harmless
     * target too would make the safest thing on the field the most expensive one to kill.
     */
    @Test
    void leavesHimFreeWhenHisSweepKillsSomethingThatWasNotComingAtHim() {
        Simulation simulation = harmless();
        Entity commander = simulation.spawnCommander(50f, 50f);
        simulation.spawnEnemy(IN_THE_ARC_X, 50f);

        steps(simulation, ticksIn(MELEE_ACTIVE) + 2, SWING_EAST);

        assertThat(countLiving(simulation, Enemy.class)).isZero();
        assertThat(commander.getComponent(Stunned.class).active()).isFalse();
    }

    @Test
    void letsTheCommanderGoOnceHisOwnLockHasPassed() {
        Simulation simulation = lethalLeaping();
        Entity commander = simulation.spawnCommander(50f, 50f);
        Entity enemy = simulation.spawnEnemy(56f, 50f);

        stepUntilAirborne(simulation, enemy);
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
     * The clinch, measured. A sweep reaches further than the commander's own body, so a leap answered
     * at full stretch dies a stride short of him - and a kill throws nothing and a corpse is
     * immovable, so without something closing that stride the beat reads as him flinching at thin air.
     * A lethal exchange ends with the two of them touching, whatever distance the blow was struck at.
     */
    @Test
    void endsALethalExchangeWithTheTwoOfThemTouching() {
        Simulation simulation = lethalLeaping();
        Entity commander = simulation.spawnCommander(50f, 50f);
        Entity enemy = simulation.spawnEnemy(56f, 50f);

        stepUntilAirborne(simulation, enemy);
        steps(simulation, ticksIn(MELEE_ACTIVE) + 4, SWING_EAST);

        assertThat(distanceBetween(commander, enemy)).isCloseTo(COMMANDER_RADIUS + ENEMY_RADIUS, Offset.offset(0.01f));
    }

    /**
     * And it holds them there. A clinch carries no knockback decay, so the survivor stops dead instead
     * of coasting on whatever he was doing when he swung - which is what let a commander backing away
     * from a leap slide out from under the body he had just been clinched to, leaving the freeze
     * playing across a gap.
     */
    @Test
    void stopsHimDeadSoTheClinchDoesNotDriftApart() {
        Simulation simulation = lethalLeaping();
        Entity commander = simulation.spawnCommander(50f, 50f);
        Entity enemy = simulation.spawnEnemy(56f, 50f);
        stepUntilAirborne(simulation, enemy);
        steps(simulation, ticksIn(MELEE_ACTIVE) + 4, SWING_EAST);

        steps(simulation, 2);

        assertThat(commander.getComponent(Velocity.class).x).isZero();
        assertThat(commander.getComponent(Velocity.class).y).isZero();
        assertThat(distanceBetween(commander, enemy)).isCloseTo(COMMANDER_RADIUS + ENEMY_RADIUS, Offset.offset(0.01f));
    }

    /**
     * And it arrives under its own momentum rather than being put there. The corpse is immovable and
     * the commander is not, so a body still travelling when it reaches him shoves him back - which is
     * the whole of what makes the beat read as two things colliding instead of one thing blinking.
     */
    @Test
    void shovesHimBackAsTheBodyArrives() {
        Simulation simulation = lethalLeaping();
        Entity commander = simulation.spawnCommander(50f, 50f);
        Entity enemy = simulation.spawnEnemy(56f, 50f);
        stepUntilAirborne(simulation, enemy);

        steps(simulation, ticksIn(MELEE_ACTIVE) + 4, SWING_EAST);

        assertThat(commander.getComponent(Position.class).x).isLessThan(50f);
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

        assertThat(unhurt(commander)).isTrue();
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

        assertThat(killed(commander)).isTrue();
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

        assertThat(killed(commander)).isTrue();
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
        return new Simulation(harmlessWorld.config().withUnit(UnitKind.GRUNT, grunt -> grunt.withContactDamage(1000)));
    }
}
