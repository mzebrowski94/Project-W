package pl.mzebrows.pwt.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.def.Order;

/**
 * The opener's order inputs work his crate and give no order; every other seat's orders still work
 * (G0056). A draft's context with a fourth card, so on the keyboard shutting it has a key of its own.
 */
class CrateKeysTest {

    @Test
    void takesTheOpenersOrderAndLeavesEveryOtherSeatsAlone() {
        PlayerInputs inputs = new PlayerInputs().order(0, Order.FORWARD).order(1, Order.DEFENCE);

        CrateBar.Key key = CrateKeys.route(inputs, 1);

        assertThat(key).isEqualTo(CrateBar.Key.CLOSE);
        assertThat(inputs.orderOf(1)).isNull();
        assertThat(inputs.orderOf(0)).isEqualTo(Order.FORWARD);
    }

    @Test
    void highlightsAllFourCardsWithTheKeyboardsOrderKeys() {
        assertThat(CrateKeys.keyboard(Order.FOLLOW_ME)).isEqualTo(CrateBar.Key.CARD_1);
        assertThat(CrateKeys.keyboard(Order.FORWARD)).isEqualTo(CrateBar.Key.CARD_2);
        assertThat(CrateKeys.keyboard(Order.DEFENCE)).isEqualTo(CrateBar.Key.CARD_3);
        assertThat(CrateKeys.keyboard(Order.CANCEL)).isEqualTo(CrateBar.Key.CARD_4);
        assertThat(CrateKeys.keyboard(null)).isNull();
    }

    @Test
    void movesTheHighlightWithTheDpadAndShutsWithDown() {
        assertThat(CrateKeys.pad(Order.FORWARD)).isEqualTo(CrateBar.Key.NEXT);
        assertThat(CrateKeys.pad(Order.CANCEL)).isEqualTo(CrateBar.Key.PREVIOUS);
        assertThat(CrateKeys.pad(Order.DEFENCE)).isEqualTo(CrateBar.Key.CLOSE);
        assertThat(CrateKeys.pad(Order.FOLLOW_ME)).isNull();
    }
}
