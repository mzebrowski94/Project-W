package pl.mzebrows.pwt.client;

import java.util.Arrays;
import pl.mzebrows.pwt.ui.hud.PlayerReadout;

/**
 * The keys the seat whose crate is open works it with, in its own device's words, for the cards' hints
 * (G0056): the key that takes, the one that shuts, and on the keyboard the one for each card. Named again
 * only when the seat changes. {@link DraftHints}' twin: the draft takes on its own key, a crate on the
 * action key that opened it.
 */
final class CrateHints {

    private final ControlsConfig controls;
    private String take = "";
    private String close = "";
    private final String[] cards = new String[PlayerReadout.CRATE_CARDS];
    private int seat = -2;
    private boolean playStation;

    CrateHints(ControlsConfig controls) {
        this.controls = controls;
        Arrays.fill(cards, "");
    }

    /** Names the keys of this seat, whose pad - if it has one - is a PlayStation pad or not. */
    void follow(int seat, boolean playStation) {
        if (seat == this.seat && playStation == this.playStation) {
            return;
        }
        this.seat = seat;
        this.playStation = playStation;
        Arrays.fill(cards, "");
        if (seat == PlayerSeats.KEYBOARD_SLOT) {
            ControlsConfig.Keys keys = controls.key();
            take = keys.action().name();
            close = keys.crateClose().name();
            cards[0] = keys.orderFollowMe().name();
            cards[1] = keys.orderForward().name();
            cards[2] = keys.orderDefence().name();
            cards[3] = keys.orderCancel().name();
        } else {
            take = PadLabels.of(controls.pad().action(), controls.pad().actionTrigger(), playStation);
            close = PadLabels.of(controls.pad().orderDefence(), controls.pad().orderDefenceTrigger(), playStation);
        }
    }

    String take() {
        return take;
    }

    String close() {
        return close;
    }

    /** The key that highlights card {@code index}, or empty where a card is reached by moving to it. */
    String card(int index) {
        return index < cards.length ? cards[index] : "";
    }
}
