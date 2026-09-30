package pl.mzebrows.pwt.client;

import com.badlogic.gdx.Input;
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
            ControlsConfig.Keyboard keys = controls.keyboard();
            take = Input.Keys.toString(keys.action());
            close = Input.Keys.toString(keys.crateClose());
            cards[0] = Input.Keys.toString(keys.orderFollowMe());
            cards[1] = Input.Keys.toString(keys.orderForward());
            cards[2] = Input.Keys.toString(keys.orderDefence());
            cards[3] = Input.Keys.toString(keys.orderCancel());
        } else {
            take = PadLabels.of(controls.pad().action(), playStation);
            close = PadLabels.of(controls.pad().orderDefence(), playStation);
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
