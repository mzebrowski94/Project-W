package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.scenario.Scenario;

/**
 * The harness state machine, tested with no window and no GL context. Everything the alpha-phase
 * controls actually <em>do</em> is here rather than in the screen, which is what makes that possible.
 */
class ScenarioSessionTest {

    private static final float TICK = SimConfig.defaults().tickSeconds();
    private static final CommanderInput IDLE = CommanderInput.IDLE;

    @Test
    void runsTheWorldWhileItIsNotPaused() {
        ScenarioSession session = session();

        session.advance(TICK, IDLE);

        assertThat(session.simulation().tick()).isEqualTo(1L);
    }

    @Test
    void stopsTheClockWhenPaused() {
        ScenarioSession session = session();
        session.apply(HarnessCommand.PAUSE, IDLE);

        session.advance(TICK * 10f, IDLE);

        assertThat(session.simulation().tick()).isZero();
    }

    @Test
    void resumesWhenPauseIsPressedAgain() {
        ScenarioSession session = session();

        session.apply(HarnessCommand.PAUSE, IDLE);
        session.apply(HarnessCommand.PAUSE, IDLE);
        session.advance(TICK, IDLE);

        assertThat(session.simulation().tick()).isEqualTo(1L);
    }

    @Test
    void advancesExactlyOneTickWhenSteppedWhilePaused() {
        ScenarioSession session = session();
        session.apply(HarnessCommand.PAUSE, IDLE);

        session.apply(HarnessCommand.STEP, IDLE);

        assertThat(session.simulation().tick()).isEqualTo(1L);
    }

    /** Otherwise a held step key would run extra ticks on top of the clock's, at no fixed rate. */
    @Test
    void ignoresAStepWhileTheClockIsAlreadyRunning() {
        ScenarioSession session = session();

        session.apply(HarnessCommand.STEP, IDLE);

        assertThat(session.simulation().tick()).isZero();
    }

    @Test
    void buildsAWholeNewWorldOnRestart() {
        ScenarioSession session = session();
        Simulation original = session.simulation();
        session.advance(TICK * 5f, IDLE);

        session.apply(HarnessCommand.RESTART, IDLE);

        assertThat(session.simulation()).isNotSameAs(original);
        assertThat(session.simulation().tick()).isZero();
    }

    /** A restart that stayed paused would look like a restart that did nothing. */
    @Test
    void unpausesOnRestart() {
        ScenarioSession session = session();
        session.apply(HarnessCommand.PAUSE, IDLE);

        session.apply(HarnessCommand.RESTART, IDLE);

        assertThat(session.paused()).isFalse();
    }

    @Test
    void showsTheOverlayUntilItIsToggledOff() {
        ScenarioSession session = session();

        assertThat(session.overlayVisible()).isTrue();
        session.apply(HarnessCommand.TOGGLE_OVERLAY, IDLE);
        assertThat(session.overlayVisible()).isFalse();
    }

    @Test
    void startsWithGodModeOff() {
        ScenarioSession session = session();

        assertThat(session.godMode()).isFalse();
        assertThat(session.simulation().commanderInvulnerable()).isFalse();
    }

    @Test
    void makesTheCommanderUnkillableWhenGodModeIsSwitchedOn() {
        ScenarioSession session = session();

        session.apply(HarnessCommand.TOGGLE_GOD_MODE, IDLE);

        assertThat(session.godMode()).isTrue();
        assertThat(session.simulation().commanderInvulnerable()).isTrue();
    }

    @Test
    void switchesGodModeBackOff() {
        ScenarioSession session = session();

        session.apply(HarnessCommand.TOGGLE_GOD_MODE, IDLE);
        session.apply(HarnessCommand.TOGGLE_GOD_MODE, IDLE);

        assertThat(session.simulation().commanderInvulnerable()).isFalse();
    }

    /** Restarting to try the same thing again should not quietly turn the safety off. */
    @Test
    void carriesGodModeIntoARestartedWorld() {
        ScenarioSession session = session();
        session.apply(HarnessCommand.TOGGLE_GOD_MODE, IDLE);

        session.apply(HarnessCommand.RESTART, IDLE);

        assertThat(session.godMode()).isTrue();
        assertThat(session.simulation().commanderInvulnerable()).isTrue();
    }

    @Test
    void isNotLeavingUntilAskedTo() {
        assertThat(session().leaving()).isFalse();
    }

    /**
     * The screen queues a menu switch the moment this turns true, and a later command must not undo
     * it - the swap is already scheduled by then.
     */
    @Test
    void staysLeavingOnceAskedTo() {
        ScenarioSession session = session();

        session.apply(HarnessCommand.LEAVE, IDLE);
        session.apply(HarnessCommand.PAUSE, IDLE);
        session.apply(HarnessCommand.RESTART, IDLE);

        assertThat(session.leaving()).isTrue();
    }

    /** A paused world is drawn exactly where it is, not part-way towards a tick that is not coming. */
    @Test
    void freezesRenderInterpolationWhilePaused() {
        ScenarioSession session = session();

        session.apply(HarnessCommand.PAUSE, IDLE);

        assertThat(session.renderAlpha()).isEqualTo(1f);
    }

    @Test
    void populatesTheWorldFromTheScenario() {
        assertThat(session().simulation().engine().getEntities().size()).isEqualTo(1);
    }

    @Test
    void letsAScenarioOverrideTheConfigItRunsWith() {
        ScenarioSession session = new ScenarioSession(new FasterCommander(), SimConfig.defaults());

        assertThat(session.simulation().config().commander().speed()).isEqualTo(FasterCommander.SPEED);
    }

    private static ScenarioSession session() {
        return new ScenarioSession(new OneCommander(), SimConfig.defaults());
    }

    /** The smallest scenario that satisfies the registry's rules: exactly one commander, nothing else. */
    private static final class OneCommander implements Scenario {
        @Override
        public String name() {
            return "One commander";
        }

        @Override
        public String description() {
            return "A single commander and an empty arena.";
        }

        @Override
        public void populate(Simulation simulation) {
            simulation.spawnCommander(10f, 10f);
        }
    }

    private static final class FasterCommander implements Scenario {
        static final float SPEED = 999f;

        @Override
        public String name() {
            return "Faster commander";
        }

        @Override
        public String description() {
            return "Overrides the commander's speed.";
        }

        @Override
        public SimConfig configure(SimConfig base) {
            return base.with(base.commander().withSpeed(SPEED));
        }

        @Override
        public void populate(Simulation simulation) {
            simulation.spawnCommander(10f, 10f);
        }
    }
}
