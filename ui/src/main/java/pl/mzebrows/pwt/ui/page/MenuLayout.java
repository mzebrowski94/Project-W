package pl.mzebrows.pwt.ui.page;

import pl.mzebrows.pwt.ui.layout.Rect;

/**
 * Where a page's panel and its lines go on the UI canvas, and which entry a pointer is over - worked out
 * here so hovering and clicking are testable without a display.
 *
 * <p>A page is a column of lines in one panel: a head (title, body), the entries, and a foot (anything
 * the page lists under its entries, then the hint). In a level the panel is <b>docked</b> to the left
 * edge of the view, because the camera centres on the player's man and a centred menu covers exactly
 * what he needs to see (plan 6.5); elsewhere it is centred.
 *
 * @param padding UI pixels between the panel's frame and its lines
 * @param pitch UI pixels from one line's top to the next
 * @param advance the font's advance
 * @param fontSize a line of text's height, capitals and descenders
 */
public record MenuLayout(int padding, int pitch, int advance, int fontSize) {

    public enum Anchor {
        DOCKED_LEFT,
        /** Docked to the right of the view. What sits beside a page docked left goes through {@link #beside} instead. */
        DOCKED_RIGHT,
        CENTRED
    }

    /**
     * The panel for this many lines, {@code columns} characters wide whatever it holds - a page never
     * changes width as its selection, its description or the device's hint changes. Only a view too
     * narrow for it makes it narrower.
     */
    public Rect panel(Rect view, Anchor anchor, int lines, int columns) {
        int wanted = width(columns);
        int height = Math.min(view.height(), lines * pitch + 3 * padding);
        if (anchor == Anchor.DOCKED_LEFT) {
            int width = Math.min(view.width() - 4 * padding, wanted);
            int top = view.top() - 2 * padding;
            return new Rect(view.x() + 2 * padding, top - height, width, height);
        }
        if (anchor == Anchor.DOCKED_RIGHT) {
            int width = Math.min(view.width() - 4 * padding, wanted);
            int top = view.top() - 2 * padding;
            return new Rect(view.right() - 2 * padding - width, top - height, width, height);
        }
        int width = Math.min(view.width() - 4 * padding, wanted);
        return new Rect(view.x() + (view.width() - width) / 2, view.y() + (view.height() - height) / 2, width, height);
    }

    /**
     * A panel shown beside a page docked left - the pause menu's list of upgrades: docked right, as wide
     * as its own lines ask and never wider than the room right of {@code page}, so the two never overlap.
     * Where that room is narrower than {@code leastColumns} it goes under the page instead. Either way its
     * height is what its lines ask, cut to the room there is; what does not fit is the caller's to scroll.
     *
     * @param page where the page it sits beside rests - not where its arrival has it this frame
     * @param lines how many lines it would like
     * @param columns how many characters its widest line is
     * @param leastColumns the narrowest it may be beside the page
     */
    public Rect beside(Rect view, Rect page, int lines, int columns, int leastColumns) {
        int wantedHeight = lines * pitch + 3 * padding;
        int room = view.right() - 2 * padding - (page.right() + 2 * padding);
        if (room >= width(leastColumns)) {
            int width = Math.min(room, width(columns));
            int top = view.top() - 2 * padding;
            int height = Math.min(wantedHeight, view.height() - 4 * padding);
            return new Rect(view.right() - 2 * padding - width, top - height, width, height);
        }
        int width = Math.min(view.width() - 4 * padding, width(columns));
        int top = page.y() - 2 * padding;
        int height = Math.max(0, Math.min(wantedHeight, top - (view.y() + 2 * padding)));
        return new Rect(page.x(), top - height, width, height);
    }

    /** How many whole lines a panel this tall has room for - the inverse of the height {@link #panel} gives. */
    public int linesIn(Rect panel) {
        return Math.max(0, (panel.height() - 3 * padding) / pitch);
    }

    /** How wide a panel of this many columns is: its text, and twice the padding each side. */
    public int width(int columns) {
        return columns * advance + 4 * padding;
    }

    /** The top of a line in a panel - where its capitals start. */
    public int lineTop(Rect panel, int line) {
        return panel.top() - 2 * padding - line * pitch;
    }

    /** Where a line's text starts. */
    public int lineX(Rect panel) {
        return panel.x() + 2 * padding;
    }

    /**
     * The entry under a point, or -1: entries fill whole lines of the panel, the first on line
     * {@code firstEntryLine}.
     */
    public int entryAt(Rect panel, int firstEntryLine, int entries, int x, int y) {
        if (x < panel.x() || x >= panel.right()) {
            return -1;
        }
        for (int i = 0; i < entries; i++) {
            // A line's band: its text, and half the gap to each neighbour.
            int top = lineTop(panel, firstEntryLine + i) + (pitch - fontSize) / 2;
            if (y < top && y >= top - pitch) {
                return i;
            }
        }
        return -1;
    }
}
