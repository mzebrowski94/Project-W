package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.def.ActionKind;
import pl.mzebrows.pwt.sim.def.Order;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.scenario.Scenario;

/**
 * The harness state machine, tested with no window and no GL context. Everything the alpha-phase
 * controls actually <em>do</em> is here rather than in the screen, which is what makes that possible.
 */
class ScenarioSessionTest {

    private static final float TICK = SimConfig.defaults().tickSeconds();
    private static final CommanderInput IDLE = CommanderInput.IDLE;

    /** Nobody asking for anything, in any slot. Never mutated, so one of them serves every test. */
    private static final PlayerInputs NOBODY = new PlayerInputs();

    @Test
    void runsTheWorldWhileItIsNotPaused() {
        ScenarioSession session = session();

        session.advance(TICK, NOBODY);

        assertThat(session.simulation().tick()).isEqualTo(1L);
    }

    @Test
    void stopsTheClockWhenPaused() {
        ScenarioSession session = session();
        session.apply(HarnessCommand.PAUSE, NOBODY);

        session.advance(TICK * 10f, NOBODY);

        assertThat(session.simulation().tick()).isZero();
    }

    @Test
    void resumesWhenPauseIsPressedAgain() {
        ScenarioSession session = session();

        session.apply(HarnessCommand.PAUSE, NOBODY);
        session.apply(HarnessCommand.PAUSE, NOBODY);
        session.advance(TICK, NOBODY);

        assertThat(session.simulation().tick()).isEqualTo(1L);
    }

    @Test
    void advancesExactlyOneTickWhenSteppedWhilePaused() {
        ScenarioSession session = session();
        session.apply(HarnessCommand.PAUSE, NOBODY);

        session.apply(HarnessCommand.STEP, NOBODY);

        assertThat(session.simulation().tick()).isEqualTo(1L);
    }

    /** Otherwise a held step key would run extra ticks on top of the clock's, at no fixed rate. */
    @Test
    void ignoresAStepWhileTheClockIsAlreadyRunning() {
        ScenarioSession session = session();

        session.apply(HarnessCommand.STEP, NOBODY);

        assertThat(session.simulation().tick()).isZero();
    }

    @Test
    void buildsAWholeNewWorldOnRestart() {
        ScenarioSession session = session();
        Simulation original = session.simulation();
        session.advance(TICK * 5f, NOBODY);

        session.apply(HarnessCommand.RESTART, NOBODY);

        assertThat(session.simulation()).isNotSameAs(original);
        assertThat(session.simulation().tick()).isZero();
    }

    /** A restart that stayed paused would look like a restart that did nothing. */
    @Test
    void unpausesOnRestart() {
        ScenarioSession session = session();
        session.apply(HarnessCommand.PAUSE, NOBODY);

        session.apply(HarnessCommand.RESTART, NOBODY);

        assertThat(session.paused()).isFalse();
    }

    @Test
    void showsTheOverlayUntilItIsToggledOff() {
        ScenarioSession session = session();

        assertThat(session.overlayVisible()).isTrue();
        session.apply(HarnessCommand.TOGGLE_OVERLAY, NOBODY);
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

        session.apply(HarnessCommand.TOGGLE_GOD_MODE, NOBODY);

        assertThat(session.godMode()).isTrue();
        assertThat(session.simulation().commanderInvulnerable()).isTrue();
    }

    @Test
    void switchesGodModeBackOff() {
        ScenarioSession session = session();

        session.apply(HarnessCommand.TOGGLE_GOD_MODE, NOBODY);
        session.apply(HarnessCommand.TOGGLE_GOD_MODE, NOBODY);

        assertThat(session.simulation().commanderInvulnerable()).isFalse();
    }

    /** Restarting to try the same thing again should not quietly turn the safety off. */
    @Test
    void carriesGodModeIntoARestartedWorld() {
        ScenarioSession session = session();
        session.apply(HarnessCommand.TOGGLE_GOD_MODE, NOBODY);

        session.apply(HarnessCommand.RESTART, NOBODY);

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

        session.apply(HarnessCommand.LEAVE, NOBODY);
        session.apply(HarnessCommand.PAUSE, NOBODY);
        session.apply(HarnessCommand.RESTART, NOBODY);

        assertThat(session.leaving()).isTrue();
    }

    /** A paused world is drawn exactly where it is, not part-way towards a tick that is not coming. */
    @Test
    void freezesRenderInterpolationWhilePaused() {
        ScenarioSession session = session();

        session.apply(HarnessCommand.PAUSE, NOBODY);

        assertThat(session.renderAlpha()).isEqualTo(1f);
    }

