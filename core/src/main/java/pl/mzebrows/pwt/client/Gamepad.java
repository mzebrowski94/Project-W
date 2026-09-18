package pl.mzebrows.pwt.client;

/**
 * One gamepad, as the game sees it. No type from any controller library crosses this interface.
 *
 * <p>That is the whole point of it: swapping gdx-controllers for input4j, for GLFW's own gamepad
 * API, or for whatever comes next is writing one new {@link GamepadSource} and one new class
 * implementing this - and changing nothing that decides what a button means, which is where the
 * bugs live.
 */
public interface Gamepad {

    /**
     * Something that identifies this pad for as long as it is plugged in, and identifies it again
     * when it is plugged back in.
     *
     * <p>What a player's slot is reclaimed by: unplugging a pad leaves its commander standing on the
     * field, and plugging it back in has to put the same player behind him rather than seating a
     * fifth one.
     */
    String id();

    /** What the pad calls itself, for the log and for choosing which labels to show the player. */
    String name();

    /** Where this control is right now. Sticks in {@code [-1, 1]} with y up; triggers in {@code [0, 1]}. */
    float axis(GamepadAxis axis);

    /** Whether this button is down right now. {@link GamepadButton#NONE} is never down. */
    boolean pressed(GamepadButton button);
}
