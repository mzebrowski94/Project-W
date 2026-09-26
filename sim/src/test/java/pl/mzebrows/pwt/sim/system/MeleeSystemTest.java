package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.MELEE_ACTIVE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.MELEE_COOLDOWN;
import static pl.mzebrows.pwt.sim.AlphaWorlds.MELEE_REACH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.harmless;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.AlphaWorlds;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Strike;
import pl.mzebrows.pwt.sim.component.Stunned;

/**
 * Swinging. The sweep is a real body in the world that travels an arc and is gone (G0005), so what is
 * tested here is where it goes and when - never whether it hit anything, which belongs to collision.
 */
class MeleeSystemTest {

    private static final CommanderInput EAST = new CommanderInput(0f, 0f, 60f, 50f, false, true);
    private static final CommanderInput NORTH_AIM_EAST_WALK = new CommanderInput(1f, 0f, 50f, 60f, false, true);
    private static final CommanderInput IDLE_AIMED = new CommanderInput(0f, 0f, 60f, 50f, false, false);

    /** Walking east and swinging east: the case where an anchored sweep is left behind him. */
    private static final CommanderInput EAST_AIM_EAST_WALK = new CommanderInput(1f, 0f, 90f, 50f, false, true);

    @Test
    void putsASweepInTheWorldWhenTheMeleeInputIsPressed() {
        Simulation simulation = simulation();
        simulation.spawnCommander(50f, 50f);

        simulation.step(EAST);

        assertThat(count(simulation, Strike.class)).isEqualTo(1);
    }

    @Test
    void putsNothingInTheWorldWithoutTheInput() {
        Simulation simulation = simulation();
        simulation.spawnCommander(50f, 50f);

        simulation.step(IDLE_AIMED);

        assertThat(count(simulation, Strike.class)).isZero();
    }

    @Test
    void keepsTheSweepAtArmsLength() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(50f, 50f);

        simulation.step(EAST);

        assertThat(distanceBetween(commander, sweep(simulation))).isCloseTo(MELEE_REACH, Offset.offset(0.2f));
    }

    /** Halfway through the arc the sweep is where the player pointed; the ends are the wind and follow. */
    @Test
    void carriesTheSweepThroughWhereThePlayerIsAiming() {
        Simulation simulation = simulation();
        simulation.spawnCommander(50f, 50f);

        steps(simulation, ticksIn(MELEE_ACTIVE) / 2, EAST);

        Position position = sweep(simulation).getComponent(Position.class);
        assertThat(position.x).isCloseTo(50f + MELEE_REACH, Offset.offset(0.2f));
        assertThat(position.y).isCloseTo(50f, Offset.offset(0.2f));
    }

    /** G0002: the swing goes where he points, not where he is running. */
    @Test
    void aimsTheSweepWhereTheCommanderPointsNotWhereHeWalks() {
        Simulation simulation = simulation();
        simulation.spawnCommander(50f, 50f);

        steps(simulation, ticksIn(MELEE_ACTIVE) / 2, NORTH_AIM_EAST_WALK);

        Position position = sweep(simulation).getComponent(Position.class);
        assertThat(position.y).isGreaterThan(50f + MELEE_REACH / 2f);
    }

    /**
     * <strong>The sweep is in front of him, not behind where he set off from.</strong> The arc used
     * to be centred where he stood when the swing was released, and a commander covers several
     * strides while one is travelling - so using the hand strike while running put the sweep out of
     * his back. Half an arc in, walking east, it must be east of him.
     */
    @Test
    void keepsTheSweepInFrontOfHimWhileHeIsWalking() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(50f, 50f);

        steps(simulation, ticksIn(MELEE_ACTIVE) / 2, EAST_AIM_EAST_WALK);

        assertThat(sweep(simulation).getComponent(Position.class).x)
                .isCloseTo(commander.getComponent(Position.class).x + MELEE_REACH, Offset.offset(0.3f));
    }

    @Test
    void takesTheSweepAwayOnceItHasTravelledItsArc() {
        Simulation simulation = simulation();
        simulation.spawnCommander(50f, 50f);

        steps(simulation, ticksIn(MELEE_ACTIVE) + 2, EAST);

        assertThat(count(simulation, Strike.class)).isZero();
    }

    @Test
    void swingsOnlyOnceWithinItsCooldown() {
        Simulation simulation = harmless();
        simulation.spawnCommander(50f, 50f);

        int swings = countSwings(simulation, ticksIn(MELEE_COOLDOWN) - 4);

        assertThat(swings).isEqualTo(1);
    }

    @Test
    void swingsAgainOnceTheCooldownHasPassed() {
        Simulation simulation = harmless();
        simulation.spawnCommander(50f, 50f);

        int swings = countSwings(simulation, ticksIn(MELEE_COOLDOWN * 2f));

        assertThat(swings).isEqualTo(2);
    }

    @Test
    void swingsNothingWhileTheCommanderIsHeld() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(50f, 50f);
        commander.getComponent(Stunned.class).apply(AlphaWorlds.ticksIn(AlphaWorlds.MELEE_LOCK), 0f);

        simulation.step(EAST);

        assertThat(count(simulation, Strike.class)).isZero();
    }

    /** A weapon with a wind-up commits the player before it commits the sweep. The hand strike has none. */
    @Test
    void holdsBackTheSweepUntilAWeaponWithAWindUpHasFinishedIt() {
        SimConfig config = AlphaWorlds.config();
        Simulation simulation = new Simulation(config.withMelee(config.melee().withWindupSeconds(0.2f)));
        simulation.spawnCommander(50f, 50f);

        simulation.step(EAST);

        assertThat(count(simulation, Strike.class)).isZero();
    }

    @Test
    void releasesTheSweepOnceTheWindUpIsOver() {
        SimConfig config = AlphaWorlds.config();
        Simulation simulation = new Simulation(config.withMelee(config.melee().withWindupSeconds(0.2f)));
        simulation.spawnCommander(50f, 50f);

        steps(simulation, ticksIn(0.2f) + 2, EAST);

        assertThat(count(simulation, Strike.class)).isEqualTo(1);
    }

    /** Counts separate swings rather than ticks a sweep existed for. */
    private static int countSwings(Simulation simulation, int ticks) {
        int swings = 0;
        boolean swinging = false;
        for (int i = 0; i < ticks; i++) {
            simulation.step(EAST);
            boolean present = count(simulation, Strike.class) > 0;
            if (present && !swinging) {
                swings++;
            }
            swinging = present;
        }
        return swings;
    }

    private static Entity sweep(Simulation simulation) {
        return simulation
                .engine()
                .getEntitiesFor(Family.all(Strike.class).get())
                .first();
    }

    private static float distanceBetween(Entity first, Entity second) {
        Position a = first.getComponent(Position.class);
        Position b = second.getComponent(Position.class);
        float dx = b.x - a.x;
        float dy = b.y - a.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
