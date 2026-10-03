package pl.mzebrows.pwt.sim.system.motion;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.FOLLOW_AHEAD_SECONDS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TROOPER_APPROACH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ordering;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.def.ApproachDef;
import pl.mzebrows.pwt.sim.def.Order;

/**
 * How a squad crosses open ground: abreast, not in single file (G0041).
 *
 * <p>The spread is the pack's own (G0033) - each man walks at a point beside where he is going, on a
 * side seeded from where he was put down - so what is worth pinning here is only that the squad uses it,
 * for the two walks it makes as a group: to a point, and after the commander.
 *
 * <p>Which side a man takes is a function of where he was put down, so the pairs here are put down
 * where the seeding sends them to opposite sides - two men a few units apart can land on the same one.
 */
class SquadSpreadTest {

    private static final float LANE = 50f;

    /** Far enough that he neither captures anything nor gets in the way. */
    private static final float CORNER = 95f;

    @Test
    void walksTwoMenToAPointAbreastRatherThanOneBehindTheOther() {
        Simulation spread = squadWalkingToAPoint(TROOPER_APPROACH);
        Simulation file = squadWalkingToAPoint(ApproachDef.off());

        assertThat(apart(file)).isLessThan(1f);
        assertThat(apart(spread)).isGreaterThan(3f);
    }

    /** The spread is spent by the time he is close, so a man walking wide still ends up standing on it. */
    @Test
    void stillEndsUpStandingOnThePoint() {
        Simulation simulation = squadWalkingToAPoint(TROOPER_APPROACH);

        steps(simulation, 120);

        for (Entity trooper : troopers(simulation)) {
            assertThat(trooper.getComponent(Trooper.class).mode).isEqualTo(Trooper.Mode.SECURE);
        }
    }

    @Test
    void fansOutBesideHimRatherThanQueueingOnHisHeels() {
        Simulation spread = squadFollowingHim(TROOPER_APPROACH);
        Simulation file = squadFollowingHim(ApproachDef.off());

        assertThat(apart(file)).isLessThan(1f);
        assertThat(apart(spread)).isGreaterThan(2f);
    }

    /**
     * Where he will be rather than where he is: a man keeping up with a commander who is walking across
     * his front cuts in ahead of him, instead of chasing the spot he has just left.
     */
    @Test
    void walksAtWhereHeIsGoingRatherThanWhereHeIs() {
        float leading = northOfWhereHeStarted(FOLLOW_AHEAD_SECONDS);
        float trailing = northOfWhereHeStarted(0f);

        assertThat(leading).isGreaterThan(trailing + 2f);
    }

    private static Simulation squadWalkingToAPoint(ApproachDef approach) {
        Simulation simulation = new Simulation(straightWalking(approach, 0f));
        simulation.spawnCommander(CORNER, CORNER);
        simulation.spawnControlPoint(90f, LANE);
        simulation.spawnTrooper(10f, LANE);
        simulation.spawnTrooper(12f, LANE);
        while (leader(simulation) < 70f) {
            steps(simulation, 1);
        }
        return simulation;
    }

    private static Simulation squadFollowingHim(ApproachDef approach) {
        Simulation simulation = new Simulation(straightWalking(approach, 0f));
        Entity commander = simulation.spawnCommander(LANE, LANE);
        simulation.spawnTrooper(66f, LANE);
        simulation.spawnTrooper(69f, LANE);
        simulation.step(ordering(Order.FOLLOW_ME));
        commander.getComponent(Position.class).set(10f, LANE);
        steps(simulation, 150);
        return simulation;
    }

    private static float northOfWhereHeStarted(float aheadSeconds) {
        Simulation simulation = new Simulation(straightWalking(ApproachDef.off(), aheadSeconds));
        simulation.spawnCommander(LANE, 20f);
        Entity trooper = simulation.spawnTrooper(70f, 20f);
        simulation.step(ordering(Order.FOLLOW_ME));
        PlayerInputs walkingNorth = new PlayerInputs().set(0, new CommanderInput(0f, 1f, LANE, CORNER, false, false));
        for (int i = 0; i < 40; i++) {
            simulation.step(walkingNorth);
        }
        return trooper.getComponent(Position.class).y - 20f;
    }

    /** Beelining, so nothing but the spread can put one man beside the other. */
    private static SimConfig straightWalking(ApproachDef approach, float aheadSeconds) {
        SimConfig base = config();
        return base.withTrooper(
                base.trooper().withUrgency(1f).withApproach(approach).withFollowAheadSeconds(aheadSeconds));
    }

    private static float leader(Simulation simulation) {
        float furthest = 0f;
        for (Entity trooper : troopers(simulation)) {
            furthest = Math.max(furthest, trooper.getComponent(Position.class).x);
        }
        return furthest;
    }

    private static ImmutableArray<Entity> troopers(Simulation simulation) {
        return simulation.engine().getEntitiesFor(Family.all(Trooper.class).get());
    }

    /** How far apart across the lane the first two troopers are. */
    private static float apart(Simulation simulation) {
        Position first = troopers(simulation).get(0).getComponent(Position.class);
        Position second = troopers(simulation).get(1).getComponent(Position.class);
        return Math.abs(first.y - second.y);
    }
}
