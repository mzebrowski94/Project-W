package pl.mzebrows.pwt.client;

import com.badlogic.gdx.Input;
import java.util.Arrays;
import pl.mzebrows.pwt.ui.hud.PlayerReadout;

/**
 * The keys the seat driving the draft works it with, in its own device's words, for the cards' hints:
 * the key that takes, the one that closes and keeps, and on the keyboard the one for each card. Named
 * again only when the seat changes.
 */
final class DraftHints {

    private final ControlsConfig controls;
    private String take = "";
    private String close = "";
    private final String[] cards = new String[PlayerReadout.CARDS];
    private int seat = -2;
    private boolean playStation;

    DraftHints(ControlsConfig controls) {
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
            take = Input.Keys.toString(keys.draft());
            close = Input.Keys.toString(keys.orderCancel());
            cards[0] = Input.Keys.toString(keys.orderFollowMe());
            cards[1] = Input.Keys.toString(keys.orderForward());
            cards[2] = Input.Keys.toString(keys.orderDefence());
        } else {
            take = PadLabels.of(controls.pad().draft(), playStation);
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
