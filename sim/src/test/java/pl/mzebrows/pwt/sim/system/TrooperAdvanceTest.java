package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.POINT_CAPTURE_SECONDS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TROOPER_SPEED;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ground;
import static pl.mzebrows.pwt.sim.AlphaWorlds.on;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.component.Velocity;

/**
 * How a squad crosses a level: towards what the level wants taken, looking around on the way.
 *
 * <p>Two things here are decisions rather than behaviour, and both are easy to break by accident.
 * <strong>Nearest means nearest by route</strong>, so a squad does not march at a point on the far
 * side of a wall; and <strong>urgency is not a speed</strong>, so that "move out" - which is a speed -
 * stays a thing the player can see happening.
 */
class TrooperAdvanceTest {

    /** Far enough that he neither captures anything nor gets in the way. */
    private static final float CORNER = 95f;

    private static final float MIDDLE = 50f;

    private static final Offset<Float> TOLERANCE = Offset.offset(0.5f);

    @Test
    void walksTheSquadTowardsTheNearestPointTheLevelWantsTaken() {
        Simulation simulation = simulation();
        simulation.spawnCommander(CORNER, CORNER);
        simulation.spawnControlPoint(80f, 20f);
        Entity trooper = simulation.spawnTrooper(20f, 20f);

        steps(simulation, 30);

        assertThat(mode(trooper)).isEqualTo(Trooper.Mode.ADVANCE);
        assertThat(at(trooper).x).isGreaterThan(20f);
    }

    /**
     * 2.e, and the reason {@code FlowField.stepsTo} is public. The near point is straight through a
     * wall; the far one is a clear walk. A squad that chose on a straight line would march into rock.
     */
    @Test
    void picksThePointThatIsTheShorterWalkRatherThanTheNearerOne() {
        Simulation simulation = on(ground().rect(9, 0, 1, 14, Terrain.ROCK).build());
        simulation.spawnCommander(CORNER, CORNER);
        simulation.spawnControlPoint(70f, 20f);
        simulation.spawnControlPoint(20f, 80f);
        Entity trooper = simulation.spawnTrooper(20f, 20f);

        steps(simulation, 40);

        assertThat(at(trooper).y).isGreaterThan(20f);
    }

