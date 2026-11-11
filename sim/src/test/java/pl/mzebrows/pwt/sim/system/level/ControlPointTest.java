package pl.mzebrows.pwt.sim.system.level;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.POINT_AGGRO_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.POINT_CAPTURE_SECONDS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.POINT_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.POINT_REINFORCEMENT_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.harmless;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;
import static pl.mzebrows.pwt.sim.AlphaWorlds.watching;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Level;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Pickup;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Post;
import pl.mzebrows.pwt.sim.component.Respawning;
import pl.mzebrows.pwt.sim.def.Objective;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * Taking ground, and what the level makes of it.
 *
 * <p>The reset is the rule most worth pinning. Dwell that quietly persists turns a point into a
 * number the player is tracking across minutes, and it removes the only reason an alien has to push
 * a trooper off one.
 */
class ControlPointTest {

    private static final float MIDDLE = 50f;

    /** Far enough away that a unit put here is neither capturing nor within earshot of the point. */
    private static final float ELSEWHERE = 95f;

    private static final Offset<Float> TOLERANCE = Offset.offset(1e-3f);

    /** How far the post a held point becomes watches. Any positive number; the value is the level's. */
    private static final float WATCH = 20f;

    /** Caches enough to crowd the ring, and the drops lying round it in a circle this far out. */
    private static final int CROWD = 20;

    private static final int DROPS = 12;

    private static final float DROP_RING = 9f;

    /** A corner of the fixture arena for a wave to arrive in. */
    private static final TileRect A_CORNER = new TileRect(16, 16, 2, 2);

