package pl.mzebrows.pwt.ui.hud;

import java.util.Arrays;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.def.ActionKind;

/**
 * One machine's top bar and the local seats that share it (G0049, G0056): which seat drives the bar, what
 * that seat's keys mean while it does, whose inputs the bar takes, and the pause a solo player's draft makes.
 *
 * <p><strong>One bar per machine.</strong> A draft or a crate has it, never both, and only the seat that
 * opened it works it: its order inputs choose a card and give no order, its action press takes a crate's
 * card. Every other seat plays on - except that, while the bar is taken, another seat's press on a crate
 * within reach is not sent at all, and a crate another local seat has open anyway is shut.
 *
 * <p>A frame, in order: {@link #route} as soon as the devices are read, {@link #work} once the menus have
 * had them, and {@link #follow} once the readout is fresh. Device state comes in as values (ADR 0011); what
 * it decides goes out through {@link SeatedPlay}.
 */
public final class TopBarSeats {

    /** The keyboard's seat when it has one: the first (glossary, <em>seat</em>). */
    public static final int KEYBOARD = 0;

    /** The seat whose order inputs are the keyboard's number keys, or -1 while player one is on a pad. */
    private int keyboard = KEYBOARD;

    private final DraftBar draftBar = new DraftBar();
    private final CrateBar crateBar = new CrateBar();
    private final DraftPause draftPause = new DraftPause();

    /** Scratch, by seat: this frame's keys for each bar. */
    private final DraftBar.@Nullable Key[] draftKeys;

    private final CrateBar.@Nullable Key[] crateKeys;

    /** Whether each seat's action was held last frame, so a take is its press rather than its hold. */
    private final boolean[] wasActing;

    /** Scratch, by seat: the slot each plays, refreshed where it is read. */
    private final int[] slotOfSeat;

    /** @param seats how many local seats the machine has */
    public TopBarSeats(int seats) {
        draftKeys = new DraftBar.Key[seats];
        crateKeys = new CrateBar.Key[seats];
        wasActing = new boolean[seats];
        slotOfSeat = new int[seats];
    }

    /**
     * Which seat is the keyboard's - {@link #KEYBOARD}, or -1 while player one is on a pad and every seat is a pad's.
     * Its order keys choose cards by number; a pad's d-pad moves the highlight.
     */
    public void keyboardIn(int seat) {
        keyboard = seat;
    }

    /** The draft on show, for the client to draw. */
    public DraftBar draft() {
        return draftBar;
    }

    /** The crate on show, for the client to draw. */
    public CrateBar crate() {
        return crateBar;
    }

    /**
     * This frame's input, as the devices gave it, turned into what the bars and the world are given: the
     * drafting seat's order becomes a card key and gives no order ({@link DraftKeys}), the crating seat's
     * order and action press work the crate ({@link CrateKeys}), and while a menu is open every seat holds
     * still, keeping only its aim.
     *
     * @param players this frame's input, by seat; changed in place
     * @param draftPressed which seats pressed their draft key - the keyboard's, or a pad's
     * @param crateClosePressed whether the keyboard's crate key went down: a pad shuts its crate with an order
     * @param menu whether a menu is open on this machine
     */
    public void route(PlayerInputs players, boolean[] draftPressed, boolean crateClosePressed, boolean menu) {
        int seats = Math.min(draftKeys.length, players.capacity());
        Arrays.fill(draftKeys, null);
        Arrays.fill(crateKeys, null);
        for (int seat = 0; seat < seats; seat++) {
            if (draftPressed[seat]) {
                draftKeys[seat] = DraftBar.Key.DRAFT;
            }
        }
        int drafting = draftBar.seat();
        if (drafting >= 0 && drafting < seats) {
            DraftBar.Key key = DraftKeys.route(players, drafting, drafting == keyboard);
            if (draftKeys[drafting] == null) {
                draftKeys[drafting] = key;
            }
        }
        int crating = crateBar.seat();
        if (crating >= 0 && crating < seats) {
            crateKeys[crating] = CrateKeys.route(players, crating, crating == keyboard);
            if (crating == keyboard && crateClosePressed) {
                crateKeys[crating] = CrateBar.Key.CLOSE;
            }
            if (players.get(crating).acting() && !wasActing[crating]) {
                crateKeys[crating] = CrateBar.Key.TAKE;
            }
        }
        for (int seat = 0; seat < seats; seat++) {
            wasActing[seat] = players.get(seat).acting();
        }
        if (menu) {
            Arrays.fill(draftKeys, null);
            Arrays.fill(crateKeys, null);
            for (int seat = 0; seat < seats; seat++) {
                CommanderInput was = players.get(seat);
                players.set(
                                seat,
                                new CommanderInput(
                                        0f,
                                        0f,
                                        was.aimX(),
                                        was.aimY(),
                                        false,
                                        false,
                                        false,
                                        false,
                                        false,
                                        was.aimRelative()))
                        .order(seat, null);
            }
        }
    }

