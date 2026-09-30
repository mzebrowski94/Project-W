package pl.mzebrows.pwt.client;

import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.def.Order;
import pl.mzebrows.pwt.ui.hud.CrateBar;

/**
 * The input context of a seat whose crate is open (G0056): his order inputs work the crate and give no
 * order, the action key takes the highlighted card, and his move, aim, fire, strike and grenade stay live
 * - every other seat plays on untouched. The draft's context ({@link DraftKeys}) with a fourth card.
 *
 * <p>On the keyboard the four order keys highlight cards 1-4, so shutting it has a key of its own ({@code
 * controls.key.crateClose}); on a pad right and left move the highlight and down shuts it, as in a draft.
 */
final class CrateKeys {

    private CrateKeys() {}

    /**
     * Takes this seat's order out of the frame's input and returns what it means to the crate - or null, for
     * no order or one that means nothing there.
     */
    static CrateBar.@Nullable Key route(PlayerInputs inputs, int seat) {
        Order order = inputs.orderOf(seat);
        inputs.order(seat, null);
        return seat == PlayerSeats.KEYBOARD_SLOT ? keyboard(order) : pad(order);
    }

    static CrateBar.@Nullable Key keyboard(@Nullable Order order) {
        if (order == null) {
            return null;
        }
        return switch (order) {
            case FOLLOW_ME -> CrateBar.Key.CARD_1;
            case FORWARD -> CrateBar.Key.CARD_2;
            case DEFENCE -> CrateBar.Key.CARD_3;
            case CANCEL -> CrateBar.Key.CARD_4;
        };
    }

    static CrateBar.@Nullable Key pad(@Nullable Order order) {
        if (order == null) {
            return null;
        }
        return switch (order) {
            case FOLLOW_ME -> null;
            case FORWARD -> CrateBar.Key.NEXT;
            case DEFENCE -> CrateBar.Key.CLOSE;
            case CANCEL -> CrateBar.Key.PREVIOUS;
        };
    }
}
