package pl.mzebrows.pwt.client;

import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.def.Order;
import pl.mzebrows.pwt.ui.hud.DraftBar;

/**
 * The input context of a seat whose draft is open (G0049): his order inputs work the draft and
 * give no order, while his move, aim, fire, strike, grenade and action stay live (G0049) and every other
 * seat plays on untouched.
 *
 * <p>Which order input means what goes by the order's place, not by the key: on the keyboard the first
 * three choose card 1-3 and the fourth closes (the keys {@code 1}-{@code 4}); on a pad right and left
 * move the highlight and down closes ({@code controls.pad.orderForward}, {@code orderCancel} and {@code
 * orderDefence}), and up does nothing.
 */
final class DraftKeys {

    private DraftKeys() {}

    /**
     * Takes this seat's order out of the frame's input and returns what it means to the draft - or null,
     * for no order or one that means nothing there.
     */
    static DraftBar.@Nullable Key route(PlayerInputs inputs, int seat) {
        Order order = inputs.orderOf(seat);
        inputs.order(seat, null);
        return seat == PlayerSeats.KEYBOARD_SLOT ? keyboard(order) : pad(order);
    }

    static DraftBar.@Nullable Key keyboard(@Nullable Order order) {
        if (order == null) {
            return null;
        }
        return switch (order) {
            case FOLLOW_ME -> DraftBar.Key.CARD_1;
            case FORWARD -> DraftBar.Key.CARD_2;
            case DEFENCE -> DraftBar.Key.CARD_3;
            case CANCEL -> DraftBar.Key.CLOSE;
        };
    }

    static DraftBar.@Nullable Key pad(@Nullable Order order) {
        if (order == null) {
            return null;
        }
        return switch (order) {
            case FOLLOW_ME -> null;
            case FORWARD -> DraftBar.Key.NEXT;
            case DEFENCE -> DraftBar.Key.CLOSE;
            case CANCEL -> DraftBar.Key.PREVIOUS;
        };
    }
}
