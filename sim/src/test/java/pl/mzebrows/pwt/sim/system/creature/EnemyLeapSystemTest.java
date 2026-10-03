package pl.mzebrows.pwt.sim.system.creature;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.LEAP_AIRBORNE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.LEAP_HESITATION;
import static pl.mzebrows.pwt.sim.AlphaWorlds.LEAP_SEQUENCE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.LEAP_SPEED;
import static pl.mzebrows.pwt.sim.AlphaWorlds.LEAP_TRIGGER_RANGE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.harmlessLeaping;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.AlphaWorlds;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Leap;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.def.LeapDef;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * Throwing yourself at the commander.
 *
 * <p>Three properties here are the mechanic rather than the implementation, and each has a test that
 * would fail loudly if someone tidied it away: the unit stops before it jumps so the player can see
 * it coming, the jump goes where the commander <em>was</em> so it can be dodged, and a jump can be
 * knocked out of a unit so it can be countered.
 *
 * <p>The base world does not leap at all, so an enemy that suddenly started jumping in an unrelated
 * test would be caught rather than absorbed.
 */
class EnemyLeapSystemTest {

    private static final float TOLERANCE = 1e-3f;

    /** Whether this unit is in the air and travelling at the thing named, rather than past it. */
    private static boolean leapsToward(Entity enemy, Entity quarry) {
        Leap leap = enemy.getComponent(Leap.class);
        if (!leap.airborne()) {
            return false;
        }
        Position from = enemy.getComponent(Position.class);
        Position to = quarry.getComponent(Position.class);
        float toX = to.x - from.x;
        float toY = to.y - from.y;
        float length = (float) Math.sqrt(toX * toX + toY * toY);
        return length > 0f && (leap.directionX * toX + leap.directionY * toY) / length > 0.9f;
    }

    @Test
    void walksRatherThanLeapingWhileTheCommanderIsOutOfRange() {
        Simulation simulation = harmlessLeaping();
        simulation.spawnCommander(50f, 50f);
        Entity enemy = simulation.spawnEnemy(50f + LEAP_TRIGGER_RANGE + 10f, 50f);

        steps(simulation, 4);

        assertThat(enemy.getComponent(Leap.class).phase).isEqualTo(Leap.Phase.READY);
    }

    @Test
    void commitsOnceTheCommanderIsInRange() {
        Simulation simulation = harmlessLeaping();
        simulation.spawnCommander(50f, 50f);
        Entity enemy = simulation.spawnEnemy(60f, 50f);

        simulation.step();

        assertThat(enemy.getComponent(Leap.class).phase).isEqualTo(Leap.Phase.HESITATING);
    }

    /**
     * G0026, applied to the leap. It used to ask for "the commander" and got the first one standing,
     * so every leaper on a level threw itself at player one: the second player was never leapt at,
     * and neither was a trooper standing right in front of one. Who it comes for is decided once, by
     * {@code PerceptionSystem}, and this reads that answer.
     */
    @Test
    void leapsAtTheSecondPlayerWhenHeIsTheOneStandingInFrontOfIt() {
        Simulation simulation = harmlessLeaping();
        simulation.spawnCommander(10f, 10f, 0);
        Entity second = simulation.spawnCommander(50f, 50f, 1);
        Entity enemy = simulation.spawnEnemy(60f, 50f);

        steps(simulation, ticksIn(LEAP_HESITATION) + 2);

        assertThat(leapsToward(enemy, second)).isTrue();
    }

    @Test
    void leapsAtATrooperWhenItIsTheNearestManOnTheField() {
        Simulation simulation = harmlessLeaping();
        simulation.spawnCommander(10f, 10f);
        Entity trooper = simulation.spawnTrooper(50f, 50f);
        Entity enemy = simulation.spawnEnemy(60f, 50f);

        steps(simulation, ticksIn(LEAP_HESITATION) + 2);

        assertThat(leapsToward(enemy, trooper)).isTrue();
    }

    /** Nothing noticed is nothing to jump at. A leap is an attack, and an attack needs a quarry. */
    @Test
    void leapsAtNobodyOnceThereIsNobodyLeftToHunt() {
        Simulation simulation = harmlessLeaping();
        Entity enemy = simulation.spawnEnemy(60f, 50f);

        steps(simulation, 4);

        assertThat(enemy.getComponent(Leap.class).phase).isEqualTo(Leap.Phase.READY);
    }

    /** The tell. A unit that kept walking through its wind-up would give the player nothing to read. */
    @Test
    void standsStillWhileItWindsUp() {
        Simulation simulation = harmlessLeaping();
        simulation.spawnCommander(50f, 50f);
        Entity enemy = simulation.spawnEnemy(60f, 50f);

        steps(simulation, ticksIn(LEAP_HESITATION) - 2);

        Velocity velocity = enemy.getComponent(Velocity.class);
        assertThat(velocity.x).isCloseTo(0f, Offset.offset(TOLERANCE));
        assertThat(velocity.y).isCloseTo(0f, Offset.offset(TOLERANCE));
    }

    @Test
    void leavesTheGroundOnceTheWindUpIsOver() {
        Simulation simulation = harmlessLeaping();
        simulation.spawnCommander(50f, 50f);
        Entity enemy = simulation.spawnEnemy(60f, 50f);

        steps(simulation, ticksIn(LEAP_HESITATION) + 2);

        assertThat(enemy.getComponent(Leap.class).phase).isEqualTo(Leap.Phase.AIRBORNE);
    }

