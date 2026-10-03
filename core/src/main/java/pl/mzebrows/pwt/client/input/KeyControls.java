package pl.mzebrows.pwt.client.input;

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
 *
 * <p>An order is not read here: it is an input event rather than held state, and {@link OrderKeys}
 * reads it beside this (ADR 0012).
 */
public final class KeyControls {

    private KeyControls() {}

    /** Samples the keys, with the mouse state and the world point under the pointer supplied. */
    public static CommanderInput read(
            ControlsConfig.Keys bindings, KeyHolds held, boolean firing, boolean striking, float aimX, float aimY) {
        return new CommanderInput(
                axis(
                        held,
                        bindings.moveRight(),
                        bindings.moveRightAlias(),
                        bindings.moveLeft(),
                        bindings.moveLeftAlias()),
                axis(held, bindings.moveUp(), bindings.moveUpAlias(), bindings.moveDown(), bindings.moveDownAlias()),
                aimX,
                aimY,
                firing,
                striking,
                held.held(bindings.grenade()),
                held.held(bindings.action()),
                held.held(bindings.drag()),
                false);
    }

    /** One way for two keys, the other way for two others; both at once cancel. */
    private static float axis(
            KeyHolds held, KeyCode positive, KeyCode positiveAlias, KeyCode negative, KeyCode negativeAlias) {
        float value = 0f;
        if (held.held(positive) || held.held(positiveAlias)) {
            value += 1f;
        }
        if (held.held(negative) || held.held(negativeAlias)) {
            value -= 1f;
        }
        return value;
    }
}
