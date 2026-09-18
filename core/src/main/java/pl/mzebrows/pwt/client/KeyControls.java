package pl.mzebrows.pwt.client;

import pl.mzebrows.pwt.sim.CommanderInput;

/**
 * What the keyboard is asking for, as one tick of {@link CommanderInput}.
 *
 * <p>Player one's, and in the alpha nobody else's (1.a). The mouse half - which buttons are down and
 * where the pointer is in the world - is handed in rather than read here, because unprojecting a
 * pointer needs the viewport and the viewport needs a window; keeping that on the other side of the
 * call is what leaves this class testable without one.
 *
 * <p>Movement and aim are read independently (G0002): the mouse decides where the commander points
 * and the keys decide where he walks, and neither is derived from the other.
 *
 * <p>The grenade, action and drag keys are read as held rather than as pressed, and so are the mouse
 * buttons. How often a throw may happen is a {@code Thrower} cooldown and how long a stabilise takes
 * is a number on a casualty; neither is a question about key repeat.
 */
public final class KeyControls {

    private KeyControls() {}

    /** Samples the keys, with the mouse state and the world point under the pointer supplied. */
    public static CommanderInput read(
            ControlsConfig.Keyboard bindings,
            KeyHolds held,
            KeyPresses pressed,
            boolean firing,
            boolean striking,
            float aimX,
            float aimY) {
        return new CommanderInput(
                axis(held, bindings.moveX()),
                axis(held, bindings.moveY()),
                aimX,
                aimY,
                firing,
                striking,
                held.held(bindings.grenade()),
                held.held(bindings.action()),
                held.held(bindings.drag()),
                OrderKeys.poll(pressed, bindings),
                false);
    }

    private static float axis(KeyHolds held, ControlsConfig.KeyAxis keys) {
        float value = 0f;
        if (held.held(keys.positive()) || held.held(keys.positiveAlias())) {
            value += 1f;
        }
        if (held.held(keys.negative()) || held.held(keys.negativeAlias())) {
            value -= 1f;
        }
        return value;
    }
}
