package pl.mzebrows.pwt.ui.hud;

import java.util.ArrayList;
import java.util.List;

/**
 * Makes a line fit its slot. The font is monospace, so a slot is a number of columns and every fit is
 * arithmetic - testable without a display.
 *
 * <p>A line that does not fit is cut and ends in {@value #ELLIPSIS} (ASCII: a pixel font may lack the
 * ellipsis glyph). A template with an argument shortens the <b>argument</b> first, so "CURRENT GOAL:"
 * survives and the goal loses its tail rather than the other way round.
 */
public final class TextFit {

    public static final String ELLIPSIS = "...";

    /** Where a template's argument goes. */
    public static final String ARGUMENT = "{0}";

    private TextFit() {}

    /** How many characters fit in this many pixels. */
    public static int columns(int widthPixels, int advance) {
        return widthPixels <= 0 || advance <= 0 ? 0 : widthPixels / advance;
    }

    /** The text, cut to {@code columns} characters if it is longer. */
    public static String fit(String text, int columns) {
        if (text.length() <= columns) {
            return text;
        }
        if (columns <= ELLIPSIS.length()) {
            return columns <= 0 ? "" : ELLIPSIS.substring(0, columns);
        }
        return text.substring(0, columns - ELLIPSIS.length()) + ELLIPSIS;
    }

    /** The template with its argument filled in, the argument cut first if the whole does not fit. */
    public static String fit(String template, String argument, int columns) {
        String whole = template.replace(ARGUMENT, argument);
        if (whole.length() <= columns || !template.contains(ARGUMENT)) {
            return fit(whole, columns);
        }
        int room = columns - (template.length() - ARGUMENT.length());
        if (room > ELLIPSIS.length()) {
            return template.replace(ARGUMENT, fit(argument, room));
        }
        return fit(whole, columns);
    }

    /**
     * Words laid into at most {@code maxLines} lines of {@code columns} characters, breaking between
     * words; what does not fit is cut, the last line ending in {@value #ELLIPSIS}. A word longer than a
     * line is cut where it must be.
     */
    public static List<String> wrap(String text, int columns, int maxLines) {
        List<String> lines = new ArrayList<>(maxLines);
        String rest = text.trim();
        while (!rest.isEmpty() && lines.size() < maxLines) {
            if (rest.length() <= columns) {
                lines.add(rest);
                rest = "";
                break;
            }
            int cut = rest.lastIndexOf(' ', columns);
            if (cut <= 0) {
                cut = columns;
            }
            lines.add(rest.substring(0, cut).trim());
            rest = rest.substring(cut).trim();
        }
        if (!rest.isEmpty() && !lines.isEmpty()) {
            int last = lines.size() - 1;
            lines.set(last, fit(lines.get(last) + " " + rest, columns));
        }
        return lines;
    }

    /** A count for a slot four characters wide: the number, or {@code 999+} above it. */
    public static String count(int value) {
        return value > 999 ? "999+" : Integer.toString(Math.max(0, value));
    }
}