    @Test
    void populatesTheWorldFromTheScenario() {
        assertThat(session()
                        .simulation()
                        .engine()
                        .getEntitiesFor(Family.all(Commander.class).get())
                        .size())
                .isEqualTo(1);
    }

    @Test
    void letsAScenarioOverrideTheConfigItRunsWith() {
        ScenarioSession session = new ScenarioSession(new FasterCommander(), SimConfig.defaults());

        assertThat(session.simulation().config().commander().speed()).isEqualTo(FasterCommander.SPEED);
    }

    /**
     * The overlay readout. Orders are delivered by the simulation and nothing about them is kept
     * there afterwards, which is right - "what did I last press" is a question about the player, not
     * about the world - so the answer has to live here.
     */
    @Test
    void remembersTheLastOrderTheirPlayerGave() {
        ScenarioSession session = session();

        session.advance(TICK, only(IDLE.ordering(Order.FOLLOW_ME)));
        session.advance(TICK, only(IDLE.ordering(Order.DEFENCE)));
        session.advance(TICK, NOBODY);

        assertThat(session.lastOrder()).isEqualTo(Order.DEFENCE);
    }

    @Test
    void hasNoLastOrderBeforeOneIsGiven() {
        assertThat(session().lastOrder()).isNull();
    }

    /** Paused is still a way to give one, and a readout that disagreed with the key would be worse than none. */
    @Test
    void remembersAnOrderGivenWhilePaused() {
        ScenarioSession session = session();
        session.apply(HarnessCommand.PAUSE, NOBODY);

        session.advance(TICK, only(IDLE.ordering(Order.FORWARD)));

        assertThat(session.lastOrder()).isEqualTo(Order.FORWARD);
    }

    @Test
    void forgetsTheLastOrderWhenTheScenarioRestarts() {
        ScenarioSession session = session();
        session.advance(TICK, only(IDLE.ordering(Order.FOLLOW_ME)));

        session.apply(HarnessCommand.RESTART, NOBODY);

        assertThat(session.lastOrder()).isNull();
    }

    /**
     * The bug this exists to stop coming back. A key is "just pressed" for exactly one <em>frame</em>,
     * and a frame shorter than a tick runs no simulation step at all - which above 60 Hz is most
     * frames. The order used to go into the input record, get shown in the overlay, and reach no tick
     * whatsoever: the console said it had been given and more than half the time nothing happened.
     */
    @Test
    void deliversAnOrderGivenOnAFrameTooShortToRunATick() {
        ScenarioSession session = new ScenarioSession(new CommanderAndTrooper(), SimConfig.defaults());

        session.advance(TICK * 0.4f, only(IDLE.ordering(Order.DEFENCE)));
        session.advance(TICK, NOBODY);

        assertThat(trooperOrder(session)).isEqualTo(Order.DEFENCE);
    }

    /** One press is one order, however many ticks the frame after it happens to run. */
    @Test
    void spendsAnOrderOnceEvenWhenAFrameRunsSeveralTicks() {
        ScenarioSession session = new ScenarioSession(new CommanderAndTrooper(), SimConfig.defaults());

        session.advance(TICK * 3f, only(IDLE.ordering(Order.DEFENCE)));

        assertThat(trooperOrder(session)).isEqualTo(Order.DEFENCE);
    }

    /**
     * Scaffolding for the console prompt: the action is announced when it changes and not otherwise.
     * Reporting it every frame is the failure this exists to prevent - a line per frame is a flood,
     * not a prompt, and the player would stop reading it.
     */
    @Test
    void reportsAnActionWhenOneComesWithinReach() {
        ScenarioSession session = new ScenarioSession(new CommanderAndTrooper(), SimConfig.defaults());
        session.advance(TICK, NOBODY);

        putTheTrooperDown(session);
        session.advance(TICK, NOBODY);

        assertThat(session.newlyOfferedAction(0)).isEqualTo(ActionKind.STABILISE);
    }

    /** The other half of the console scaffolding: which wave has walked in, once, when it does. */
    @Test
    void reportsAWaveWhenItArrives() {
        ScenarioSession session = new ScenarioSession(new OneWave(), SimConfig.defaults());

        session.advance(TICK, NOBODY);

        assertThat(session.newlyArrivedWave()).isEqualTo(1);
        assertThat(session.totalWaves()).isEqualTo(1);
    }

