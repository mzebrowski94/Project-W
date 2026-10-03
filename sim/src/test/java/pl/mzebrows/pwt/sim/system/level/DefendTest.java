package pl.mzebrows.pwt.sim.system.level;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TROOPER_PATCH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.harmless;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ordering;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;
import static pl.mzebrows.pwt.sim.AlphaWorlds.watching;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Level;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.Objective;
import pl.mzebrows.pwt.sim.def.Order;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * A level that tells you where to stand instead of where to go. The post is ground the other side
 * walks at, so the fighting comes to you in waves and the level is not done until the last of them
 * has been seen off.
 */
class DefendTest {

    private static final float POST_X = 30f;

    private static final float LANE = 50f;

    /** Out at the far end, well beyond anything's detection range from the post. */
    private static final float EAST = 80f;

    /** Where he stands in the tests that are about a creature which has noticed nobody at all. */
    private static final float OUT_OF_THE_WAY = 10f;

    private static final float SECONDS = 1f;

    /** How far a post that names its own ground watches, in the fixture arena. */
    private static final float WATCH = 20f;

    private static final TileRect FAR_CORNER = new TileRect(16, 16, 2, 2);

    /** More than anything in the fixture has. A test tidying the field does not need a fair fight. */
    private static final int LETHAL = 1000;

    @Test
    void walksTheAliensAtThePostWhenTheyHaveNoticedNobody() {
        Simulation simulation = watching();
        simulation.spawnCommander(OUT_OF_THE_WAY, LANE);
        simulation.spawnPost(POST_X, LANE);
        Entity grunt = simulation.spawnUnit(UnitKind.GRUNT, EAST - 10f, LANE);

        steps(simulation, ticksIn(SECONDS));

        assertThat(at(grunt).x).isLessThan(EAST - 10f);
    }

    /** Somewhere to be, not somewhere to go: anything it notices outranks the walk. */
    @Test
    void leavesThePostForSomebodyItHasNoticed() {
        Simulation simulation = watching();
        simulation.spawnCommander(EAST, LANE);
        simulation.spawnPost(POST_X, LANE);
        Entity grunt = simulation.spawnUnit(UnitKind.GRUNT, EAST - 10f, LANE);

        simulation.spawnTrooper(EAST - 4f, LANE);
        steps(simulation, ticksIn(SECONDS));

        assertThat(at(grunt).x).isGreaterThan(EAST - 10f);
    }

    @Test
    void sendsInAWaveWhenItsTimeComes() {
        Simulation simulation = simulation();
        simulation.spawnCommander(EAST, LANE);
        simulation.waves().add(SECONDS, UnitKind.GRUNT, 3, FAR_CORNER);

        steps(simulation, ticksIn(SECONDS * 0.5f));
        int beforehand = count(simulation, Enemy.class);
        steps(simulation, ticksIn(SECONDS));

        assertThat(beforehand).isZero();
        assertThat(count(simulation, Enemy.class)).isEqualTo(3);
    }

    @Test
    void sendsEachWaveInItsOwnTime() {
        Simulation simulation = simulation();
        simulation.spawnCommander(EAST, LANE);
        simulation.waves().add(SECONDS, UnitKind.GRUNT, 2, FAR_CORNER);
        simulation.waves().add(SECONDS, UnitKind.GRUNT, 2, FAR_CORNER);

        steps(simulation, ticksIn(SECONDS) + 2);
        int afterTheFirst = count(simulation, Enemy.class);
        steps(simulation, ticksIn(SECONDS) + 2);

        assertThat(afterTheFirst).isEqualTo(2);
        assertThat(count(simulation, Enemy.class)).isEqualTo(4);
    }

    @Test
    void holdsThePostOnlyOnceTheLastWaveHasBeenSeenOff() {
        Simulation simulation = simulation();
        simulation.spawnCommander(EAST, LANE);
        simulation.spawnPost(POST_X, LANE);
        Level level = simulation.level();
        level.require(Objective.HOLD_POINT);
        simulation.waves().add(SECONDS, UnitKind.GRUNT, 1, FAR_CORNER);

        steps(simulation, 2);
        boolean beforeTheWave = level.isMet(Objective.HOLD_POINT);
        steps(simulation, ticksIn(SECONDS) + 2);
        boolean duringTheWave = level.isMet(Objective.HOLD_POINT);
        killEveryEnemy(simulation);
        steps(simulation, ticksIn(1f));

        assertThat(beforeTheWave).isFalse();
        assertThat(duringTheWave).isFalse();
        assertThat(level.isMet(Objective.HOLD_POINT)).isTrue();
    }

    /**
     * A post that names a watch asks about its own ground rather than about the level.
     *
     * <p>The two are the same thing on a level the size of one fight, which is why nothing had to say
     * it until a level was a march: seven screens with nests still working four biomes back can never
     * be clear, so a post there that meant "the level" would be an objective that could not be met.
     */
    @Test
    void holdsAPostWhoseOwnGroundIsClearWithSomethingStillAliveAcrossTheLevel() {
        Simulation simulation = harmless();
        simulation.spawnCommander(POST_X, LANE);
        simulation.spawnPost(POST_X, LANE, WATCH);
        simulation.level().require(Objective.HOLD_POINT);
        simulation.spawnUnit(UnitKind.GRUNT, POST_X + WATCH * 2f, LANE);

        steps(simulation, 3);

        assertThat(simulation.level().isMet(Objective.HOLD_POINT)).isTrue();
    }

