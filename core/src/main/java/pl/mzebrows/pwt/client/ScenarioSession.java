package pl.mzebrows.pwt.client;

import java.util.Arrays;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.FixedStepClock;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.def.ActionKind;
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
public final class ScenarioSession implements ScenarioPlay {

    private final Scenario scenario;
    private final SimConfig baseConfig;

    private Simulation simulation;
    private FixedStepClock clock;
    private boolean paused;
    private OverlayMode overlayMode = OverlayMode.PANEL;
    private boolean leaving;
    private boolean godMode;
    private boolean cosmeticLead = true;

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
    private final @Nullable Order[] pendingOrder = new Order[Players.MAX];

    /** Reused, so handing a tick its waiting events costs no allocation per tick. */
    private final PlayerInputs forThisTick = new PlayerInputs();

    /**
     * Players seated since the last tick, waiting for one to take their join.
     *
     * <p>Joining is an input event (ADR 0012): the simulation puts a commander on the field on the tick
     * the join arrives, and nothing outside a tick changes the world. Held here for the same reason
     * {@link #pendingOrder} is - a frame may run no tick at all, and an event must not be lost to it.
     */
    private final boolean[] pendingJoin = new boolean[Players.MAX];

    /** Whether god mode has been switched since the last tick, which the next tick carries as an event. */
    private boolean pendingGodModeSwitch;

    /**
     * Which slots have a player in them, so a restart puts everybody back.
     *
     * <p>A scenario spawns one commander and knows nothing about pads; everybody who joined by
     * plugging one in did so after it had finished populating the world. Rebuilding that world would
     * otherwise leave three players watching a game with one commander in it.
     */
    private final boolean[] seated = new boolean[Players.MAX];

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
    @Override
    public void apply(HarnessCommand command, PlayerInputs input) {
        switch (command) {
            case PAUSE -> paused = !paused;
            case STEP -> stepOnce(input);
            case RESTART -> restart();
            case TOGGLE_OVERLAY -> overlayMode = overlayMode.next();
            case TOGGLE_GOD_MODE -> setGodMode(!godMode);
            case TOGGLE_COSMETIC_LEAD -> cosmeticLead = !cosmeticLead;
            case NEXT_LAG -> {
                // Offline there is no link to lag.
            }
            case LEAVE -> leaving = true;
        }
    }

    private final Announcements announcements = new Announcements();

    /** The last squad order the player gave, for the overlay to show. Null until one is given. */
    @Override
    public @Nullable Order lastOrder() {
        return lastOrder;
    }

    /** The action this slot's commander could take right now, if it has changed; see {@link Announcements}. */
    @Override
    public @Nullable ActionKind newlyOfferedAction(int slot) {
        return announcements.newlyOfferedAction(simulation, slot);
    }

    /** The number of the wave that has just arrived, or zero; see {@link Announcements}. */
    @Override
    public int newlyArrivedWave() {
        return announcements.newlyArrivedWave(simulation);
    }

    /** How many waves this level has altogether, for the other half of the line. */
    @Override
    public int totalWaves() {
        return simulation.waves().total();
    }

    /**
     * Puts a player who has just picked up a pad onto the field on the next tick, and remembers that he
     * is there.
     *
     * <p>Idempotent on purpose: the simulation leaves a slot that already has a commander alone, so a
     * pad reconnecting after the cable was kicked does not hand its player a second one.
     */
    @Override
    public void seat(int slot) {
        seated[slot] = true;
        pendingJoin[slot] = true;
    }

    /** Runs however many fixed steps this frame's elapsed time is worth. Does nothing while paused. */
    @Override
    public void advance(float deltaSeconds, PlayerInputs input) {
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
    @Override
    public float renderAlpha() {
        return paused ? 1f : clock.alpha();
    }

    @Override
    public Simulation simulation() {
        return simulation;
    }

    public Scenario scenario() {
        return scenario;
    }

    @Override
    public String planet() {
        return scenario.planet();
    }

    @Override
    public String scenarioName() {
        return scenario.name();
    }

    @Override
    public boolean cosmeticLead() {
        return cosmeticLead;
    }

    /** Offline a seat is its slot (G0028). */
    @Override
    public int slotOf(int seat) {
        return seat >= 0 && seat < PlayerSeats.SEATS ? seat : -1;
    }

    /** Offline there is no connection to speak of. */
    @Override
    public @Nullable String connection() {
        return null;
    }

    /** Nothing held but the world, which goes with this. */
    @Override
    public void close() {}

    @Override
    public boolean paused() {
        return paused;
    }

    @Override
    public void setPaused(boolean on) {
        paused = on;
    }

    @Override
    public OverlayMode overlayMode() {
        return overlayMode;
    }

    /** Whether the commander is currently unkillable. Survives a restart, because a restart is not a fix. */
    public boolean godMode() {
        return godMode;
    }

    /** True once the player has asked to go back to the menu. One-way. */
    @Override
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
    private void remember(PlayerInputs input) {
        for (int slot = 0; slot < Players.MAX; slot++) {
            Order given = input.orderOf(slot);
            if (given != null) {
                lastOrder = given;
                pendingOrder[slot] = given;
            }
        }
    }

    /**
     * This tick's held input, and every event waiting for a tick to take it - an order, a join, a god
     * mode switch - each spent exactly once.
     *
     * <p>Spent rather than repeated: several ticks can run in one frame, and one press has to mean
     * one order or a single tap would recruit every trooper that walked into earshot during the
     * catch-up.
     */
    private PlayerInputs forThisTick(PlayerInputs input) {
        forThisTick.clear();
        for (int slot = 0; slot < Players.MAX; slot++) {
            forThisTick.set(slot, input.get(slot)).order(slot, pendingOrder[slot]);
            pendingOrder[slot] = null;
            if (pendingJoin[slot]) {
                forThisTick.join(slot);
                pendingJoin[slot] = false;
            }
        }
        if (pendingGodModeSwitch) {
            forThisTick.switchInvulnerability(godMode);
            pendingGodModeSwitch = false;
        }
        return forThisTick;
    }

    /** Single-stepping only makes sense while paused; otherwise the clock is already advancing. */
    private void stepOnce(PlayerInputs input) {
        remember(input);
        if (paused) {
            simulation.step(forThisTick(input));
        }
    }

    private void restart() {
        lastOrder = null;
        Arrays.fill(pendingOrder, null);
        simulation = newWorld();
        clock = new FixedStepClock(simulation.config());
        paused = false;
        for (int slot = 0; slot < Players.MAX; slot++) {
            if (seated[slot]) {
                seat(slot);
            }
        }
        // Carries god mode into the new world; restarting to try something again should not silently
        // undo it. The new world starts with it off, so it is switched on again by the first tick.
        pendingGodModeSwitch = godMode;
    }

    private void setGodMode(boolean enabled) {
        godMode = enabled;
        pendingGodModeSwitch = true;
    }

    private Simulation newWorld() {
        return Scenarios.world(scenario, baseConfig);
    }
}
