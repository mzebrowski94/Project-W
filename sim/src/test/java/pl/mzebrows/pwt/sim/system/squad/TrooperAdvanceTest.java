package pl.mzebrows.pwt.sim.system.squad;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DEATH_THROES;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ENEMY_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.HOLD_JITTER_SECONDS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.HOLD_SECONDS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.POINT_CAPTURE_SECONDS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.POINT_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.POST_RING_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.RALLY_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TROOPER_SCAN_SECONDS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TROOPER_SPEED;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ground;
import static pl.mzebrows.pwt.sim.AlphaWorlds.on;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ordering;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;
import static pl.mzebrows.pwt.sim.AlphaWorlds.twoBiomes;

import com.badlogic.ashley.core.Entity;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.sim.AlphaWorlds;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Facing;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.Order;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * How a squad crosses a level: towards what the level wants taken, looking around on the way.
 *
 * <p>Two things here are decisions rather than behaviour, and both are easy to break by accident.
 * <strong>Nearest means nearest by route</strong>, so a squad does not march at a point on the far
 * side of a wall; and <strong>urgency is not a speed</strong>, so that "forward" - which is a speed -
 * stays a thing the player can see happening.
 */
class TrooperAdvanceTest {

    /** Far enough that he neither captures anything nor gets in the way. */
    private static final float CORNER = 95f;

    private static final float MIDDLE = 50f;

    /** In the west half of {@link AlphaWorlds#twoBiomes} and in the east half. */
    private static final float WEST = 20f;

    private static final float EAST = 80f;

    /** Long enough for one man to turn through half the circle a scan at a time, and no longer. */
    private static final float WATCH_SECONDS = 8f;

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

