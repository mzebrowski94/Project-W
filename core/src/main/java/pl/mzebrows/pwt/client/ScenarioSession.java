package pl.mzebrows.pwt.client;

import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.FixedStepClock;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.scenario.Scenario;

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

    /** Runs however many fixed steps this frame's elapsed time is worth. Does nothing while paused. */
    public void advance(float deltaSeconds, CommanderInput input) {
        if (paused) {
            return;
        }
        int steps = clock.stepsFor(deltaSeconds);
        for (int i = 0; i < steps; i++) {
            simulation.step(input);
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

    /** Single-stepping only makes sense while paused; otherwise the clock is already advancing. */
    private void stepOnce(CommanderInput input) {
        if (paused) {
            simulation.step(input);
        }
    }

    private void restart() {
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
        Simulation world = new Simulation(scenario.configure(baseConfig));
        scenario.populate(world);
        world.setCommanderInvulnerable(godMode);
        return world;
    }
}
