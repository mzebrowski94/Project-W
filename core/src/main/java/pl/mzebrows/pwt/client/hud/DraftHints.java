package pl.mzebrows.pwt.client.hud;

import java.util.Arrays;
import pl.mzebrows.pwt.client.input.ControlsConfig;
import pl.mzebrows.pwt.client.input.PadLabels;
import pl.mzebrows.pwt.ui.hud.PlayerReadout;

/**
 * The keys the seat driving the draft works it with, in its own device's words, for the cards' hints:
 * the key that takes, the one that closes and keeps, and on the keyboard the one for each card. Named
 * again only when the seat changes.
 */
public final class DraftHints {

    private final ControlsConfig controls;
    private String take = "";
    private String close = "";
    private final String[] cards = new String[PlayerReadout.CARDS];
    private int seat = -2;
    private boolean keyboard;
    private boolean playStation;

    public DraftHints(ControlsConfig controls) {
        this.controls = controls;
        Arrays.fill(cards, "");
    }

    /**
     * Names the keys of this seat - the keyboard's, or a pad's that is a PlayStation pad or not.
     *
     * @param keyboard whether the seat is the keyboard and mouse's, which seat zero is unless player one is on a pad
     */
    public void follow(int seat, boolean keyboard, boolean playStation) {
        if (seat == this.seat && keyboard == this.keyboard && playStation == this.playStation) {
            return;
        }
        this.seat = seat;
        this.keyboard = keyboard;
        this.playStation = playStation;
        Arrays.fill(cards, "");
        if (keyboard) {
            ControlsConfig.Keys keys = controls.key();
            take = keys.draft().name();
            close = keys.orderCancel().name();
            cards[0] = keys.orderFollowMe().name();
            cards[1] = keys.orderForward().name();
            cards[2] = keys.orderDefence().name();
        } else {
            take = PadLabels.of(controls.pad().draft(), controls.pad().draftTrigger(), playStation);
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