    /**
     * Works the bars with this frame's keys and sends what they hand back: a card taken is a pick for its
     * seat, a crate's take or shut is that seat's. While the bar is taken, another seat's press on a crate in
     * reach is dropped from {@code players}. Then the solo pause: opening the bar pauses a game with one
     * player in it, closing resumes. A menu closes both bars, sending nothing.
     *
     * @param menu whether a menu is open on this machine now
     */
    public void work(PlayerInputs players, Readout readout, boolean menu, SeatedPlay play) {
        workDraft(readout, menu, play);
        workCrate(players, readout, menu, play);
        boolean solo = playersIn(readout) <= 1;
        switch (draftPause.next(draftBar.open() || crateBar.open(), solo, play.paused(), menu)) {
            case PAUSE -> play.setDraftPaused(true);
            case RESUME -> play.setDraftPaused(false);
            case NONE -> {
                // Nothing to change.
            }
        }
    }

    /** Whether the pause in force is a draft's, on this machine or another, which the paused banner leaves alone. */
    public boolean quietPause(SeatedPlay play) {
        return draftPause.ours() || play.pausedForDraft();
    }

    /**
     * Keeps both bars true to the world once the readout is fresh: a draft gone gives way to his next, a crate
     * the world has shut closes, and the crate bar shows the first local seat with one open - shutting any
     * other local seat's, so a machine holds one at a time.
     */
    public void follow(Readout readout, boolean menu, SeatedPlay play) {
        draftBar.follow(readout);
        for (int seat = 0; seat < slotOfSeat.length; seat++) {
            slotOfSeat[seat] = play.slotOf(seat);
        }
        if (menu) {
            return;
        }
        crateBar.follow(readout, slotOfSeat, draftBar.open());
        int intruder = crateBar.intruder(readout, slotOfSeat, draftBar.open());
        if (intruder >= 0) {
            play.crate(intruder, null);
            crateBar.settle(slotOfSeat[intruder]);
        }
    }

    /** A new world: nothing on show, and nothing sent that it could still answer. */
    public void forget() {
        draftBar.close();
        crateBar.forget();
    }

    private void workDraft(Readout readout, boolean menu, SeatedPlay play) {
        if (menu) {
            draftBar.close();
            return;
        }
        for (int seat = 0; seat < draftKeys.length; seat++) {
            DraftBar.Key key = draftKeys[seat];
            // One bar per machine: while a crate has it, a draft key opens nothing (G0056).
            if (key != null && !crateBar.open() && draftBar.press(seat, play.slotOf(seat), key, readout)) {
                play.pick(seat, draftBar.takenSerial(), draftBar.takenKind());
            }
        }
    }

    private void workCrate(PlayerInputs players, Readout readout, boolean menu, SeatedPlay play) {
        if (menu) {
            // The crate stays open in the world; the bar comes back when the menu goes.
            crateBar.close();
            return;
        }
        if (crateBar.open()) {
            int seat = crateBar.seat();
            CrateBar.Key key = crateKeys[seat];
            if (key != null) {
                switch (crateBar.press(seat, key, readout)) {
                    case TAKE -> play.crate(seat, crateBar.taken());
                    case CLOSE -> play.crate(seat, null);
                    case NONE -> {
                        // A highlight moved, or nothing he may take.
                    }
                }
            }
        }
        boolean taken = crateBar.open() || draftBar.open();
        int seats = Math.min(crateKeys.length, players.capacity());
        for (int seat = 0; seat < seats; seat++) {
            int slot = play.slotOf(seat);
            if (taken
                    && seat != crateBar.seat()
                    && slot >= 0
                    && slot < Players.MAX
                    && readout.player(slot).action == ActionKind.OPEN_CRATE
                    && players.get(seat).acting()) {
                players.set(seat, players.get(seat).acting(false));
            }
        }
    }

    /** Players in the game, here or on another machine, and not away: one is a solo game. */
    private static int playersIn(Readout readout) {
        int count = 0;
        for (int slot = 0; slot < Players.MAX; slot++) {
            if (readout.player(slot).posture != Posture.ABSENT && !readout.player(slot).away) {
                count++;
            }
        }
        return count;
    }
}
