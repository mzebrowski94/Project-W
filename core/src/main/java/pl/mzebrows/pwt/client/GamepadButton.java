package pl.mzebrows.pwt.client;

/**
 * A button on a gamepad, named by where it is rather than by what is printed on it.
 *
 * <p><strong>Position, not letter.</strong> The bottom face button is A on an Xbox pad, Cross on a
 * PlayStation one and B on a Nintendo one, and every game that binds "A" ends up with two binding
 * tables and a bug in one of them. Binding {@link #SOUTH} instead means one table, and what the
 * player is told to press is a label chosen from {@link PadLabels} when the pad says what it is.
 *
 * <p>{@link #NONE} is how this codebase says <em>not bound</em>, the same way a zero chance says not
 * for me: an action with no button on the pad carries {@code NONE} rather than being absent from the
 * bindings, so every action is in the file and the file says which ones do nothing.
 */
public enum GamepadButton {
    NONE,
    /** Bottom face button: A, Cross. */
    SOUTH,
    /** Right face button: B, Circle. */
    EAST,
    /** Left face button: X, Square. */
    WEST,
    /** Top face button: Y, Triangle. */
    NORTH,
    /** Left bumper: LB, L1. */
    L1,
    /** Right bumper: RB, R1. */
    R1,
    /** Left stick pressed in. */
    L3,
    /** Right stick pressed in. */
    R3,
    START,
    SELECT,
    DPAD_UP,
    DPAD_DOWN,
    DPAD_LEFT,
    DPAD_RIGHT
}
