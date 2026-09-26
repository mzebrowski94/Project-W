package pl.mzebrows.pwt.ui.layout;

/**
 * How tall each bar must be to hold what it holds, derived from the font and the parts rather than
 * configured. A second number for a bar's height would be a key that lies the day the font changes.
 *
 * @param padding UI pixels between a bar's edge and its content
 * @param fontSize the font's native pixel height: one line of text
 * @param leading UI pixels between two lines
 * @param topLines lines of text the top bar holds - mission, goal and the terminal's lines
 * @param panelRows rows of text beside the portrait in a player panel
 * @param portraitSize the portrait's side, in UI pixels
 */
public record HudMetrics(int padding, int fontSize, int leading, int topLines, int panelRows, int portraitSize) {

    public HudMetrics {
        if (padding < 0 || fontSize < 1 || leading < 0 || topLines < 1 || panelRows < 1 || portraitSize < 0) {
            throw new IllegalArgumentException("HUD metrics out of range: " + padding + ", " + fontSize + ", " + leading
                    + ", " + topLines + ", " + panelRows + ", " + portraitSize);
        }
    }

    /** The top bar at its smallest: its lines of text, the gaps between them, and padding round both. */
    public int topMin() {
        return 2 * padding + textHeight(topLines);
    }

    /** The bottom bar at its smallest: whichever is taller of a panel's text and its portrait, padded. */
    public int bottomMin() {
        return 2 * padding + Math.max(portraitSize, textHeight(panelRows));
    }

    /** UI pixels a block of this many lines takes, from the top of the first to the bottom of the last. */
    public int textHeight(int lines) {
        return lines * fontSize + (lines - 1) * leading;
    }
}
