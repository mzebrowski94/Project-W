package pl.mzebrows.pwt.sim.system.squad;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.POINT_CAPTURE_SECONDS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.POINT_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TROOPER_PATCH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Trooper;

/**
 * The capture crowd (G0086): enough men on a point being taken, and the next man to come goes on to the point the
 * squad would take after it - one ahead, and no further (G0040's line). What it is for is a squad that spreads over
 * two points instead of piling twenty men onto one.
 */
class CaptureCrowdTest {

    private static final int CROWD = 5;

    /** Long enough that nothing is taken while the test watches where a man goes. */
    private static final float SLOW_CAPTURE_SECONDS = 60f;

    private static final float LANE = 50f;

    /** The crowded point, the next one after it, and one past that. */
    private static final float CROWDED_X = 50f;

    private static final float NEXT_X = 85f;

    private static final float PAST_Y = 85f;

    /** Where the man who comes to the crowd sets off from: the crowded point is the nearest walk. */
    private static final float FROM_X = 15f;

    /** Inside a point's radius, round its middle, so a crowd stands on it without standing on one another. */
    private static final float[][] ROUND_IT = {{0f, 0f}, {-2f, -2f}, {2f, -2f}, {-2f, 2f}, {2f, 2f}};

    /** Long enough to walk from the start to the next point round the crowd, and to settle there. */
    private static final float THE_WALK_SECONDS = 6f;

    /** Long enough after that for him to have gone on, if anything would send him. */
    private static final float A_WHILE_SECONDS = 10f;

    /** A crowd small enough that one more man than it stands round a point's middle. */
    private static final int SMALL_CROWD = 3;

    /** A point nearer him than the next one once he is on his way there, and further from the crowded one. */
    private static final float NEARER_X = 25f;

    private static final float NEARER_Y = 15f;

    /** A little off the lane, so the crowded point is his nearest walk and the nearer one not yet. */
    private static final float FROM_Y = 52f;

    /** Long enough that he is on his way to the next point, well short of it. */
    private static final float UNDER_WAY_SECONDS = 1f;

    /** How far past a patch's edge a man walking to a spot on it may stray. */
    private static final float SLACK = 5f;

    @Test
    void sendsAManWhoComesToACrowdedPointOnToTheNext() {
        Simulation simulation = slowToTake();
        simulation.spawnControlPoint(CROWDED_X, LANE);
        Entity next = simulation.spawnControlPoint(NEXT_X, LANE);
        standOn(simulation, CROWDED_X, LANE, CROWD);
        Entity comer = simulation.spawnTrooper(FROM_X, LANE);

        steps(simulation, ticksIn(THE_WALK_SECONDS));

        assertThat(distance(comer, next)).isLessThan(POINT_RADIUS);
        assertThat(mode(comer)).isEqualTo(Trooper.Mode.SECURE);
    }

    @Test
    void letsAManJoinAPointWithFewerOnItThanTheCrowd() {
        Simulation simulation = slowToTake();
        Entity crowded = simulation.spawnControlPoint(CROWDED_X, LANE);
        simulation.spawnControlPoint(NEXT_X, LANE);
        standOn(simulation, CROWDED_X, LANE, CROWD - 1);
        Entity comer = simulation.spawnTrooper(FROM_X, LANE);

        steps(simulation, ticksIn(THE_WALK_SECONDS));

        assertThat(distance(comer, crowded)).isLessThan(POINT_RADIUS);
        assertThat(mode(comer)).isEqualTo(Trooper.Mode.SECURE);
    }

    /** G0040's line: one point ahead of the squad, never two. */
    @Test
    void holdsAtTheNextPointRatherThanGoingPastIt() {
        Simulation simulation = slowToTake();
        simulation.spawnControlPoint(CROWDED_X, LANE);
        Entity next = simulation.spawnControlPoint(NEXT_X, LANE);
        simulation.spawnControlPoint(NEXT_X, PAST_Y);
        standOn(simulation, CROWDED_X, LANE, CROWD);
        Entity comer = simulation.spawnTrooper(FROM_X, LANE);

        steps(simulation, ticksIn(THE_WALK_SECONDS + A_WHILE_SECONDS));

        assertThat(distance(comer, next)).isLessThan(POINT_RADIUS);
    }