    @Test
    void takesAPointHeldForLongEnough() {
        Simulation simulation = harmless();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity point = simulation.spawnControlPoint(MIDDLE, MIDDLE);

        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) + 2);

        assertThat(point.getComponent(ControlPoint.class).captured).isTrue();
    }

    @Test
    void takesAPointOnTheTickItsDwellRunsOutAndNotBefore() {
        Simulation simulation = harmless();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity point = simulation.spawnControlPoint(MIDDLE, MIDDLE);

        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) - 1);
        boolean takenEarly = point.getComponent(ControlPoint.class).captured;
        simulation.step();

        assertThat(takenEarly).isFalse();
        assertThat(point.getComponent(ControlPoint.class).captured).isTrue();
    }

    /** The rally point is the point taken last (G0055), so each remembers when in the level it fell. */
    @Test
    void numbersThePointsInTheOrderTheyAreTaken() {
        Simulation simulation = harmless();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity first = simulation.spawnControlPoint(MIDDLE, MIDDLE);
        Entity second = simulation.spawnControlPoint(ELSEWHERE, ELSEWHERE);
        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) + 2);

        commander.getComponent(Position.class).set(ELSEWHERE, ELSEWHERE);
        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) + 2);

        assertThat(first.getComponent(ControlPoint.class).takenOrder).isEqualTo(1);
        assertThat(second.getComponent(ControlPoint.class).takenOrder).isEqualTo(2);
    }

    @Test
    void leavesAPointNobodyIsStandingOnAlone() {
        Simulation simulation = harmless();
        simulation.spawnCommander(ELSEWHERE, ELSEWHERE);
        Entity point = simulation.spawnControlPoint(MIDDLE, MIDDLE);

        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) + 2);

        assertThat(point.getComponent(ControlPoint.class).captured).isFalse();
        assertThat(point.getComponent(ControlPoint.class).heldTicks).isZero();
    }

    /** 2.d, and the whole reason holding one is a fight rather than an errand. */
    @Test
    void startsTheCaptureAgainWhenTheLastCapturerLeaves() {
        Simulation simulation = harmless();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity point = simulation.spawnControlPoint(MIDDLE, MIDDLE);
        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS * 0.5f));
        assertThat(point.getComponent(ControlPoint.class).heldTicks).isPositive();

        commander.getComponent(Position.class).set(ELSEWHERE, ELSEWHERE);
        simulation.step();

        assertThat(point.getComponent(ControlPoint.class).heldTicks).isZero();
        assertThat(point.getComponent(ControlPoint.class).captured).isFalse();
    }

    /** Pillar 5: a point filling up rides the same channel as a unit filling up, so it is drawn. */
    @Test
    void publishesHowFarAlongACaptureIs() {
        Simulation simulation = harmless();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity point = simulation.spawnControlPoint(MIDDLE, MIDDLE);

        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS * 0.5f));

        assertThat(point.getComponent(Appearance.class).charge).isBetween(0.3f, 0.7f);
    }

    @Test
    void showsATakenPointAsFullRatherThanAsUntouched() {
        Simulation simulation = harmless();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity point = simulation.spawnControlPoint(MIDDLE, MIDDLE);

        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) + 2);

        assertThat(point.getComponent(Appearance.class).charge).isCloseTo(1f, TOLERANCE);
    }

    /** 2.b. Taking ground is loud, and what it wakes comes to look. */
    @Test
    void sendsWhatIsNearbyToSeeWhoIsOnThePoint() {
        Simulation simulation = watching();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnControlPoint(MIDDLE, MIDDLE);
        Entity enemy = simulation.spawnEnemy(MIDDLE + POINT_AGGRO_RADIUS - 2f, MIDDLE);

        simulation.step();

        Perception perception = enemy.getComponent(Perception.class);
        assertThat(perception.provoked || perception.aggro).isTrue();
    }

    @Test
    void leavesWhatIsOutOfEarshotAlone() {
        Simulation simulation = watching();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnControlPoint(MIDDLE, MIDDLE);
        Entity enemy = simulation.spawnEnemy(ELSEWHERE, ELSEWHERE);

        simulation.step();

        assertThat(enemy.getComponent(Perception.class).provoked).isFalse();
    }

    @Test
    void takesNoNoticeOfSomebodyStandingOutsideTheRadius() {
        Simulation simulation = harmless();
        simulation.spawnCommander(MIDDLE + POINT_RADIUS + 3f, MIDDLE);
        Entity point = simulation.spawnControlPoint(MIDDLE, MIDDLE);

        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) + 2);

        assertThat(point.getComponent(ControlPoint.class).captured).isFalse();
    }

    @Test
    void meetsTheCaptureObjectiveOnceEveryPointIsTaken() {
        Simulation simulation = harmless();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnControlPoint(MIDDLE, MIDDLE);
        level(simulation).require(Objective.CAPTURE_POINTS);

        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) + 2);

        assertThat(level(simulation).isMet(Objective.CAPTURE_POINTS)).isTrue();
        assertThat(level(simulation).complete()).isTrue();
    }

    @Test
    void leavesTheCaptureObjectiveUnmetWhileAnyPointIsStillHeldAgainstYou() {
        Simulation simulation = harmless();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnControlPoint(MIDDLE, MIDDLE);
        simulation.spawnControlPoint(ELSEWHERE, ELSEWHERE);
        level(simulation).require(Objective.CAPTURE_POINTS);

        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) + 2);

        assertThat(level(simulation).isMet(Objective.CAPTURE_POINTS)).isFalse();
        assertThat(level(simulation).complete()).isFalse();
    }

    /** A map with no points on it has not taken them all - it has nothing to take. */
    @Test
    void neverMeetsTheCaptureObjectiveOnAMapWithNoPoints() {
        Simulation simulation = harmless();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        level(simulation).require(Objective.CAPTURE_POINTS);

        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) + 2);

        assertThat(level(simulation).isMet(Objective.CAPTURE_POINTS)).isFalse();
    }

    @Test
    void meetsTheClearObjectiveOnlyWhenNothingIsLeftStanding() {
        Simulation simulation = harmless();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity enemy = simulation.spawnEnemy(ELSEWHERE, ELSEWHERE);
        level(simulation).require(Objective.CLEAR_ENEMIES);
        simulation.step();
        assertThat(level(simulation).isMet(Objective.CLEAR_ENEMIES)).isFalse();

        enemy.getComponent(pl.mzebrows.pwt.sim.component.Damage.class)
                .land(pl.mzebrows.pwt.sim.AlphaWorlds.ENEMY_HEALTH, pl.mzebrows.pwt.sim.def.Harm.KILL);
        simulation.step();

        assertThat(level(simulation).isMet(Objective.CLEAR_ENEMIES)).isTrue();
    }

    /**
     * Both objectives at once, which is the case the whole set-of-objectives shape exists for. Taking
     * the points is not enough while something is still alive to argue about it.
     */
    @Test
    void requiresEveryObjectiveALevelNames() {
        Simulation simulation = harmless();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnControlPoint(MIDDLE, MIDDLE);
        simulation.spawnEnemy(ELSEWHERE, ELSEWHERE);
        level(simulation).require(Objective.CAPTURE_POINTS);
        level(simulation).require(Objective.CLEAR_ENEMIES);

        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) + 2);

        assertThat(level(simulation).isMet(Objective.CAPTURE_POINTS)).isTrue();
        assertThat(level(simulation).complete()).isFalse();
    }

    /** A level that names nothing is never finished, rather than finished before it starts. */
    @Test
    void neverCompletesALevelThatNamesNoObjectives() {
        Simulation simulation = harmless();
        simulation.spawnCommander(MIDDLE, MIDDLE);

        simulation.step();

        assertThat(level(simulation).complete()).isFalse();
    }

    private static Level level(Simulation simulation) {
        return simulation
                .engine()
                .getEntitiesFor(Family.all(Level.class).get())
                .get(0)
                .getComponent(Level.class);
    }

    /**
     * The last ground of a march: taking it is not the end of the level but the start of its last
     * fight. A post goes down where it stands, and whatever the level was holding back is let go.
     */
    @Test
    void putsAPostDownOnGroundThatIsHeldAfterwards() {
        Simulation simulation = harmless();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        held(simulation);

        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) + 2);

        assertThat(count(simulation, Post.class)).isEqualTo(1);
    }

    /**
     * And not before. A post on the field from the first tick sends everything that has noticed
     * nobody walking at it from across the level, and turns "forward" round for the whole march
     * (G0027) - so when it appears is the mechanic rather than a detail.
     */
    @Test
    void namesNoPostWhileThatGroundIsStillBeingTaken() {
        Simulation simulation = harmless();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        held(simulation);

        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS * 0.5f));

        assertThat(count(simulation, Post.class)).isZero();
    }

    @Test
    void leavesAnOrdinaryPointNamingNoPostWhenItIsTaken() {
        Simulation simulation = harmless();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnControlPoint(MIDDLE, MIDDLE);

        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) + 2);

        assertThat(count(simulation, Post.class)).isZero();
    }

    @Test
    void sendsNothingInWhileTheLevelIsStillHoldingItsScheduleBack() {
        Simulation simulation = harmless();
        simulation.spawnCommander(ELSEWHERE, ELSEWHERE);
        held(simulation);
        simulation.waves().holdBack().add(0.1f, UnitKind.GRUNT, 3, A_CORNER);

        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) + 2);

        assertThat(count(simulation, Enemy.class)).isZero();
    }

    @Test
    void letsGoOfThatScheduleOnceTheGroundIsTaken() {
        Simulation simulation = harmless();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        held(simulation);
        simulation.waves().holdBack().add(0.1f, UnitKind.GRUNT, 3, A_CORNER);

        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) + ticksIn(0.2f) + 4);

        assertThat(count(simulation, Enemy.class)).isEqualTo(3);
    }

    /** G0078: a refill where the fighting is, one for every player, so co-op is not a race for it. */
    @Test
    void laysAGrenadeCacheBesideItForEverySeatedSlotWhenItIsTaken() {
        Simulation simulation = new Simulation(config(), 2);
        simulation.spawnCommander(MIDDLE, MIDDLE, 0);
        simulation.spawnCommander(ELSEWHERE, ELSEWHERE, 1);
        simulation.spawnControlPoint(MIDDLE, MIDDLE);
        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) - 1);
        int beforeItFell = grenadeCaches(simulation).size();

        steps(simulation, 3);

        TileRect reach = TileRect.around(simulation.map(), MIDDLE, MIDDLE, POINT_REINFORCEMENT_RADIUS);
        assertThat(beforeItFell).isZero();
        assertThat(grenadeCaches(simulation)).hasSize(2).allSatisfy(cache -> {
            Position at = cache.getComponent(Position.class);
            assertThat(distance(at, MIDDLE, MIDDLE)).isGreaterThan(POINT_RADIUS);
            assertThat(reach.contains(
                            simulation.map().colAt(at.x), simulation.map().rowAt(at.y)))
                    .isTrue();
        });
    }

    /** Seated is seated: a player whose commander is on his way back is still a player, and still gets his. */
    @Test
    void countsASlotWhoseCommanderIsOnHisWayBack() {
        Simulation simulation = new Simulation(config(), 2);
        simulation.spawnCommander(MIDDLE, MIDDLE, 0);
        Entity ticket = simulation.engine().createEntity();
        ticket.add(simulation.engine().createComponent(Respawning.class).set(ticksIn(60f), 0, 1));
        simulation.engine().addEntity(ticket);
        simulation.spawnControlPoint(MIDDLE, MIDDLE);

        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) + 2);

        assertThat(grenadeCaches(simulation)).hasSize(2);
    }

    /** A refill before the hold waves is the point of it: the point the level ends on lays them too. */
    @Test
    void laysThemBesideAPointThatIsHeldAfterwards() {
        Simulation simulation = harmless();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        held(simulation);

        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) + 2);

        assertThat(grenadeCaches(simulation)).hasSize(1);
    }

    /** The point's supply drops lie in the same ring, and a cache laid on one would hide it. */
    @Test
    void laysThemOffWhateverAlreadyLiesBesideIt() {
        // Many caches against a ring of drops, so a cache laid blind to them is all but sure to land on one.
        Simulation simulation =
                new Simulation(config().withControlPoint(config().controlPoint().withGrenadeCaches(CROWD)));
        simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnControlPoint(MIDDLE, MIDDLE);
        for (int i = 0; i < DROPS; i++) {
            float turn = i * (float) (2.0 * Math.PI / DROPS);
            simulation.spawnPickup(
                    PickupKind.SUPPLY_DROP,
                    MIDDLE + DROP_RING * (float) Math.cos(turn),
                    MIDDLE + DROP_RING * (float) Math.sin(turn));
        }

        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) + 2);

        float apart = simulation.config().pickup(PickupKind.ARMOURY_GRENADES).radius()
                + simulation.config().pickup(PickupKind.SUPPLY_DROP).radius();
        for (Entity cache : grenadeCaches(simulation)) {
            for (Entity other :
                    simulation.engine().getEntitiesFor(Family.all(Pickup.class).get())) {
                if (other.getComponent(Pickup.class).kind == PickupKind.SUPPLY_DROP) {
                    assertThat(distance(
                                    cache.getComponent(Position.class),
                                    other.getComponent(Position.class).x,
                                    other.getComponent(Position.class).y))
                            .isGreaterThanOrEqualTo(apart);
                }
            }
        }
    }

    @Test
    void laysThemOnceAndNeverAgainWhileItIsHeld() {
        Simulation simulation = harmless();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnControlPoint(MIDDLE, MIDDLE);

        steps(simulation, ticksIn(POINT_CAPTURE_SECONDS) * 3);

        assertThat(grenadeCaches(simulation)).hasSize(1);
    }

    private static List<Entity> grenadeCaches(Simulation simulation) {
        List<Entity> caches = new ArrayList<>();
        for (Entity entity :
                simulation.engine().getEntitiesFor(Family.all(Pickup.class).get())) {
            if (entity.getComponent(Pickup.class).kind == PickupKind.ARMOURY_GRENADES) {
                caches.add(entity);
            }
        }
        return caches;
    }

    private static float distance(Position at, float x, float y) {
        float dx = at.x - x;
        float dy = at.y - y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /** A point in the middle of the arena that the level says is ground to hold afterwards. */
    private static void held(Simulation simulation) {
        simulation
                .spawnControlPoint(MIDDLE, MIDDLE)
                .getComponent(ControlPoint.class)
                .holdAfterwards(WATCH);
    }
}