    @Test
    void refusesToHoldAPostWithSomethingStandingOnItsOwnGround() {
        Simulation simulation = harmless();
        simulation.spawnCommander(POST_X, LANE);
        simulation.spawnPost(POST_X, LANE, WATCH);
        simulation.level().require(Objective.HOLD_POINT);
        simulation.spawnUnit(UnitKind.GRUNT, POST_X + WATCH * 0.5f, LANE);

        steps(simulation, 3);

        assertThat(simulation.level().isMet(Objective.HOLD_POINT)).isFalse();
    }

    /** A post that names no watch still means the whole level, which is every level built so far. */
    @Test
    void stillMeansTheWholeLevelForAPostThatNamesNoWatch() {
        Simulation simulation = harmless();
        simulation.spawnCommander(POST_X, LANE);
        simulation.spawnPost(POST_X, LANE);
        simulation.level().require(Objective.HOLD_POINT);
        simulation.spawnUnit(UnitKind.GRUNT, EAST, LANE);

        steps(simulation, 3);

        assertThat(simulation.level().isMet(Objective.HOLD_POINT)).isFalse();
    }

    @Test
    void neverHoldsAPostOnALevelThatHasNone() {
        Simulation simulation = simulation();
        simulation.spawnCommander(EAST, LANE);
        simulation.level().require(Objective.HOLD_POINT);

        steps(simulation, ticksIn(SECONDS));

        assertThat(simulation.level().isMet(Objective.HOLD_POINT)).isFalse();
    }

    /**
     * With nothing in front of them, the squad stays around the thing it was told to hold.
     *
     * <p>A defence has nothing to take, so the squad falls through to roaming - and roaming the map
     * is the wrong answer on the one level whose whole instruction is to stay here.
     */
    @Test
    void keepsItsRoamingAroundThePost() {
        Simulation simulation = simulation();
        simulation.spawnCommander(POST_X + 2f, LANE);
        simulation.spawnPost(POST_X, LANE);
        Entity trooper = simulation.spawnTrooper(POST_X + 6f, LANE);

        steps(simulation, ticksIn(6f));

        Trooper mind = trooper.getComponent(Trooper.class);
        assertThat(mind.hasTarget).isTrue();
        assertThat(fromThePost(mind.targetX, mind.targetY)).isLessThanOrEqualTo(TROOPER_PATCH);
        assertThat(fromThePost(at(trooper).x, at(trooper).y)).isLessThan(TROOPER_PATCH + 2f);
    }

    /** Forward, on a level that tells you where to stand, means away from the thing you are standing on. */
    @Test
    void pushesTheFrontLineAwayFromThePost() {
        Simulation simulation = simulation();
        simulation.spawnCommander(POST_X + 2f, LANE);
        simulation.spawnPost(POST_X, LANE);
        Entity trooper = simulation.spawnTrooper(POST_X + 6f, LANE);

        simulation.step(ordering(Order.FORWARD));
        steps(simulation, 2);

        Trooper mind = trooper.getComponent(Trooper.class);
        assertThat(mind.hasTarget).isTrue();
        assertThat(mind.targetX).isGreaterThan(at(trooper).x);
    }

    private static float fromThePost(float x, float y) {
        float dx = x - POST_X;
        float dy = y - LANE;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static Position at(Entity entity) {
        return entity.getComponent(Position.class);
    }

    /** A wave is cleared once everything it sent is off its feet - a source of drafts (G0049) - and not before. */
    @Test
    void marksAWaveClearedOnlyOnceTheLastOfItsAliensGoesDown() {
        Simulation simulation = simulation();
        simulation.spawnCommander(EAST, LANE);
        simulation.waves().add(SECONDS, UnitKind.GRUNT, 2, FAR_CORNER);
        steps(simulation, ticksIn(SECONDS) + 2);
        ImmutableArray<Entity> aliens =
                simulation.engine().getEntitiesFor(Family.all(Enemy.class).get());
        assertThat(aliens.size()).isEqualTo(2);

        aliens.get(0).getComponent(Damage.class).land(LETHAL, Harm.KILL);
        steps(simulation, 2);
        long withOneStanding = simulation.waves().cleared;
        killEveryEnemy(simulation);
        steps(simulation, 2);

        assertThat(withOneStanding).isZero();
        assertThat(simulation.waves().cleared).isEqualTo(1L);
    }

    private static void killEveryEnemy(Simulation simulation) {
        ImmutableArray<Entity> standing =
                simulation.engine().getEntitiesFor(Family.all(Enemy.class).get());
        for (int i = 0; i < standing.size(); i++) {
            standing.get(i).getComponent(Damage.class).land(LETHAL, Harm.KILL);
        }
    }
}