    /** 2.c. A point taken is a point finished with; the squad goes to the next one. */
    @Test
    void movesOnToTheNextPointOnceOneIsTaken() {
        Simulation simulation = simulation();
        simulation.spawnCommander(CORNER, CORNER);
        Entity taken = simulation.spawnControlPoint(20f, 20f);
        simulation.spawnControlPoint(80f, 20f);
        Entity trooper = simulation.spawnTrooper(20f, 20f);
        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) + 4);
        assertThat(taken.getComponent(ControlPoint.class).captured).isTrue();

        float before = at(trooper).x;
        steps(simulation, 60);

        assertThat(at(trooper).x).isGreaterThan(before);
    }

    /** 2.c, the other half: nothing left to take, so go and look for whatever is still alive. */
    @Test
    void roamsWhenThereIsNothingLeftToTake() {
        Simulation simulation = simulation();
        simulation.spawnCommander(CORNER, CORNER);
        Entity trooper = simulation.spawnTrooper(20f, 20f);

        steps(simulation, 30);

        assertThat(mode(trooper)).isEqualTo(Trooper.Mode.EXPLORE);
        assertThat(moved(trooper, 20f, 20f)).isTrue();
    }

    /** 1.b. Anything less than full urgency has to show as ground covered sideways. */
    @Test
    void weavesOnItsWayRatherThanWalkingAStraightLine() {
        Simulation simulation = simulation();
        simulation.spawnCommander(CORNER, CORNER);
        simulation.spawnControlPoint(90f, 20f);
        Entity trooper = simulation.spawnTrooper(20f, 20f);

        float wandered = furthestOffTheLine(simulation, trooper, 20f, 120);

        assertThat(wandered).isGreaterThan(1f);
    }

    @Test
    void walksStraightAtItWhenUrgencyIsFull() {
        Simulation simulation = withUrgency(1f);
        simulation.spawnCommander(CORNER, CORNER);
        simulation.spawnControlPoint(90f, 20f);
        Entity trooper = simulation.spawnTrooper(20f, 20f);

        float wandered = furthestOffTheLine(simulation, trooper, 20f, 120);

        assertThat(wandered).isLessThan(0.5f);
    }

    /**
     * The decision urgency was chosen for. It blends the route against the sweep and leaves the pace
     * alone - so a squad ambling and a squad beelining cover ground at the same rate, and the only
     * thing that changes how fast they walk is an order to move out.
     */
    @Test
    void keepsTheSameWalkingPaceWhateverTheUrgency() {
        assertThat(paceOf(withUrgency(0.2f))).isCloseTo(TROOPER_SPEED, TOLERANCE);
        assertThat(paceOf(withUrgency(1f))).isCloseTo(TROOPER_SPEED, TOLERANCE);
    }

    /** The sweep is what "looking around" is, and pillar 5 says it has to be on screen. */
    @Test
    void sweepsItsAttentionFromSideToSideAsItGoes() {
        Simulation simulation = simulation();
        simulation.spawnCommander(CORNER, CORNER);
        simulation.spawnControlPoint(90f, 20f);
        Entity trooper = simulation.spawnTrooper(20f, 20f);
        steps(simulation, 5);

        float first = trooper.getComponent(Appearance.class).facingY;
        steps(simulation, ticksIn(2f));
        float later = trooper.getComponent(Appearance.class).facingY;

        assertThat(first).isNotEqualTo(later);
    }

    /** Its facing is a decision, not a side effect: it is published even before it has taken a step. */
    @Test
    void looksSomewhereFromTheFirstTick() {
        Simulation simulation = simulation();
        simulation.spawnCommander(CORNER, CORNER);
        Entity trooper = simulation.spawnTrooper(20f, 20f);

        simulation.step();

        Appearance appearance = trooper.getComponent(Appearance.class);
        assertThat(appearance.facingX * appearance.facingX + appearance.facingY * appearance.facingY)
                .isGreaterThan(0.9f);
    }

    /**
     * Standing on a point beats shuffling towards its middle. Everyone inside the radius counts the
     * same, so walking further in buys nothing, and a squad piled on one spot cannot see past itself.
     */
    @Test
    void standsOnAPointRatherThanWalkingAtItsMiddle() {
        Simulation simulation = simulation();
        simulation.spawnCommander(CORNER, CORNER);
        simulation.spawnControlPoint(MIDDLE, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE + 2f, MIDDLE);
        simulation.step();

        float wasX = at(trooper).x;
        steps(simulation, 30);

        assertThat(mode(trooper)).isEqualTo(Trooper.Mode.SECURE);
        assertThat(at(trooper).x).isCloseTo(wasX, Offset.offset(0.6f));
    }

    @Test
    void walksInWhenItIsStillOutsideThePoint() {
        Simulation simulation = simulation();
        simulation.spawnCommander(CORNER, CORNER);
        simulation.spawnControlPoint(MIDDLE, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE + 30f, MIDDLE);

        simulation.step();

        assertThat(mode(trooper)).isEqualTo(Trooper.Mode.ADVANCE);
    }

    /**
     * A squad holding a point watches its surroundings rather than its middle. Without a bearing each,
     * every man keeps the direction he walked in on and they all face inwards, which reads as four
     * men staring at the same patch of ground.
     */
    @Test
    void givesEveryManHoldingAPointHisOwnBearingToWatch() {
        Simulation simulation = simulation();
        simulation.spawnCommander(CORNER, CORNER);
        simulation.spawnControlPoint(MIDDLE, MIDDLE);
        Entity first = simulation.spawnTrooper(MIDDLE + 1f, MIDDLE);
        Entity second = simulation.spawnTrooper(MIDDLE - 1f, MIDDLE);
        Entity third = simulation.spawnTrooper(MIDDLE, MIDDLE + 1f);

        steps(simulation, 5);

        assertThat(facing(first)).isNotEqualTo(facing(second));
        assertThat(facing(second)).isNotEqualTo(facing(third));
    }

    private static float facing(Entity trooper) {
        return trooper.getComponent(Appearance.class).facingX;
    }

    private static Simulation withUrgency(float urgency) {
        SimConfig base = config();
        return new Simulation(base.withTrooper(base.trooper().withUrgency(urgency)));
    }

    /** How far off the straight line to the objective the trooper ever strays, in world units. */
    private static float furthestOffTheLine(Simulation simulation, Entity trooper, float line, int ticks) {
        float furthest = 0f;
        for (int i = 0; i < ticks; i++) {
            simulation.step();
            float off = Math.abs(at(trooper).y - line);
            if (off > furthest) {
                furthest = off;
            }
        }
        return furthest;
    }

    /** How fast a trooper actually walks in a world configured this way. */
    private static float paceOf(Simulation simulation) {
        simulation.spawnCommander(CORNER, CORNER);
        simulation.spawnControlPoint(90f, 20f);
        Entity trooper = simulation.spawnTrooper(20f, 20f);
        steps(simulation, 20);

        Velocity velocity = trooper.getComponent(Velocity.class);
        return (float) Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y);
    }

    private static boolean moved(Entity trooper, float fromX, float fromY) {
        Position at = at(trooper);
        return Math.abs(at.x - fromX) > 0.5f || Math.abs(at.y - fromY) > 0.5f;
    }

    private static Trooper.Mode mode(Entity trooper) {
        return trooper.getComponent(Trooper.class).mode;
    }

    private static Position at(Entity trooper) {
        return trooper.getComponent(Position.class);
    }
}
