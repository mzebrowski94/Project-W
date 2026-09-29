package pl.mzebrows.pwt.ui.hud;

import pl.mzebrows.pwt.sim.upgrade.UpgradeKind;

/**
 * The draft in the top bar (D18, {@code UI:hud/top/draft}): whose it is, which draft, and which card is
 * highlighted. One per machine - a machine's top bar shows one draft at a time - and a pure decision, so
 * it is tested without a window; the client draws what it says and sends the pick it hands back.
 *
 * <p>Only the seat that opened it drives it. Another local seat pressing its draft key while the bar is
 * taken changes nothing: the bar already says whose draft it is.
 */
public final class DraftBar {

    /** What one seat asked of the draft this frame. */
    public enum Key {
        /** Open his oldest draft, or take the highlighted card if it is open already. */
        DRAFT,
        /** Highlight a card by its place - the keyboard's 1-3. */
        CARD_1,
        CARD_2,
        CARD_3,
        /** Move the highlight - a pad's d-pad. */
        NEXT,
        PREVIOUS,
        /** Close the draft and keep it. */
        CLOSE
    }

    private int seat = -1;
    private int slot = -1;
    private int serial;
    private int highlighted;

    private int takenSerial;
    private UpgradeKind takenKind = UpgradeKind.LARGER_ARMY;

    public boolean open() {
        return seat >= 0;
    }

    /** The seat driving it, or -1. */
    public int seat() {
        return seat;
    }

    /** Whose draft it is, or -1. */
    public int slot() {
        return slot;
    }

    /** Which of his drafts is on show. */
    public int serial() {
        return serial;
    }

    public int highlighted() {
        return highlighted;
    }

    /** The draft of the card {@link #press} just took. */
    public int takenSerial() {
        return takenSerial;
    }

    /** The kind of the card {@link #press} just took. */
    public UpgradeKind takenKind() {
        return takenKind;
    }

    /**
     * One seat's key. Returns true when it took a card, which {@link #takenSerial()} and {@link
     * #takenKind()} then name; taking one closes the bar.
     *
     * @param slot the slot that seat plays
     */
    public boolean press(int pressingSeat, int slot, Key key, Readout readout) {
        if (!open()) {
            if (key == Key.DRAFT && slot >= 0 && readout.player(slot).drafts > 0) {
                this.seat = pressingSeat;
                this.slot = slot;
                this.serial = readout.player(slot).draftSerial;
                this.highlighted = 0;
            }
            return false;
        }
        if (pressingSeat != seat) {
            return false;
        }
        PlayerReadout player = readout.player(this.slot);
        int cards = player.draftSerial == serial ? player.cardCount : 0;
        switch (key) {
            case DRAFT -> {
                if (highlighted < cards) {
                    takenSerial = serial;
                    takenKind = player.cards[highlighted];
                    close();
                    return true;
                }
            }
            case CARD_1 -> highlight(0, cards);
            case CARD_2 -> highlight(1, cards);
            case CARD_3 -> highlight(2, cards);
            case NEXT -> highlight(cards == 0 ? 0 : (highlighted + 1) % cards, cards);
            case PREVIOUS -> highlight(cards == 0 ? 0 : (highlighted + cards - 1) % cards, cards);
            case CLOSE -> close();
        }
        return false;
    }

    /**
     * Keeps the bar true to the world, once a frame: a draft that has gone - passed on, or re-dealt to
     * nothing - gives way to his next oldest, and a man with none left has nothing to show.
     */
    public void follow(Readout readout) {
        if (!open()) {
            return;
        }
        PlayerReadout player = readout.player(slot);
        if (player.drafts == 0) {
            close();
            return;
        }
        if (player.draftSerial != serial) {
            serial = player.draftSerial;
            highlighted = 0;
        }
        if (highlighted >= player.cardCount) {
            highlighted = Math.max(0, player.cardCount - 1);
        }
    }

    /** Closes it, keeping the draft. */
    public void close() {
        seat = -1;
        slot = -1;
        serial = 0;
        highlighted = 0;
    }

    private void highlight(int place, int cards) {
        if (place < cards) {
            highlighted = place;
        }
    }
}
