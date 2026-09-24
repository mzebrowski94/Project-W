package pl.mzebrows.pwt.client;

/**
 * The gamepads a {@link PadPolicy} lets this copy of the game read, and only those - so the rest of
 * the client never learns other pads exist. Keeps the positions it chose from one frame to the next, so
 * asking costs no allocation per frame.
 */
public final class ChosenGamepads implements GamepadSource {

    private final GamepadSource every;
    private final PadPolicy policy;
    private int[] chosen = new int[8];
    private int count;

    public ChosenGamepads(GamepadSource every, PadPolicy policy) {
        this.every = every;
        this.policy = policy;
    }

    @Override
    public int count() {
        int plugged = every.count();
        if (chosen.length < plugged) {
            chosen = new int[plugged];
        }
        count = 0;
        for (int pad = 0; pad < plugged; pad++) {
            if (policy.takes(pad)) {
                chosen[count++] = pad;
            }
        }
        return count;
    }

    @Override
    public Gamepad get(int index) {
        if (index < 0 || index >= count) {
            throw new IndexOutOfBoundsException("pad " + index + " of " + count + " this copy reads");
        }
        return every.get(chosen[index]);
    }
}
