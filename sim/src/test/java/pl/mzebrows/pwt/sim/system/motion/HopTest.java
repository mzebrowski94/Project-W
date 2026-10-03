package pl.mzebrows.pwt.sim.system.motion;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ENEMY_SPEED;
import static pl.mzebrows.pwt.sim.AlphaWorlds.HOP_SECONDS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.REST_SECONDS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.hopping;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * The gait: a creature that gets about in bursts with a sit between them.
 *
 * <p>The thing worth holding on to is that it is <strong>not a speed change</strong>. A gait paces a
 * course something else chose and hands back exactly the ground a walk would have covered, so
 * {@code speed} on a strain means one thing across the whole table and a hopping creature can be
 * tuned against a walking one without arithmetic.
 *
 * <p>The phase comes from where a unit was put down, so two of them are out of step without anything
 * being drawn from the {@code Rng} - the same trick a weave plays, and the same reason: a pack that
 * hops in unison reads as one animal.
 */
class HopTest {

    /** Well clear of anything, and far enough east to be chased at for a second. */
    private static final float COMMANDER_X = 95f;

    private static final float Y = 50f;

    /** Its middle plus its row lands exactly on a cycle boundary, so it sets off mid-hop. */
    private static final float IN_STEP_X = 30f;

    /** Half a cycle out from the one above, so it sets off sitting. */
    private static final float OUT_OF_STEP_X = 30.5f;

    /** Far enough from the first that the solver never has to prise the two apart. */
    private static final float OUT_OF_STEP_Y = 60f;

    private static final float CYCLE = HOP_SECONDS + REST_SECONDS;

    @Test
    void coversTheSameGroundInHopsAsItWouldAtAWalk() {
        Simulation simulation = hopping();
        simulation.spawnCommander(COMMANDER_X, Y);
        Entity frog = simulation.spawnUnit(UnitKind.FROG, IN_STEP_X, Y);

        steps(simulation, ticksIn(CYCLE));

        assertThat(frog.getComponent(Position.class).x).isCloseTo(IN_STEP_X + ENEMY_SPEED * CYCLE, Offset.offset(0.6f));
    }

    /** The sit is the whole of what makes one worth shooting at: it is repeatedly not going anywhere. */
    @Test
    void goesNowhereAtAllBetweenHops() {
        Simulation simulation = hopping();
        simulation.spawnCommander(COMMANDER_X, Y);
        Entity frog = simulation.spawnUnit(UnitKind.FROG, IN_STEP_X, Y);

        steps(simulation, ticksIn(HOP_SECONDS + REST_SECONDS * 0.5f));

        Velocity velocity = frog.getComponent(Velocity.class);
        assertThat(velocity.x).isZero();
        assertThat(velocity.y).isZero();
    }

    @Test
    void putsTwoSpawnedApartOutOfStepWithEachOther() {
        Simulation simulation = hopping();
        simulation.spawnCommander(COMMANDER_X, Y);
        Entity first = simulation.spawnUnit(UnitKind.FROG, IN_STEP_X, Y);
        Entity second = simulation.spawnUnit(UnitKind.FROG, OUT_OF_STEP_X, OUT_OF_STEP_Y);

        steps(simulation, ticksIn(HOP_SECONDS * 0.5f));

        assertThat(first.getComponent(Position.class).x).isGreaterThan(IN_STEP_X);
        assertThat(second.getComponent(Position.class).x).isEqualTo(OUT_OF_STEP_X);
    }

    /** Twice over on every run, because a gait drawn from the dice would be a gait nobody can read. */
    @Test
    void hopsTheSameWayOnEveryRun() {
        assertThat(walkedInOneCycle()).isEqualTo(walkedInOneCycle());
    }

    private static float walkedInOneCycle() {
        Simulation simulation = hopping();
        simulation.spawnCommander(COMMANDER_X, Y);
        Entity frog = simulation.spawnUnit(UnitKind.FROG, IN_STEP_X, Y);

        steps(simulation, ticksIn(CYCLE));

        return frog.getComponent(Position.class).x;
    }
}
