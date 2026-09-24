package pl.mzebrows.pwt.client;

import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.def.ActionKind;
import pl.mzebrows.pwt.sim.def.Order;

/**
 * What the scenario screen plays through: offline, a world stepped on this machine ({@link
 * ScenarioSession}); online, this machine's place in a session ({@link OnlineSession}, ADR 0012). The
 * screen reads the devices and draws; everything it decides between lives behind this, where a test can
 * reach it without a window.
 *
 * <p>Input arrives indexed by local <strong>seat</strong> - keyboard first, then pads (G0038). Offline a
 * seat is its slot; online the session hands each seat a slot, and {@link #slotOf(int)} says which.
 */
public interface ScenarioPlay extends AutoCloseable {

    /** A local seat has a player in it now - a pad just picked up. */
    void seat(int seat);

    /** Carries out one harness command. {@code local} is what the players are holding, for a single step. */
    void apply(HarnessCommand command, PlayerInputs local);

    /** One frame: plays however many ticks this frame is worth. */
    void advance(float deltaSeconds, PlayerInputs local);

    /**
     * The world as of the last tick played, or null while an online session has none yet. Replaced by a
     * restart, and online by a heal, so a caller holding on to it compares by identity.
     */
    @Nullable
    Simulation simulation();

    /** What is being played, or null until an online session has been told. */
    @Nullable
    String scenarioName();

    /** How far towards the next tick to draw, in {@code [0, 1]}. */
    float renderAlpha();

    boolean paused();

    boolean overlayVisible();

    /**
     * Whether this machine's own commanders are drawn facing where their players aim right now rather
     * than where they aimed a lead of ticks ago. Drawn only; nothing is fed back (ADR 0012).
     */
    boolean cosmeticLead();

    /** True once the player has asked to go back to the menu. One-way. */
    boolean leaving();

    /** The last squad order a player here gave, for the overlay. Null until one is given. */
    @Nullable
    Order lastOrder();

    /** The action this slot's commander could take right now, if it has changed; see {@link Announcements}. */
    @Nullable
    ActionKind newlyOfferedAction(int slot);

    /** The wave that has just arrived, or zero; see {@link Announcements}. */
    int newlyArrivedWave();

    /** How many waves this level has altogether. */
    int totalWaves();

    /** The slot a local seat plays in, or -1 if it has none. */
    int slotOf(int seat);

    /** Whether a slot is played on this machine. The camera frames only these (G0038). */
    default boolean local(int slot) {
        for (int seat = 0; seat < PlayerSeats.SEATS; seat++) {
            if (slotOf(seat) == slot) {
                return true;
            }
        }
        return false;
    }

    /** A line about the connection for the overlay - or, with no world yet, for the whole screen. Null offline. */
    @Nullable
    String connection();

    /**
     * Why this play cannot go on, once it cannot: the host left, the link dropped, the host refused this
     * build. The screen goes back to the menu and says so. Null while it goes on - always, offline.
     */
    default @Nullable String ended() {
        return null;
    }

    /** How friends join this session - its room code and addresses - while this copy hosts one; null otherwise. */
    default @Nullable String invitation() {
        return null;
    }

    /** Lets go of everything the play holds: a session's links, its sequencer. */
    @Override
    void close();
}
