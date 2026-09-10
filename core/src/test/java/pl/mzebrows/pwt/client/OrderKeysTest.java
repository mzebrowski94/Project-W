package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.gdx.Input;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.def.Order;

/**
 * Which key means which order.
 *
 * <p>Worth a test despite being four ifs, for the reason the whole client boundary exists: this is a
 * decision, and decisions are testable without a GL context. The keys themselves are alpha-phase
 * scaffolding - the real binding is a button and a radial menu - but "which input means what" is the
 * part that will survive that change, and it is the part that would silently collide with another
 * feature claiming the same key.
 */
class OrderKeysTest {

    @Test
    void readsEachOrderOffItsOwnKey() {
        assertThat(OrderKeys.poll(pressing(Input.Keys.NUM_1))).isEqualTo(Order.FOLLOW_ME);
        assertThat(OrderKeys.poll(pressing(Input.Keys.NUM_2))).isEqualTo(Order.MOVE_OUT);
        assertThat(OrderKeys.poll(pressing(Input.Keys.NUM_3))).isEqualTo(Order.STOP);
        assertThat(OrderKeys.poll(pressing(Input.Keys.NUM_4))).isEqualTo(Order.CANCEL);
    }

    @Test
    void asksForNothingWhenNoOrderKeyIsPressed() {
        assertThat(OrderKeys.poll(pressing(Input.Keys.W))).isNull();
    }

    /** Every order the game has must be reachable, or one of them is a constant nobody can use. */
    @Test
    void leavesNoOrderWithoutAKey() {
        Set<Order> bound = Set.of(Order.FOLLOW_ME, Order.MOVE_OUT, Order.STOP, Order.CANCEL);

        assertThat(bound).containsExactlyInAnyOrder(Order.values());
    }

    private static KeyPresses pressing(int... keycodes) {
        return keycode -> {
            for (int pressed : keycodes) {
                if (pressed == keycode) {
                    return true;
                }
            }
            return false;
        };
    }
}