    @Test
    void travelsAtItsLeapSpeedRatherThanItsWalkingSpeed() {
        Simulation simulation = harmlessLeaping();
        simulation.spawnCommander(50f, 50f);
        Entity enemy = simulation.spawnEnemy(60f, 50f);

        steps(simulation, ticksIn(LEAP_HESITATION) + 2);

        Velocity velocity = enemy.getComponent(Velocity.class);
        float speed = (float) Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y);
        assertThat(speed).isCloseTo(LEAP_SPEED, Offset.offset(TOLERANCE));
    }

    /**
     * The whole reason a leap can be dodged. A jump that steered after its target would be unanswerable,
     * and an unanswerable attack has nothing for the strike to counter.
     */
    @Test
    void keepsGoingWhereItAimedEvenIfTheCommanderMoves() {
        Simulation simulation = harmlessLeaping();
        Entity commander = simulation.spawnCommander(50f, 50f);
        Entity enemy = simulation.spawnEnemy(50f, 60f);
        steps(simulation, ticksIn(LEAP_HESITATION) + 2);
        Velocity launched = enemy.getComponent(Velocity.class);
        float launchedX = launched.x;
        float launchedY = launched.y;

        commander.getComponent(Position.class).set(10f, 50f);
        simulation.step();

        Velocity velocity = enemy.getComponent(Velocity.class);
        assertThat(velocity.x).isCloseTo(launchedX, Offset.offset(TOLERANCE));
        assertThat(velocity.y).isCloseTo(launchedY, Offset.offset(TOLERANCE));
    }

    @Test
    void stringsTogetherAsManyLeapsAsItsSequenceAllows() {
        Simulation simulation = harmlessLeaping();
        simulation.spawnCommander(50f, 50f);
        Entity enemy = simulation.spawnEnemy(50f, 80f);

        List<Leap.Phase> phases = record(simulation, enemy, ticksIn(LEAP_HESITATION + LEAP_AIRBORNE * 4f) + 40);

        assertThat(airborneRuns(phases)).isEqualTo(LEAP_SEQUENCE);
    }

    /** A landed counter does not merely interrupt the hop; it ends the whole sequence. */
    @Test
    void abandonsTheWholeSequenceWhenItIsStunned() {
        Simulation simulation = harmlessLeaping();
        simulation.spawnCommander(50f, 50f);
        Entity enemy = simulation.spawnEnemy(50f, 60f);
        steps(simulation, ticksIn(LEAP_HESITATION) + 2);

        enemy.getComponent(Stunned.class)
                .apply(AlphaWorlds.ticksIn(AlphaWorlds.MELEE_LOCK), AlphaWorlds.KNOCKBACK_DECAY);
        simulation.step();

        Leap leap = enemy.getComponent(Leap.class);
        assertThat(leap.phase).isEqualTo(Leap.Phase.READY);
        assertThat(leap.leapsLeft).isZero();
    }

    @Test
    void neverLeavesTheGroundWhenItsKindHasLeapingSwitchedOff() {
        Simulation simulation = simulation();
        simulation.spawnCommander(50f, 50f);
        Entity enemy = simulation.spawnEnemy(55f, 50f);

        steps(simulation, 60);

        assertThat(enemy.getComponent(Leap.class).phase).isEqualTo(Leap.Phase.READY);
    }

    /**
     * Variety the player has to watch for, not count. Two enemies committing on the same tick must not
     * jump in lockstep, or the tell becomes a metronome.
     */
    @Test
    void variesTheWindUpBetweenOneUnitAndTheNext() {
        Simulation simulation = variedHesitation();
        simulation.spawnCommander(50f, 50f);
        Entity first = simulation.spawnEnemy(50f, 62f);
        Entity second = simulation.spawnEnemy(50f, 38f);

        simulation.step();

        assertThat(first.getComponent(Leap.class).remainingTicks)
                .isNotEqualTo(second.getComponent(Leap.class).remainingTicks);
    }

    /** Same seed, same wind-up. Pillar 6 applies to the one place in the game that draws a random number. */
    @Test
    void drawsTheSameWindUpOnEveryRunOfTheSameSeed() {
        int first = firstHesitation();
        int second = firstHesitation();

        assertThat(first).isEqualTo(second);
    }

    private static int firstHesitation() {
        Simulation simulation = variedHesitation();
        simulation.spawnCommander(50f, 50f);
        Entity enemy = simulation.spawnEnemy(50f, 62f);

        simulation.step();

        return enemy.getComponent(Leap.class).remainingTicks;
    }

    private static Simulation variedHesitation() {
        SimConfig config = AlphaWorlds.config();
        LeapDef varied =
                AlphaWorlds.leapConfig().withHesitationMinSeconds(0.05f).withHesitationMaxSeconds(0.5f);
        return new Simulation(config.withUnit(
                UnitKind.GRUNT, grunt -> grunt.withContactDamage(0).withLeap(varied)));
    }

    private static List<Leap.Phase> record(Simulation simulation, Entity enemy, int ticks) {
        List<Leap.Phase> phases = new ArrayList<>();
        for (int i = 0; i < ticks; i++) {
            simulation.step();
            phases.add(enemy.getComponent(Leap.class).phase);
        }
        return phases;
    }

    /** How many separate times the unit was in the air, rather than how many ticks it spent there. */
    private static int airborneRuns(List<Leap.Phase> phases) {
        int runs = 0;
        Leap.Phase previous = Leap.Phase.READY;
        for (Leap.Phase phase : phases) {
            if (phase == Leap.Phase.AIRBORNE && previous != Leap.Phase.AIRBORNE) {
                runs++;
            }
            previous = phase;
        }
        return runs;
    }
}
