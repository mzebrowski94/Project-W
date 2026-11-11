package pl.mzebrows.pwt.sim.system.motion;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.BLOODHOUND_PERCENT;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DETECTION_RANGE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.LOSE_RANGE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.WANDER_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.wandering;
import static pl.mzebrows.pwt.sim.AlphaWorlds.watching;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.AlphaWorlds;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;

class WanderSystemTest {

    private static final float MIDDLE = 50f;
    private static final int A_WHILE = 300;
    private static final int A_LONG_WHILE = 900;

    /** How far past its radius a unit may drift: it turns for home on arrival, it does not stop dead. */
    private static final float SLACK = 5f;

    /** Well outside its patch, so walking home is a journey rather than a step. */
    private static final float DRAGGED = 30f;

    /** Where a unit starts, and where it has chased something to. Far enough apart to tell apart. */
    private static final float SPAWNED_AT = MIDDLE + DETECTION_RANGE - 1f;

    private static final float GAVE_UP_AT = 20f;

    /** Close enough to be noticed, far enough not to be touching - a fight would end the test early. */
    private static final float WITHIN_SIGHT = 10f;

    @Test
    void movesAnIdleUnitAwayFromWhereItWasSpawned() {
        Simulation simulation = wandering();
        Entity enemy = simulation.spawnEnemy(MIDDLE, MIDDLE);

        steps(simulation, A_WHILE);

        assertThat(distanceFrom(enemy, MIDDLE, MIDDLE)).isGreaterThan(0f);
    }

    @Test
    void keepsAnIdleUnitNearWhereItWasSpawned() {
        Simulation simulation = wandering();
        Entity enemy = simulation.spawnEnemy(MIDDLE, MIDDLE);

        for (int i = 0; i < A_LONG_WHILE; i++) {
            simulation.step();
            assertThat(distanceFrom(enemy, MIDDLE, MIDDLE)).isLessThan(WANDER_RADIUS + SLACK);
        }
    }

    /** The patch is wider, and nothing else: it still ambles about where it was spawned. */
    @Test
    void amblesAboutAWiderPatchOnceTheSwarmTracksBetter() {
        Simulation simulation = wandering();
        AlphaWorlds.hold(simulation, UpgradeKind.BLOODHOUND, 2);
        Entity enemy = simulation.spawnEnemy(MIDDLE, MIDDLE);
        float widened = WANDER_RADIUS * (100 + 2 * BLOODHOUND_PERCENT) / 100f;
        float farthest = 0f;

        for (int i = 0; i < A_LONG_WHILE; i++) {
            simulation.step();
            farthest = Math.max(farthest, distanceFrom(enemy, MIDDLE, MIDDLE));
        }

        assertThat(farthest).isGreaterThan(WANDER_RADIUS + SLACK).isLessThan(widened + SLACK);
    }

    @Test
    void standsStillWhenWanderingIsSwitchedOff() {
        Simulation simulation = watching();
        Entity enemy = simulation.spawnEnemy(MIDDLE, MIDDLE);

        steps(simulation, A_WHILE);

        assertThat(distanceFrom(enemy, MIDDLE, MIDDLE)).isZero();
    }

    @Test
    void leavesAUnitThatIsHuntingToTheHunt() {
        Simulation simulation = wandering();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity enemy = simulation.spawnEnemy(MIDDLE + DETECTION_RANGE - 1f, MIDDLE);

        steps(simulation, 30);

        assertThat(distanceFrom(enemy, MIDDLE, MIDDLE)).isLessThan(DETECTION_RANGE - 1f);
    }

    @Test
    void walksBackToItsSpawnPointWhenItFindsItselfAwayFromIt() {
        Simulation simulation = wandering();
        Entity enemy = simulation.spawnEnemy(MIDDLE, MIDDLE);
        enemy.getComponent(Position.class).set(MIDDLE + DRAGGED, MIDDLE);

        steps(simulation, A_LONG_WHILE);

        assertThat(distanceFrom(enemy, MIDDLE, MIDDLE)).isLessThan(WANDER_RADIUS + SLACK);
    }

    /**
     * Where a chase ends is where it lives now. Anchoring the patch to the spawn instead sent a unit
     * that had broken off back across the level, which reads as the level rewinding rather than as a
     * creature losing interest.
     */
    @Test
    void settlesWhereItGaveUpRatherThanWhereItWasSpawned() {
        Simulation simulation = wandering();
        Entity commander = simulation.spawnCommander(SPAWNED_AT - WITHIN_SIGHT, MIDDLE);
        Entity enemy = simulation.spawnEnemy(SPAWNED_AT, MIDDLE);
        simulation.step();

        enemy.getComponent(Position.class).set(GAVE_UP_AT, MIDDLE);
        commander.getComponent(Position.class).set(GAVE_UP_AT - WITHIN_SIGHT, MIDDLE);
        simulation.step();
        commander.getComponent(Position.class).set(GAVE_UP_AT, MIDDLE + LOSE_RANGE * 3f);
        steps(simulation, A_LONG_WHILE);

        assertThat(distanceFrom(enemy, GAVE_UP_AT, MIDDLE)).isLessThan(WANDER_RADIUS + SLACK);
        assertThat(distanceFrom(enemy, SPAWNED_AT, MIDDLE)).isGreaterThan(WANDER_RADIUS + SLACK);
    }

    private static float distanceFrom(Entity entity, float x, float y) {
        Position position = entity.getComponent(Position.class);
        float dx = position.x - x;
        float dy = position.y - y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
