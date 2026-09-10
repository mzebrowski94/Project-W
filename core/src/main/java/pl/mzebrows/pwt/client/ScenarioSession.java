package pl.mzebrows.pwt.client;

import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.FixedStepClock;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.def.Order;
import pl.mzebrows.pwt.sim.scenario.Scenario;
import pl.mzebrows.pwt.sim.scenario.Scenarios;

/**
 * One scenario being played: the world, the clock that drives it, and the harness state around it.
 *
 * <p>Everything the harness <em>decides</em> lives here, and nothing that draws. That split is
 * deliberate and load-bearing: this class can be exercised in an ordinary unit test with no window
 * and no GL context, and {@link ScenarioScreen} is left thin enough to review by eye.
 *
 * <p>Restarting builds a whole new {@link Simulation} rather than emptying the old one, so a
 * restarted run is identical to a cold start by construction and the component pools go with it.
 * Callers holding a reference to the old world - the renderer does - must notice the swap; compare
 * {@link #simulation()} by identity.
 */
public final class ScenarioSession {

    private final Scenario scenario;
    private final SimConfig baseConfig;

    private Simulation simulation;
    private FixedStepClock clock;
    private boolean paused;
    private boolean overlayVisible = true;
    private boolean leaving;
    private boolean godMode;

    /**
     * The last squad order the player gave, or null if none yet.
     *
     * <p>Client state rather than simulation state, deliberately: the simulation delivers an order to
     * whoever was in earshot and keeps nothing about it having been said, which is right - "what did
     * I last press" is a question about the player, not about the world. Kept here rather than in the
     * screen because it is a decision worth testing, and the screen only draws.
     */
    private @Nullable Order lastOrder;

    /**
     * An order the player has given that no simulation tick has taken yet.
     *
     * <p><strong>This is why orders used to be dropped.</strong> A key is "just pressed" for exactly
     * one <em>frame</em>, and a frame runs zero ticks whenever it is shorter than one - which on a
     * display faster than 60 Hz is most frames. The order went into the input record, the overlay
     * recorded it, and no tick ever saw it: the console said the order had been given and nothing
     * happened, more often than not. Holding it here until a tick spends it makes one press mean
     * exactly one order, at any refresh rate.
     */
    private @Nullable Order pendingOrder;

    public ScenarioSession(Scenario scenario, SimConfig baseConfig) {
        this.scenario = scenario;
        this.baseConfig = baseConfig;
        this.simulation = newWorld();
        this.clock = new FixedStepClock(simulation.config());
    }

    /**
     * Carries out one harness command. {@code input} is only used by {@link HarnessCommand#STEP},
     * which advances the world by a tick and so needs to know what the player is currently holding.
     */
    public void apply(HarnessCommand command, CommanderInput input) {
        switch (command) {
            case PAUSE -> paused = !paused;
            case STEP -> stepOnce(input);
            case RESTART -> restart();
            case TOGGLE_OVERLAY -> overlayVisible = !overlayVisible;
            case TOGGLE_GOD_MODE -> setGodMode(!godMode);
            case LEAVE -> leaving = true;
        }
    }

    /** The last squad order the player gave, for the overlay to show. Null until one is given. */
    public @Nullable Order lastOrder() {
        return lastOrder;
    }

    /** Runs however many fixed steps this frame's elapsed time is worth. Does nothing while paused. */
    public void advance(float deltaSeconds, CommanderInput input) {
        remember(input);
        if (paused) {
            return;
        }
        int steps = clock.stepsFor(deltaSeconds);
        for (int i = 0; i < steps; i++) {
            simulation.step(forThisTick(input));
        }
    }

    /**
     * How far the clock is towards the next tick, for render interpolation. Frozen at the current
     * state while paused, so a paused world is drawn exactly where it is rather than part-way to a
     * tick that is not coming.
     */
    public float renderAlpha() {
        return paused ? 1f : clock.alpha();
    }

    public Simulation simulation() {
        return simulation;
    }

    public Scenario scenario() {
        return scenario;
    }

    public boolean paused() {
        return paused;
    }

    public boolean overlayVisible() {
        return overlayVisible;
    }

    /** Whether the commander is currently unkillable. Survives a restart, because a restart is not a fix. */
    public boolean godMode() {
        return godMode;
    }

    /** True once the player has asked to go back to the menu. One-way. */
    public boolean leaving() {
        return leaving;
    }

    /**
     * Notes an order as given.
     *
     * <p>Recorded even while paused and on a single step, because both are ways the player issues
     * one, and an overlay that disagreed with what they had just pressed would be worse than no
     * readout at all.
     */
    private void remember(CommanderInput input) {
        if (input.order() != null) {
            lastOrder = input.order();
            pendingOrder = input.order();
        }
    }

    /**
     * This tick's input, carrying a waiting order if there is one and spending it.
     *
     * <p>Spent rather than repeated: several ticks can run in one frame, and one press has to mean
     * one order or a single tap would recruit every trooper that walked into earshot during the
     * catch-up.
     */
    private CommanderInput forThisTick(CommanderInput input) {
        Order order = pendingOrder;
        pendingOrder = null;
        return input.ordering(order);
    }

    /** Single-stepping only makes sense while paused; otherwise the clock is already advancing. */
    private void stepOnce(CommanderInput input) {
        remember(input);
        if (paused) {
            simulation.step(forThisTick(input));
        }
    }

    private void restart() {
        lastOrder = null;
        pendingOrder = null;
        simulation = newWorld();
        clock = new FixedStepClock(simulation.config());
        paused = false;
    }

    private void setGodMode(boolean enabled) {
        godMode = enabled;
        simulation.setCommanderInvulnerable(enabled);
    }

    /** Carries god mode into the new world; restarting to try something again should not silently undo it. */
    private Simulation newWorld() {
        Simulation world = Scenarios.world(scenario, baseConfig);
        world.setCommanderInvulnerable(godMode);
        return world;
    }
}
