package pl.mzebrows.pwt.ui.page;

import pl.mzebrows.pwt.ui.layout.Rect;

/**
 * Where a page's panel and its lines go on the UI canvas, and which entry a pointer is over - worked out
 * here so hovering and clicking are testable without a display.
 *
 * <p>A page is a column of lines in one panel: a head (title, body), the entries, and a foot (anything
 * the page lists under its entries, then the hint). In a level the panel is <b>docked</b> to the left
 * third of the view, because the camera centres on the player's man and a centred menu covers exactly
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
        CENTRED
    }

    /** The panel for this many lines, the widest {@code widestChars} characters long. */
    public Rect panel(Rect view, Anchor anchor, int lines, int widestChars) {
        int wanted = widestChars * advance + 4 * padding;
        int height = Math.min(view.height(), lines * pitch + 3 * padding);
        if (anchor == Anchor.DOCKED_LEFT) {
            int width = Math.min(view.width() - 4 * padding, Math.max(view.width() / 3, wanted));
            int top = view.top() - 2 * padding;
            return new Rect(view.x() + 2 * padding, top - height, width, height);
        }
        int width = Math.min(view.width(), wanted);
        return new Rect(view.x() + (view.width() - width) / 2, view.y() + (view.height() - height) / 2, width, height);
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
