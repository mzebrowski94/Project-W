package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.def.ActionKind;
import pl.mzebrows.pwt.sim.def.Order;
import pl.mzebrows.pwt.sim.scenario.Scenario;
import pl.mzebrows.pwt.ui.hud.DispatchKind;
import pl.mzebrows.pwt.ui.hud.DispatchSink;
import pl.mzebrows.pwt.ui.hud.Readout;
import pl.mzebrows.pwt.ui.hud.ReadoutWriter;
import pl.mzebrows.pwt.ui.hud.Reporters;

/**
 * The harness state machine, tested with no window and no GL context. Everything the alpha-phase
 * controls actually <em>do</em> is here rather than in the screen, which is what makes that possible.
 */
class ScenarioSessionTest {

    private static final float TICK = SimConfig.defaults().tickSeconds();

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
    void startsWithTheOverlayOffAndStepsItToItsPanelToExpandedAndBackToOff() {
        ScenarioSession session = session();

        assertThat(session.overlayMode()).isEqualTo(OverlayMode.OFF);
        session.apply(HarnessCommand.TOGGLE_OVERLAY, NOBODY);
        assertThat(session.overlayMode()).isEqualTo(OverlayMode.PANEL);
        session.apply(HarnessCommand.TOGGLE_OVERLAY, NOBODY);
        assertThat(session.overlayMode()).isEqualTo(OverlayMode.EXPANDED);
        session.apply(HarnessCommand.TOGGLE_OVERLAY, NOBODY);
        assertThat(session.overlayMode()).isEqualTo(OverlayMode.OFF);
    }

    @Test
    void pausesAndResumesWhenAskedRatherThanToggling() {
        ScenarioSession session = session();

        session.setPaused(true);
        session.setPaused(true);
        assertThat(session.paused()).isTrue();
        session.setPaused(false);
        assertThat(session.paused()).isFalse();
    }

    @Test
    void letsAnOfflinePlayerRestartAndPausesForHisLostWindow() {
        ScenarioSession session = session();

        assertThat(session.canRestart()).isTrue();
        assertThat(session.online()).isFalse();
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
        session.advance(TICK, NOBODY);

        assertThat(session.godMode()).isTrue();
        assertThat(session.simulation().commanderInvulnerable()).isTrue();
    }

    @Test
    void switchesGodModeBackOff() {
        ScenarioSession session = session();

        session.apply(HarnessCommand.TOGGLE_GOD_MODE, NOBODY);
        session.advance(TICK, NOBODY);
        session.apply(HarnessCommand.TOGGLE_GOD_MODE, NOBODY);
        session.advance(TICK, NOBODY);

        assertThat(session.simulation().commanderInvulnerable()).isFalse();
    }

    /** Restarting to try the same thing again should not quietly turn the safety off. */
    @Test
    void carriesGodModeIntoARestartedWorld() {
        ScenarioSession session = session();
        session.apply(HarnessCommand.TOGGLE_GOD_MODE, NOBODY);

        session.apply(HarnessCommand.RESTART, NOBODY);
        session.advance(TICK, NOBODY);

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

        session.advance(TICK, ordering(Order.FOLLOW_ME));
        session.advance(TICK, ordering(Order.DEFENCE));
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

        session.advance(TICK, ordering(Order.FORWARD));

        assertThat(session.lastOrder()).isEqualTo(Order.FORWARD);
    }

    @Test
    void forgetsTheLastOrderWhenTheScenarioRestarts() {
        ScenarioSession session = session();
        session.advance(TICK, ordering(Order.FOLLOW_ME));

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

        session.advance(TICK * 0.4f, ordering(Order.DEFENCE));
        session.advance(TICK, NOBODY);

        assertThat(trooperOrder(session)).isEqualTo(Order.DEFENCE);
    }

