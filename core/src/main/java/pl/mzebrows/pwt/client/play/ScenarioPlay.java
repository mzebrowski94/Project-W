package pl.mzebrows.pwt.client.play;

import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.client.input.PlayerSeats;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.def.Order;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;
import pl.mzebrows.pwt.ui.hud.SeatedPlay;

/**
 * What the scenario screen plays through: offline, a world stepped on this machine ({@link
 * ScenarioSession}); online, this machine's place in a session ({@link OnlineSession}, ADR 0012). The
 * screen reads the devices and draws; everything it decides between lives behind this, where a test can
 * reach it without a window.
 *
 * <p>Input arrives indexed by local <strong>seat</strong> - keyboard first, then pads (G0038). Offline a
 * seat is its slot; online the session hands each seat a slot, and {@link #slotOf(int)} says which.
 */
public interface ScenarioPlay extends SeatedPlay, AutoCloseable {

    /** A local seat has a player in it now - a pad just picked up. */
    void seat(int seat);

    /**
     * Takes a card from the draft bar for the slot this seat plays: the draft, by serial, and the card's
     * kind, as it was shown (G0049). It counts from the next tick, like an order.
     */
    @Override
    void pick(int seat, int serial, UpgradeKind kind);

    /**
     * The crate this seat's slot has open, from the next tick (G0056): takes a pickup of this kind from it,
     * or - for null - shuts it. Kept while paused, as a solo crate is chosen from paused.
     */
    @Override
    void crate(int seat, @Nullable PickupKind take);

    /**
     * Takes the card at this place on the keyboard seat's oldest draft, from the next tick - the harness's
     * {@code F5}-{@code F7}. The card is read now, as it is shown, and handed on as its kind (G0049).
     */
    default void takeCard(int position) {
        Simulation world = simulation();
        int slot = slotOf(0);
        if (world == null || slot < 0) {
            return;
        }
        int serial = world.upgradeView().oldestDraftOf(slot);
        @Nullable UpgradeKind kind = serial == 0 ? null : world.upgradeView().card(serial, position);
        if (kind != null) {
            pick(0, serial, kind);
        }
    }

    /**
     * Whether this machine hosts a session that has a briefing: leaving a mission by the result's MENU takes every
     * machine back to it instead of ending the session (G0101).
     */
    default boolean briefed() {
        return false;
    }

    /** Who the host is, as a banner or a line names him: the lowest slot he holds or -1, and his peer number (G0104). */
    record Host(int slot, int peer) {}

    /** The host, once he has changed under this machine; null before, and always offline. */
    default @Nullable Host handedOverHost() {
        return null;
    }

    /** The host the first time this is asked after he changed, then null until he changes again: what the banner says once. */
    default @Nullable Host takeHostChange() {
        return null;
    }

    /** Whether this machine's link is down and it is trying to come back (G0105). */
    default boolean reconnecting() {
        return false;
    }

    /** Takes every machine in the session back to its briefing, the session kept. The host's to ask for. */
    default void toBriefing() {}

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

    @Override
    boolean paused();

    /**
     * Asks for the pause to be on or off - not a toggle, because online a pause asked for a moment ago
     * may not have landed yet, and toggling on top of it would undo it. Offline it happens at once.
     */
    void setPaused(boolean on);

    /**
     * The same, for a solo player's draft (G0049): online the pause carries that reason to every machine, so
     * none covers the view with the paused banner. Offline it is simply a pause.
     */
    @Override
    default void setDraftPaused(boolean on) {
        setPaused(on);
    }

    /** Whether the pause in force is for a solo player's draft, asked for on whichever machine. */
    @Override
    default boolean pausedForDraft() {
        return false;
    }

    /** Whether RESTART is this machine's to give: offline, or while it hosts. */
    default boolean canRestart() {
        return true;
    }

    /** Whether other machines share this game - so a lost window or an unplugged pad does not pause it. */
    default boolean online() {
        return false;
    }

    /** How much of the dev overlay is shown; {@code F1} steps through the modes. */
    OverlayMode overlayMode();

    /** Where the scenario is set, for the top bar; empty when it says nothing. */
    default String planet() {
        return "";
    }

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

    /** The slot a local seat plays in, or -1 if it has none. */
    @Override
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
