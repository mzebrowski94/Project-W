package pl.mzebrows.pwt.client;

import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.def.Order;

/**
 * Which key means which squad order.
 *
 * <p><strong>Alpha-phase scaffolding, and named as such.</strong> The command model is settled - an
 * order reaches the troopers near the commander and names a verb, never a target (G0017) - but the
 * input is not. The intended binding is one button and a radial menu, which needs interface work
 * that does not exist. Four keys are what makes the verbs playable in the meantime; a pad puts the
 * same four verbs on the d-pad, which is scaffolding of exactly the same kind.
 *
 * <p>These are game bindings rather than harness ones, so they are in the input map in
 * {@code docs/design/README.md} rather than with pause and step, and they are in
 * {@code controls.properties} rather than in this file. Kept out of {@code InputReader} because
 * which key means what is a decision, and decisions are testable without a GL context; the reader
 * only samples devices.
 *
 * <p>Just-pressed, not held. An order is given once and then stands on whoever heard it; a held key
 * that re-issued it every tick would keep recruiting whatever wandered into range.
 */
public final class OrderKeys {

    private OrderKeys() {}

    /** The order asked for this frame, or null if none was. */
    public static @Nullable Order poll(KeyPresses keys, ControlsConfig.Keyboard bindings) {
        if (keys.justPressed(bindings.orderFollowMe())) {
            return Order.FOLLOW_ME;
        }
        if (keys.justPressed(bindings.orderForward())) {
            return Order.FORWARD;
        }
        if (keys.justPressed(bindings.orderDefence())) {
            return Order.DEFENCE;
        }
        if (keys.justPressed(bindings.orderCancel())) {
            return Order.CANCEL;
        }
        return null;
    }
}
