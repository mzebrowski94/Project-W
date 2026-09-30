package pl.mzebrows.pwt.ui.hud;

import java.util.Arrays;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.def.PickupKind;

/**
 * The crate in the top bar (G0056): whose it is and which card is highlighted. One per machine - a
 * machine's top bar shows one crate or one draft at a time - and a pure decision, so it is tested without a
 * window; the client draws what it says and sends the take or the shut it hands back.
 *
 * <p><strong>The world opens it, not the bar.</strong> A commander's key opens a crate in the simulation;
 * the bar follows, showing the crate of the first local seat that has one open. A second local seat that
 * gets one open while the bar is taken is {@link #intruder named}, for the client to shut - so a machine
 * never holds two crates open with one bar to show them in.
 *
 * <p><strong>What it sent is remembered until the world answers</strong>: a take or a shut closes the bar at
 * once, but the crate is still open in the world until the next tick - and in a solo game, paused while
 * the bar is up, not until the pause lifts. Until the world shows it shut, the bar does not open on it again.
 */
public final class CrateBar {

    /** What one seat asked of the crate this frame. */
    public enum Key {
        /** Highlight a card by its place - the keyboard's 1-4. */
        CARD_1,
        CARD_2,
        CARD_3,
        CARD_4,
        /** Move the highlight - a pad's d-pad. */
        NEXT,
        PREVIOUS,
        /** Take the highlighted card - the action key. */
        TAKE,
        /** Shut the crate, taking nothing. */
        CLOSE
    }

    /** What a press came to. */
    public enum Outcome {
        NONE,
        /** He took {@link #taken()}. */
        TAKE,
        /** He shut it. */
        CLOSE
    }

    private int seat = -1;
    private int slot = -1;
    private int highlighted;
    private PickupKind taken = PickupKind.MEDKIT;

    /** By slot, a take or a shut sent and not yet seen by the world. */
    private final boolean[] settling = new boolean[Players.MAX];

    public boolean open() {
        return seat >= 0;
    }

    /** The seat driving it, or -1. */
    public int seat() {
        return seat;
    }

    /** Whose crate it is, or -1. */
    public int slot() {
        return slot;
    }

    public int highlighted() {
        return highlighted;
    }

    /** What {@link #press} just took. */
    public PickupKind taken() {
        return taken;
    }

    /**
     * Keeps the bar true to the world, once a frame: a crate the world has shut closes it; with none on
     * show, the first local seat with a crate open takes it - unless a draft has the bar.
     *
     * @param slotOfSeat each local seat's slot, or -1
     * @param draftOpen whether a draft has the top bar
     */
    public void follow(Readout readout, int[] slotOfSeat, boolean draftOpen) {
        for (int s = 0; s < settling.length; s++) {
            if (settling[s] && readout.player(s).crateCount == 0) {
                settling[s] = false;
            }
        }
        if (open()) {
            int count = readout.player(slot).crateCount;
            if (count == 0 || settling[slot]) {
                close();
                return;
            }
            if (highlighted >= count) {
                highlighted = count - 1;
            }
            return;
        }
        if (draftOpen) {
            return;
        }
        for (int s = 0; s < slotOfSeat.length; s++) {
            int of = slotOfSeat[s];
            if (showing(readout, of)) {
                seat = s;
                slot = of;
                highlighted = 0;
                return;
            }
        }
    }

    /**
     * A local seat, other than the bar's own, whose crate is open in the world with no bar to show it, or
     * -1. The client shuts it ({@link #settle}), so one machine holds at most one crate open.
     */
    public int intruder(Readout readout, int[] slotOfSeat, boolean draftOpen) {
        for (int s = 0; s < slotOfSeat.length; s++) {
            if ((draftOpen || open()) && s != seat && showing(readout, slotOfSeat[s])) {
                return s;
            }
        }
        return -1;
    }

    /** One seat's key. Only the bar's own seat works it; a take of a card he would be turned down for is nothing. */
    public Outcome press(int pressingSeat, Key key, Readout readout) {
        if (!open() || pressingSeat != seat) {
            return Outcome.NONE;
        }
        PlayerReadout player = readout.player(slot);
        int count = player.crateCount;
        switch (key) {
            case CARD_1 -> highlight(0, count);
            case CARD_2 -> highlight(1, count);
            case CARD_3 -> highlight(2, count);
            case CARD_4 -> highlight(3, count);
            case NEXT -> highlight(count == 0 ? 0 : (highlighted + 1) % count, count);
            case PREVIOUS -> highlight(count == 0 ? 0 : (highlighted + count - 1) % count, count);
            case TAKE -> {
                if (highlighted < count && !player.crateRefused[highlighted]) {
                    taken = player.crate[highlighted];
                    settle(slot);
                    close();
                    return Outcome.TAKE;
                }
            }
            case CLOSE -> {
                settle(slot);
                close();
                return Outcome.CLOSE;
            }
        }
        return Outcome.NONE;
    }

    /** A take or a shut was sent for this slot's crate: not shown again until the world has shut it. */
    public void settle(int settledSlot) {
        if (settledSlot >= 0 && settledSlot < settling.length) {
            settling[settledSlot] = true;
        }
    }

    /** Stops showing it, sending nothing - a menu over the bar, a new world. */
    public void close() {
        seat = -1;
        slot = -1;
        highlighted = 0;
    }

    /** Forgets what was sent - a new world has no crate anybody opened. */
    public void forget() {
        close();
        Arrays.fill(settling, false);
    }

    private boolean showing(Readout readout, int of) {
        return of >= 0 && of < settling.length && !settling[of] && readout.player(of).crateCount > 0;
    }

    private void highlight(int place, int count) {
        if (place < count) {
            highlighted = place;
        }
    }
}