    /** One press is one order, however many ticks the frame after it happens to run. */
    @Test
    void spendsAnOrderOnceEvenWhenAFrameRunsSeveralTicks() {
        ScenarioSession session = new ScenarioSession(new CommanderAndTrooper(), SimConfig.defaults());

        session.advance(TICK * 3f, ordering(Order.DEFENCE));

        assertThat(trooperOrder(session)).isEqualTo(Order.DEFENCE);
    }

    /**
     * Scaffolding for the console prompt: the action is said when it changes and not otherwise. Saying
     * it every frame is the failure this exists to prevent - a line per frame is a flood, not a prompt,
     * and the player would stop reading it.
     */
    @Test
    void promptsAnActionWhenOneComesWithinReach() {
        ScenarioSession session = new ScenarioSession(new CommanderAndTrooper(), SimConfig.defaults());
        Narration narration = new Narration(session);
        session.advance(TICK, NOBODY);
        narration.frame();

        putTheTrooperDown(session);
        session.advance(TICK, NOBODY);
        narration.frame();

        assertThat(narration.readout.player(0).action).isEqualTo(ActionKind.STABILISE);
        assertThat(narration.console).containsExactly("action: " + ActionKind.STABILISE);
    }

    @Test
    void promptsNothingWhileTheAvailableActionHasNotChanged() {
        ScenarioSession session = new ScenarioSession(new CommanderAndTrooper(), SimConfig.defaults());
        Narration narration = new Narration(session);
        narration.frame();
        putTheTrooperDown(session);
        session.advance(TICK, NOBODY);
        narration.frame();

        session.advance(TICK, NOBODY);
        narration.frame();

        assertThat(narration.console).hasSize(1);
    }

    /** What the screen does each frame after the ticks: read the world, then let the reporters compare. */
    private static final class Narration implements DispatchSink {

        final Readout readout = new Readout();
        final List<String> lines = new ArrayList<>();
        final List<String> console = new ArrayList<>();
        private final ScenarioSession session;
        private final ReadoutWriter writer = new ReadoutWriter();
        private final Reporters reporters;

        Narration(ScenarioSession session) {
            this.session = session;
            this.reporters = new Reporters(List.of(new ActionPrompts(slot -> true, console::add)));
        }

        void frame() {
            writer.fill(session.simulation(), "", "", readout);
            reporters.report(readout, this);
        }

        @Override
        public void offer(DispatchKind kind, int slot, int a, int b) {
            lines.add(kind.name());
        }

        @Override
        public void reset() {
            lines.clear();
        }
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
        session.advance(TICK, NOBODY);

        assertThat(commanderSlots(session)).containsExactlyInAnyOrder(0, 1);
    }

    /** A kicked cable comes back as a reconnection, and a reconnection is not a second commander. */
    @Test
    void leavesAPlayerWhoIsAlreadyOnTheFieldAlone() {
        ScenarioSession session = session();
        session.seat(1);
        session.advance(TICK, NOBODY);

        session.seat(1);
        session.advance(TICK, NOBODY);

        assertThat(commanderSlots(session)).containsExactlyInAnyOrder(0, 1);
    }

    /** A scenario spawns one commander and knows nothing about pads, so the harness puts the rest back. */
    @Test
    void putsEverySeatedPlayerBackWhenTheScenarioRestarts() {
        ScenarioSession session = session();
        session.seat(1);
        session.seat(2);
        session.advance(TICK, NOBODY);

        session.apply(HarnessCommand.RESTART, NOBODY);
        session.advance(TICK, NOBODY);

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

    /** Player one giving an order, holding nothing, and nobody else asking for anything. */
    private static PlayerInputs ordering(Order order) {
        return new PlayerInputs().order(0, order);
    }

    private static void putTheTrooperDown(ScenarioSession session) {
        session.simulation()
                .engine()
                .getEntitiesFor(Family.all(Trooper.class).get())
                .get(0)
                .getComponent(Damage.class)
                .add(SimConfig.defaults().trooper().health(), 0, false);
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
