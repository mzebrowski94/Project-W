package pl.mzebrows.pwt.client.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Which of the plugged-in gamepads this copy of the game reads.
 *
 * <p>Two copies on one PC both see every pad - SDL reads them whether or not the window has focus - so
 * without this each pad would drive a player in both. Copy 0 takes every pad and any other copy none,
 * unless {@code --pads} says otherwise: {@code all}, {@code none}, or pad numbers such as {@code 0,2}.
 * Keyboard and mouse need no such rule; they already follow the window with focus.
 *
 * <p>Pad numbers are the order the pads are listed in, which is plug-in order and shifts when one is
 * unplugged - good enough for a developer at one desk, which is all this is for.
 */
public final class PadPolicy {

    public static final PadPolicy ALL = new PadPolicy(true, List.of(), "all");
    public static final PadPolicy NONE = new PadPolicy(false, List.of(), "none");

    private final boolean all;
    private final List<Integer> numbers;
    private final String text;

    private PadPolicy(boolean all, List<Integer> numbers, String text) {
        this.all = all;
        this.numbers = List.copyOf(numbers);
        this.text = text;
    }

    /** What copy {@code instance} reads when nothing says otherwise. */
    public static PadPolicy defaultFor(int instance) {
        return instance == 0 ? ALL : NONE;
    }

    /**
     * {@code all}, {@code none}, or a comma-separated list of pad numbers.
     *
     * @throws IllegalArgumentException for anything else
     */
    public static PadPolicy parse(String text) {
        String trimmed = text.trim().toLowerCase(Locale.ROOT);
        if (trimmed.equals("all")) {
            return ALL;
        }
        if (trimmed.equals("none")) {
            return NONE;
        }
        List<Integer> numbers = new ArrayList<>();
        for (String part : trimmed.split(",", -1)) {
            try {
                int number = Integer.parseInt(part.trim());
                if (number < 0) {
                    throw new IllegalArgumentException("a pad number is not negative: " + part);
                }
                numbers.add(number);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("pads are all, none or numbers like 0,2 - not " + text, e);
            }
        }
        return new PadPolicy(false, numbers, trimmed);
    }

    /** Whether this copy reads the pad listed at this position. */
    public boolean takes(int padNumber) {
        return all || numbers.contains(padNumber);
    }

    @Override
    public String toString() {
        return text;
    }
}