    /**
     * <strong>A point taken is a point held.</strong> The squad stops there and waits to be told to
     * push on, rather than walking off to the next one the moment the bar fills - so ground the squad
     * has taken is ground it is still standing on, and the level advances at the commander's pace
     * rather than at the trooper's.
     */
    @Test
    void waitsOnAPointItHasTakenRatherThanWalkingOnToTheNext() {
        Simulation simulation = simulation();
        simulation.spawnCommander(CORNER, CORNER);
        Entity taken = simulation.spawnControlPoint(20f, 20f);
        simulation.spawnControlPoint(80f, 20f);
        Entity trooper = simulation.spawnTrooper(20f, 20f);
        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) + 4);
        assertThat(taken.getComponent(ControlPoint.class).captured).isTrue();

        float before = at(trooper).x;
        steps(simulation, 120);

        assertThat(at(trooper).x).isCloseTo(before, Offset.offset(2f));
        assertThat(mode(trooper)).isEqualTo(Trooper.Mode.SECURE);
    }

    /** And it is still watching while it waits - a squad holding ground is not a squad asleep. */
    @Test
    void goesOnLookingAroundWhileItWaits() {
        Simulation simulation = simulation();
        simulation.spawnCommander(CORNER, CORNER);
        simulation.spawnControlPoint(20f, 20f);
        Entity trooper = simulation.spawnTrooper(20f, 20f);
        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) + 4);

        float firstLook = facing(trooper);
        boolean lookedElsewhere = false;
        // Sampled every tick rather than once at the end: the sweep is periodic, so one late look
        // can land back exactly where it started and read as a man who never moved his head.
        for (int i = 0; i < 180 && !lookedElsewhere; i++) {
            steps(simulation, 1);
            lookedElsewhere = facing(trooper) != firstLook;
        }

        assertThat(lookedElsewhere).isTrue();
    }

    /**
     * <strong>A man standing on a point turns through his own share of the circle.</strong> Something
     * that never moves - a nest - is found wherever it stands, rather than left working at the squad's
     * back because every man happened to take a bearing the other way.
     */
    @ParameterizedTest
    @CsvSource({"0, 28", "28, 0", "0, -28", "-28, 0"})
    void findsANestOnWhicheverSideOfThePointItStands(float offsetX, float offsetY) {
        Simulation simulation = simulation();
        simulation.spawnCommander(CORNER, CORNER);
        simulation.spawnControlPoint(MIDDLE, MIDDLE);
        Entity first = simulation.spawnTrooper(MIDDLE - 1f, MIDDLE);
        Entity second = simulation.spawnTrooper(MIDDLE + 1f, MIDDLE);
        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) + 4);
        simulation.spawnNest(UnitKind.GRUNT, MIDDLE + offsetX, MIDDLE + offsetY);

        boolean found = false;
        for (int i = 0; i < ticksIn(WATCH_SECONDS) && !found; i++) {
            steps(simulation, 1);
            found = mode(first) == Trooper.Mode.FIGHT || mode(second) == Trooper.Mode.FIGHT;
        }

        assertThat(found).isTrue();
    }

    /** 2.c. "Forward" is what sends them on, which is the whole of why the order exists now. */
    @Test
    void goesOnToTheNextPointWhenHeSaysForward() {
        Simulation simulation = simulation();
        simulation.spawnCommander(20f, 32f);
        simulation.spawnControlPoint(20f, 20f);
        simulation.spawnControlPoint(80f, 20f);
        Entity trooper = simulation.spawnTrooper(20f, 20f);
        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) + 4);

        float before = at(trooper).x;
        simulation.step(ordering(Order.FORWARD));
        steps(simulation, 90);

        assertThat(at(trooper).x).isGreaterThan(before + 5f);
    }

    /**
     * <strong>A squad does not wait to be fetched by a commander who is already where it is going</strong>
     * (G0040). He is near the next point, so the men holding this one set off at once, with no order.
     */
    @Test
    void goesOnAtOnceWhenHeIsAlreadyAtTheNextPoint() {
        Simulation simulation = simulation();
        simulation.spawnCommander(80f, 20f + RALLY_RADIUS - 3f);
        simulation.spawnControlPoint(20f, 20f);
        simulation.spawnControlPoint(80f, 20f);
        Entity trooper = simulation.spawnTrooper(20f, 20f);
        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) + 4);

        float before = at(trooper).x;
        steps(simulation, 60);

        assertThat(at(trooper).x).isGreaterThan(before + 5f);
    }

    /**
     * He has gone on ahead and is nowhere near either point: the men hold a while, and then follow the
     * war on to the next point on their own (G0040).
     */
    @Test
    void goesOnAfterHoldingAWhileWhenHeHasGoneOnAhead() {
        Simulation simulation = simulation();
        simulation.spawnCommander(60f, 20f + RALLY_RADIUS + 5f);
        simulation.spawnControlPoint(20f, 20f);
        simulation.spawnControlPoint(80f, 20f);
        Entity trooper = simulation.spawnTrooper(20f, 20f);
        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) + 4);
        float before = at(trooper).x;

        steps(simulation, ticksIn(HOLD_SECONDS - HOLD_JITTER_SECONDS) - 10);
        float whileHolding = at(trooper).x;
        steps(simulation, ticksIn(2f * HOLD_JITTER_SECONDS) + 70);

        assertThat(whileHolding).isCloseTo(before, Offset.offset(2f));
        assertThat(at(trooper).x).isGreaterThan(before + 5f);
    }

    /** Each man holds for his own while, so a squad peels off a point rather than leaving it in step. */
    @Test
    void peelsOffThePointOneManAtATimeRatherThanInStep() {
        Simulation simulation = simulation();
        simulation.spawnCommander(60f, 20f + RALLY_RADIUS + 5f);
        simulation.spawnControlPoint(20f, 20f);
        simulation.spawnControlPoint(80f, 20f);
        Entity first = simulation.spawnTrooper(19f, 19f);
        Entity second = simulation.spawnTrooper(21f, 21f);
        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) + 4);

        int firstLeft = -1;
        int secondLeft = -1;
        for (int tick = 0; tick < ticksIn(3f * HOLD_SECONDS) && (firstLeft < 0 || secondLeft < 0); tick++) {
            steps(simulation, 1);
            firstLeft = firstLeft < 0 && mode(first) != Trooper.Mode.SECURE ? tick : firstLeft;
            secondLeft = secondLeft < 0 && mode(second) != Trooper.Mode.SECURE ? tick : secondLeft;
        }

        assertThat(firstLeft).isNotNegative();
        assertThat(secondLeft).isNotNegative().isNotEqualTo(firstLeft);
    }

    /** The tether: however long they have held, men never walk off ahead of a commander still behind them. */
    @Test
    void neverLeavesACommanderBehind() {
        Simulation simulation = simulation();
        simulation.spawnCommander(5f, 20f + RALLY_RADIUS + 5f);
        simulation.spawnControlPoint(20f, 20f);
        simulation.spawnControlPoint(80f, 20f);
        Entity trooper = simulation.spawnTrooper(20f, 20f);
        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) + 4);

        float before = at(trooper).x;
        steps(simulation, ticksIn(3f * HOLD_SECONDS));

        assertThat(at(trooper).x).isCloseTo(before, Offset.offset(2f));
        assertThat(mode(trooper)).isEqualTo(Trooper.Mode.SECURE);
    }

    /** He is standing on the point with them, even on its far side: they are holding it together. */
    @Test
    void staysWhileHeIsStandingOnThePointWithThem() {
        Simulation simulation = simulation();
        simulation.spawnCommander(30f + RALLY_RADIUS - 3f, 20f);
        simulation.spawnControlPoint(30f, 20f);
        simulation.spawnControlPoint(80f, 20f);
        Entity trooper = simulation.spawnTrooper(26f, 20f);
        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) + 4);

        float before = at(trooper).x;
        steps(simulation, ticksIn(3f * HOLD_SECONDS));

        assertThat(at(trooper).x).isCloseTo(before, Offset.offset(2f));
    }

    /** One press, one point: it holds the next one too rather than running the level on its own. */
    @Test
    void waitsAgainOnceItHasTakenTheNextPoint() {
        Simulation simulation = simulation();
        simulation.spawnCommander(20f, 32f);
        simulation.spawnControlPoint(20f, 20f);
        simulation.spawnControlPoint(50f, 20f);
        simulation.spawnControlPoint(80f, 20f);
        Entity trooper = simulation.spawnTrooper(20f, 20f);
        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) + 4);
        simulation.step(ordering(Order.FORWARD));
        steps(simulation, 400);

        float settled = at(trooper).x;
        steps(simulation, 200);

        assertThat(settled).isCloseTo(50f, Offset.offset(POINT_RADIUS));
        assertThat(at(trooper).x).isCloseTo(settled, Offset.offset(2f));
    }

    /** Nothing taken yet, so nothing to wait on: the first walk of a level needs no order. */
    @Test
    void walksToTheFirstPointWithoutBeingTold() {
        Simulation simulation = simulation();
        simulation.spawnCommander(CORNER, CORNER);
        simulation.spawnControlPoint(80f, 20f);
        Entity trooper = simulation.spawnTrooper(20f, 20f);

        steps(simulation, 60);

        assertThat(at(trooper).x).isGreaterThan(22f);
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
     * thing that changes how fast they walk is an order to push forward.
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

        float first = trooper.getComponent(Facing.class).y;
        steps(simulation, ticksIn(2f));
        float later = trooper.getComponent(Facing.class).y;

        assertThat(first).isNotEqualTo(later);
    }

    /** Its facing is a decision, not a side effect: it is published even before it has taken a step. */
    @Test
    void looksSomewhereFromTheFirstTick() {
        Simulation simulation = simulation();
        simulation.spawnCommander(CORNER, CORNER);
        Entity trooper = simulation.spawnTrooper(20f, 20f);

        simulation.step();

        Facing facing = trooper.getComponent(Facing.class);
        assertThat(facing.x * facing.x + facing.y * facing.y).isGreaterThan(0.9f);
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

    /**
     * Ground held afterwards is taken by the squad as any point is, once a commander has come into its biome
     * (G0061): the last fight starts when he gets there, not when the squad does.
     */
    @Test
    void takesGroundHeldAfterwardsOnceACommanderIsInItsBiome() {
        Simulation simulation = on(twoBiomes());
        simulation.spawnCommander(EAST, CORNER);
        Entity point = heldAfterwards(simulation.spawnControlPoint(EAST, MIDDLE));
        simulation.spawnTrooper(WEST, MIDDLE);

        steps(simulation, ticksIn(6f + POINT_CAPTURE_SECONDS));

        assertThat(point.getComponent(ControlPoint.class).captured).isTrue();
    }

    /** And never before: with every commander still in a biome behind it, the squad leaves it alone (G0061). */
    @Test
    void leavesGroundHeldAfterwardsAloneWhileNoCommanderIsInItsBiome() {
        Simulation simulation = on(twoBiomes());
        simulation.spawnCommander(WEST, CORNER);
        Entity point = heldAfterwards(simulation.spawnControlPoint(EAST, MIDDLE));
        Entity trooper = simulation.spawnTrooper(WEST, MIDDLE);

        steps(simulation, ticksIn(6f + POINT_CAPTURE_SECONDS));

        assertThat(point.getComponent(ControlPoint.class).captured).isFalse();
        assertThat(mode(trooper)).isNotEqualTo(Trooper.Mode.ADVANCE);
    }

    /**
     * Walking in to take it, a man goes inside its radius as on any point - not to the ring, which is where
     * they hold once it is taken (G0061).
     */
    @Test
    void walksIntoGroundHeldAfterwardsRatherThanStoppingOnTheRing() {
        Simulation simulation = simulation();
        simulation.spawnCommander(CORNER, CORNER);
        heldAfterwards(simulation.spawnControlPoint(MIDDLE, MIDDLE));
        Entity trooper = simulation.spawnTrooper(MIDDLE + 40f, MIDDLE);

        steps(simulation, ticksIn(4f));

        assertThat(distance(trooper, MIDDLE, MIDDLE)).isLessThan(POINT_RADIUS);
    }

    /**
     * Nothing is left to take once the held ground is taken, so a man standing on a point behind it -
     * where the reserve's men now come onto the field - follows the war on to it (G0040, G0061).
     */
    @Test
    void goesOnToGroundHeldAfterwardsOnceNothingIsLeftToTake() {
        Simulation simulation = simulation();
        simulation.spawnCommander(80f, 20f + POST_RING_RADIUS - 2f);
        take(simulation.spawnControlPoint(20f, 20f));
        take(heldAfterwards(simulation.spawnControlPoint(80f, 20f)));
        Entity trooper = simulation.spawnTrooper(20f, 20f);

        steps(simulation, ticksIn(5f));

        assertThat(distance(trooper, 80f, 20f)).isCloseTo(POST_RING_RADIUS, Offset.offset(1f));
        assertThat(mode(trooper)).isEqualTo(Trooper.Mode.SECURE);
    }

    /** The ring is where they hold, not a leash: "forward" still pushes the line out past it. */
    @Test
    void stillPushesOutFromTheRingWhenHeSaysForward() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        take(heldAfterwards(simulation.spawnControlPoint(MIDDLE, MIDDLE)));
        simulation.spawnPost(MIDDLE, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE + POST_RING_RADIUS - 1f, MIDDLE);
        steps(simulation, 10);
        assertThat(mode(trooper)).isEqualTo(Trooper.Mode.SECURE);

        simulation.step(ordering(Order.FORWARD));
        steps(simulation, ticksIn(1f));

        assertThat(distance(trooper, MIDDLE, MIDDLE)).isGreaterThan(POST_RING_RADIUS + 5f);
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

    /**
     * Taking a point is not a job that takes a man's attention off the field. He stands on it and
     * goes on sweeping, which is what makes a point somewhere the level can answer rather than
     * somewhere the squad stands blind.
     */
    @Test
    void goesOnLookingAroundWhileItTakesAPoint() {
        Simulation simulation = simulation();
        simulation.spawnCommander(CORNER, CORNER);
        simulation.spawnControlPoint(MIDDLE, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE + 1f, MIDDLE);
        steps(simulation, 2);

        float wasLooking = facing(trooper);
        steps(simulation, ticksIn(TROOPER_SCAN_SECONDS) + 4);

        assertThat(mode(trooper)).isEqualTo(Trooper.Mode.SECURE);
        assertThat(facing(trooper)).isNotEqualTo(wasLooking);
    }

    /**
     * And it shoots what turns up. A fight outranks the point it is standing on - the mode is chosen
     * before anything about objectives is asked - so the squad is never caught watching a bar fill
     * while something walks into it.
     */
    @Test
    void firesFromAPointItIsStandingOnWhenSomethingComesAtIt() {
        Simulation simulation = simulation();
        simulation.spawnCommander(CORNER, CORNER);
        simulation.spawnControlPoint(MIDDLE, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE + 1f, MIDDLE);
        steps(simulation, 2);
        assertThat(mode(trooper)).isEqualTo(Trooper.Mode.SECURE);

        simulation.spawnEnemy(MIDDLE + 12f, MIDDLE);
        trooper.getComponent(Trooper.class).look(1f, 0f);
        steps(simulation, 3);

        assertThat(mode(trooper)).isEqualTo(Trooper.Mode.FIGHT);
        assertThat(AlphaWorlds.count(simulation, Projectile.class)).isGreaterThan(0);
    }

    /** And goes back to holding it once the fight is over, rather than walking off after the body. */
    @Test
    void goesBackToHoldingThePointOnceTheFightIsOver() {
        Simulation simulation = simulation();
        simulation.spawnCommander(CORNER, CORNER);
        simulation.spawnControlPoint(MIDDLE, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE + 1f, MIDDLE);
        Entity enemy = simulation.spawnEnemy(MIDDLE + 12f, MIDDLE);
        trooper.getComponent(Trooper.class).look(1f, 0f);
        steps(simulation, 2);
        assertThat(mode(trooper)).isEqualTo(Trooper.Mode.FIGHT);

        enemy.getComponent(Damage.class).land(ENEMY_HEALTH * 2, Harm.KILL);
        steps(simulation, ticksIn(DEATH_THROES) + 4);

        assertThat(mode(trooper)).isEqualTo(Trooper.Mode.SECURE);
    }

    private static float facing(Entity trooper) {
        return trooper.getComponent(Facing.class).x;
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

    private static Entity heldAfterwards(Entity point) {
        point.getComponent(ControlPoint.class).holdAfterwards(0f);
        return point;
    }

    private static Entity take(Entity point) {
        point.getComponent(ControlPoint.class).captured = true;
        return point;
    }

    private static float distance(Entity trooper, float x, float y) {
        float dx = at(trooper).x - x;
        float dy = at(trooper).y - y;
        return (float) Math.sqrt(dx * dx + dy * dy);
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
