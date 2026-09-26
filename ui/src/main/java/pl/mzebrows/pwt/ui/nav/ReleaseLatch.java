package pl.mzebrows.pwt.ui.nav;

import java.util.function.IntPredicate;

/**
 * <b>A press that changes context is spent.</b> The click on RESUME is also fire; {@code SOUTH} on
 * RESUME is also act; the {@code Enter} that starts a level would reach its first tick. So on every
 * context change - a menu opened or closed, a level started - whatever is held at that moment is latched,
 * and the game ignores it until it has been let go once. A fresh press after that goes through at once.
 *
 * <p>Inputs are numbered by the client: keys, mouse buttons and each seat's pad buttons in one range.
 */
public final class ReleaseLatch {

    private final boolean[] latched;

    public ReleaseLatch(int inputs) {
        latched = new boolean[inputs];
    }

    /** Latches everything {@code held} says is down right now. */
    public void arm(IntPredicate held) {
        for (int input = 0; input < latched.length; input++) {
            latched[input] = held.test(input);
        }
    }

    /**
     * What the game sees of an input: held and not spent. Letting go of a latched input clears it, so
     * the next press is the player's own.
     */
    public boolean filter(int input, boolean held) {
        if (input < 0 || input >= latched.length) {
            return held;
        }
        if (!held) {
            latched[input] = false;
            return false;
        }
        return !latched[input];
    }

    public boolean latched(int input) {
        return input >= 0 && input < latched.length && latched[input];
    }
}
