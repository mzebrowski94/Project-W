package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DETECTION_RANGE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.watching;

import com.badlogic.ashley.core.Entity;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.def.Side;

/**
 * Being shot at, and what a unit does about it (G0016).
 *
 * <p>What is latched is a <strong>place</strong>, never whoever landed the hit. Components are
 * pooled, so a reference kept between ticks can come back pointing at whatever was built from the
 * recycled instance - and the place answers the question the player is actually asking, which is
 * "will it come for me".
 *
 * <p>The known cost of that choice is here too: two attackers do not queue up, they compete, and the
 * one nearest the latched place wins. That is emergent rather than guaranteed and it is written down
 * in the decision rather than tested for, because it is a consequence and not a rule.
 */
class AggroTest {

    /** Far enough that nothing in these tests notices him and takes over. */
    private static final float FAR = 10f;

    private static final float MUZZLE = 90f;
    private static final float MIDDLE = 50f;
    private static final Offset<Float> TOLERANCE = Offset.offset(1e-3f);

    @Test
    void sendsAHurtUnitAtWhereTheShotCameFrom() {
        Simulation simulation = watching();
        simulation.spawnCommander(FAR, FAR);
        Entity enemy = simulation.spawnEnemy(MIDDLE, MIDDLE);

        shootFromTheRight(simulation);

        assertThat(perception(enemy).provoked).isTrue();
        assertThat(perception(enemy).provokedX).isCloseTo(MUZZLE, TOLERANCE);
        assertThat(perception(enemy).provokedY).isCloseTo(MIDDLE, TOLERANCE);
    }

    /**
     * The muzzle, not the impact. A shot lands a stride from whatever it hits, so latching where it
     * connected would send the unit walking on the spot.
     */
    @Test
    void walksTowardsTheMuzzleRatherThanStandingWhereItWasHit() {
        Simulation simulation = watching();
        simulation.spawnCommander(FAR, FAR);
        Entity enemy = simulation.spawnEnemy(MIDDLE, MIDDLE);

        shootFromTheRight(simulation);
        simulation.step();

        assertThat(enemy.getComponent(Velocity.class).x).isGreaterThan(0f);
    }

    /** The latch is what makes one shot enough. It does not need the shooting to keep going. */
    @Test
    void keepsWalkingThereAfterTheShootingStops() {
        Simulation simulation = watching();
        simulation.spawnCommander(FAR, FAR);
        Entity enemy = simulation.spawnEnemy(MIDDLE, MIDDLE);
        shootFromTheRight(simulation);

        steps(simulation, 10);

        assertThat(perception(enemy).provoked).isTrue();
        assertThat(enemy.getComponent(Velocity.class).x).isGreaterThan(0f);
    }

    @Test
    void dropsTheGrudgeOnceItGetsThere() {
        Simulation simulation = watching();
        simulation.spawnCommander(FAR, FAR);
        Entity enemy = simulation.spawnEnemy(MIDDLE, MIDDLE);
        shootFromTheRight(simulation);

        steps(simulation, 400);

        assertThat(perception(enemy).provoked).isFalse();
    }

    /**
     * The commander outranks a grudge exactly as absolutely as he outranks food. A unit that notices
     * him while it is walking off to deal with somebody else drops it and does not resume.
     */
    @Test
    void dropsTheGrudgeWhenItNoticesTheCommander() {
        Simulation simulation = watching();
        Entity commander = simulation.spawnCommander(FAR, FAR);
        Entity enemy = simulation.spawnEnemy(MIDDLE, MIDDLE);
        shootFromTheRight(simulation);
        assertThat(perception(enemy).provoked).isTrue();

        commander.getComponent(Position.class).set(MIDDLE - DETECTION_RANGE * 0.5f, MIDDLE);
        simulation.step();

        assertThat(perception(enemy).aggro).isTrue();
        assertThat(perception(enemy).provoked).isFalse();
    }

    @Test
    void provokesNobodyWithAShotThatMisses() {
        Simulation simulation = watching();
        simulation.spawnCommander(FAR, FAR);
        Entity enemy = simulation.spawnEnemy(MIDDLE, MIDDLE);

        simulation.spawnProjectile(MUZZLE, MIDDLE + 20f, -1f, 0f, Side.PLAYER);
        steps(simulation, 40);

        assertThat(perception(enemy).provoked).isFalse();
    }

    /** Fires from the right and steps until the shot has crossed the gap and landed. */
    private static void shootFromTheRight(Simulation simulation) {
        simulation.spawnProjectile(MUZZLE, MIDDLE, -1f, 0f, Side.PLAYER);
        steps(simulation, 40);
    }

    private static Perception perception(Entity entity) {
        return entity.getComponent(Perception.class);
    }
}