    @Test
    void reportsNothingWhileNoFurtherWaveHasArrived() {
        ScenarioSession session = new ScenarioSession(new OneWave(), SimConfig.defaults());
        session.advance(TICK, NOBODY);
        session.newlyArrivedWave();

        session.advance(TICK, NOBODY);

        assertThat(session.newlyArrivedWave()).isZero();
    }

    @Test
    void reportsNothingOnALevelThatSendsNoWaves() {
        ScenarioSession session = session();

        session.advance(TICK, NOBODY);

        assertThat(session.newlyArrivedWave()).isZero();
        assertThat(session.totalWaves()).isZero();
    }

    @Test
    void reportsNothingWhileTheAvailableActionHasNotChanged() {
        ScenarioSession session = new ScenarioSession(new CommanderAndTrooper(), SimConfig.defaults());
        putTheTrooperDown(session);
        session.advance(TICK, NOBODY);
        session.newlyOfferedAction(0);

        session.advance(TICK, NOBODY);

        assertThat(session.newlyOfferedAction(0)).isNull();
    }

    /**
     * Plugging a pad in is the whole of joining, at any point in a level, and it works the same in
     * every scenario because no scenario is asked about it.
     */
    @Test
    void putsAPlayerWhoHasJustPluggedAPadInOntoTheField() {
        ScenarioSession session = session();
        session.advance(TICK, NOBODY);

        session.seat(1);

        assertThat(commanderSlots(session)).containsExactlyInAnyOrder(0, 1);
    }

    /** A kicked cable comes back as a reconnection, and a reconnection is not a second commander. */
    @Test
    void leavesAPlayerWhoIsAlreadyOnTheFieldAlone() {
        ScenarioSession session = session();
        session.seat(1);

        session.seat(1);

        assertThat(commanderSlots(session)).containsExactlyInAnyOrder(0, 1);
    }

    /** A scenario spawns one commander and knows nothing about pads, so the harness puts the rest back. */
    @Test
    void putsEverySeatedPlayerBackWhenTheScenarioRestarts() {
        ScenarioSession session = session();
        session.seat(1);
        session.seat(2);

        session.apply(HarnessCommand.RESTART, NOBODY);

        assertThat(commanderSlots(session)).containsExactlyInAnyOrder(0, 1, 2);
    }

    private static List<Integer> commanderSlots(ScenarioSession session) {
        List<Integer> slots = new ArrayList<>();
        for (Entity commander : session.simulation()
                .engine()
                .getEntitiesFor(Family.all(Commander.class).get())) {
            slots.add(commander.getComponent(Commander.class).slot);
        }
        return slots;
    }

    /** One player asking for something and nobody else. */
    private static PlayerInputs only(CommanderInput input) {
        return new PlayerInputs().set(0, input);
    }

    private static void putTheTrooperDown(ScenarioSession session) {
        session.simulation()
                .engine()
                .getEntitiesFor(Family.all(Trooper.class).get())
                .get(0)
                .getComponent(Damage.class)
                .add(SimConfig.defaults().trooper().health(), 0f, false);
    }

    private static @Nullable Order trooperOrder(ScenarioSession session) {
        return session.simulation()
                .engine()
                .getEntitiesFor(Family.all(Trooper.class).get())
                .get(0)
                .getComponent(Trooper.class)
                .order;
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

    /** A commander and one wave due immediately, for the console line that says what has arrived. */
    private static final class OneWave implements Scenario {
        @Override
        public String name() {
            return "One wave";
        }

        @Override
        public String description() {
            return "A commander and one lot of aliens, due on the first tick.";
        }

        @Override
        public void populate(Simulation simulation) {
            simulation.spawnCommander(10f, 10f);
            simulation.waves().add(0f, UnitKind.GRUNT, 2, new TileRect(5, 5, 2, 2));
        }
    }

    /** A commander with one trooper beside him, for testing that an order actually arrives. */
    private static final class CommanderAndTrooper implements Scenario {
        @Override
        public String name() {
            return "One commander and a trooper";
        }

        @Override
        public String description() {
            return "A commander with one man, for checking an order reaches the simulation.";
        }

        @Override
        public void populate(Simulation simulation) {
            simulation.spawnCommander(10f, 10f);
            simulation.spawnTrooper(13f, 10f);
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
            return base.withCommander(base.commander().withSpeed(SPEED));
        }

        @Override
        public void populate(Simulation simulation) {
            simulation.spawnCommander(10f, 10f);
        }
    }
}
