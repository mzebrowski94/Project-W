package pl.mzebrows.pwt.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.def.Order;

/**
 * The chooser's order inputs work his draft and give no order; every other seat's orders still work
 * (upgrades S5, D12).
 */
class DraftKeysTest {

    @Test
    void takesTheChoosersOrderAndLeavesEveryOtherSeatsAlone() {
        PlayerInputs inputs = new PlayerInputs().order(0, Order.FORWARD).order(1, Order.DEFENCE);

        DraftBar.Key key = DraftKeys.route(inputs, 1);

        assertThat(key).isEqualTo(DraftBar.Key.CLOSE);
        assertThat(inputs.orderOf(1)).isNull();
        assertThat(inputs.orderOf(0)).isEqualTo(Order.FORWARD);
    }

    @Test
    void choosesACardWithTheKeyboardsOrderKeysAndClosesWithTheFourth() {
        assertThat(DraftKeys.keyboard(Order.FOLLOW_ME)).isEqualTo(DraftBar.Key.CARD_1);
        assertThat(DraftKeys.keyboard(Order.FORWARD)).isEqualTo(DraftBar.Key.CARD_2);
        assertThat(DraftKeys.keyboard(Order.DEFENCE)).isEqualTo(DraftBar.Key.CARD_3);
        assertThat(DraftKeys.keyboard(Order.CANCEL)).isEqualTo(DraftBar.Key.CLOSE);
        assertThat(DraftKeys.keyboard(null)).isNull();
    }

    @Test
    void movesTheHighlightWithTheDpadAndClosesWithDown() {
        assertThat(DraftKeys.pad(Order.FORWARD)).isEqualTo(DraftBar.Key.NEXT);
        assertThat(DraftKeys.pad(Order.CANCEL)).isEqualTo(DraftBar.Key.PREVIOUS);
        assertThat(DraftKeys.pad(Order.DEFENCE)).isEqualTo(DraftBar.Key.CLOSE);
        assertThat(DraftKeys.pad(Order.FOLLOW_ME)).isNull();
    }
}