    /** The crowd is the men settled on it: men who reach it together keep it, and only the spare goes on, once. */
    @Test
    void keepsTheCrowdOnAPointMenReachTogetherAndSendsOnlyTheSpare() {
        Simulation simulation = slowToTake(SMALL_CROWD);
        Entity crowded = simulation.spawnControlPoint(CROWDED_X, LANE);
        Entity next = simulation.spawnControlPoint(NEXT_X, LANE);
        for (int i = 0; i <= SMALL_CROWD; i++) {
            simulation.spawnTrooper(CROWDED_X + ROUND_IT[i][0], LANE + ROUND_IT[i][1]);
        }
        simulation.step();

        for (int i = 0; i < ticksIn(THE_WALK_SECONDS); i++) {
            simulation.step();
            assertThat(menOn(simulation, crowded)).isGreaterThanOrEqualTo(SMALL_CROWD);
        }

        assertThat(menOn(simulation, crowded)).isEqualTo(SMALL_CROWD);
        assertThat(menOn(simulation, next)).isEqualTo(1);
    }

    /** One point ahead, never past it: the point chosen for him holds, though another comes nearer on the way. */
    @Test
    void keepsToThePointHeWasSentOnToThoughAnotherIsNearerOnTheWay() {
        Simulation simulation = slowToTake(CROWD);
        Entity crowded = simulation.spawnControlPoint(CROWDED_X, LANE);
        Entity next = simulation.spawnControlPoint(NEXT_X, LANE);
        Entity nearer = simulation.spawnControlPoint(NEARER_X, NEARER_Y);
        standOn(simulation, CROWDED_X, LANE, CROWD);
        Entity comer = simulation.spawnTrooper(FROM_X, FROM_Y);
        steps(simulation, ticksIn(UNDER_WAY_SECONDS));
        crowded.getComponent(ControlPoint.class).captured = true;

        for (int i = 0; i < ticksIn(THE_WALK_SECONDS); i++) {
            simulation.step();
            assertThat(distance(comer, nearer)).isGreaterThan(POINT_RADIUS);
        }

        assertThat(distance(comer, next)).isLessThan(POINT_RADIUS);
    }

    /** Nowhere further to go: he wanders round this one, as a squad with nothing left to take does. */
    @Test
    void wandersRoundACrowdedPointWithNoNextOne() {
        Simulation simulation = slowToTake();
        Entity crowded = simulation.spawnControlPoint(CROWDED_X, LANE);
        standOn(simulation, CROWDED_X, LANE, CROWD);
        Entity comer = simulation.spawnTrooper(FROM_X, LANE);
        steps(simulation, ticksIn(THE_WALK_SECONDS));

        for (int i = 0; i < ticksIn(A_WHILE_SECONDS); i++) {
            simulation.step();
            assertThat(distance(comer, crowded)).isLessThan(TROOPER_PATCH + SLACK);
        }

        assertThat(mode(comer)).isEqualTo(Trooper.Mode.EXPLORE);
    }

    /** Taken, the point is ground like any other: ground held afterwards is stood round by every man. */
    @Test
    void stopsCountingTheCrowdOnceThePointIsTaken() {
        Simulation simulation = new Simulation(crowdOf(CROWD));
        Entity point = simulation.spawnControlPoint(CROWDED_X, LANE);
        point.getComponent(ControlPoint.class).holdAfterwards(0f);
        standOn(simulation, CROWDED_X, LANE, CROWD);
        Entity comer = simulation.spawnTrooper(FROM_X, LANE);

        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS + THE_WALK_SECONDS));

        assertThat(point.getComponent(ControlPoint.class).captured).isTrue();
        assertThat(mode(comer)).isEqualTo(Trooper.Mode.SECURE);
    }

    private static Simulation slowToTake() {
        return slowToTake(CROWD);
    }

    private static Simulation slowToTake(int crowd) {
        SimConfig crowded = crowdOf(crowd);
        return new Simulation(
                crowded.withControlPoint(crowded.controlPoint().withCaptureSeconds(SLOW_CAPTURE_SECONDS)));
    }

    private static SimConfig crowdOf(int crowd) {
        SimConfig base = config();
        return base.withTrooper(base.trooper().withCaptureCrowd(crowd));
    }

    /** A commander and as many troopers as make up this many men, standing round a point's middle. */
    private static void standOn(Simulation simulation, float x, float y, int men) {
        simulation.spawnCommander(x, y);
        for (int i = 1; i < men; i++) {
            simulation.spawnTrooper(x + ROUND_IT[i][0], y + ROUND_IT[i][1]);
        }
    }

    /** How many troopers stand inside this point. */
    private static int menOn(Simulation simulation, Entity point) {
        int on = 0;
        for (Entity trooper :
                simulation.engine().getEntitiesFor(Family.all(Trooper.class).get())) {
            if (distance(trooper, point) < POINT_RADIUS) {
                on++;
            }
        }
        return on;
    }

    private static Trooper.Mode mode(Entity trooper) {
        return trooper.getComponent(Trooper.class).mode;
    }

    private static float distance(Entity man, Entity point) {
        Position at = man.getComponent(Position.class);
        Position where = point.getComponent(Position.class);
        float dx = at.x - where.x;
        float dy = at.y - where.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
