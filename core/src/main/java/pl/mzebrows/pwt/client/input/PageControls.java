package pl.mzebrows.pwt.client.input;

import pl.mzebrows.pwt.ui.layout.Rect;
import pl.mzebrows.pwt.ui.nav.InputModality;
import pl.mzebrows.pwt.ui.nav.NavInput;
import pl.mzebrows.pwt.ui.page.MenuLayout;

/**
 * The selection on whichever page is open, and what the player did with it this frame: moved it, hovered
 * an entry, confirmed one, or went back. One of these serves every page - the pause menu and the main
 * menu alike - so there is one way a page is driven. The list cursor itself is {@link MenuSelection}.
 *
 * <p>Entries that cannot be confirmed now are passed over. A page longer than the room it has scrolls,
 * and the selection drives the scroll.
 */
public final class PageControls {

    /** Nothing was confirmed and nobody went back. */
    static final int NOTHING = -1;

    /** The player went back. */
    public static final int BACK = -2;

    private static final int MAX_ENTRIES = 64;

    private final boolean[] enabled = new boolean[MAX_ENTRIES];
    private MenuSelection selection = new MenuSelection(1);
    private int count = 1;
    private boolean hovering;
    private int scroll;

    /** A page arrived with {@code count} entries; {@link #enabled()} must be filled before the next frame. */
    public void reset(int entries, int selected) {
        count = Math.max(1, Math.min(entries, MAX_ENTRIES));
        selection = new MenuSelection(count);
        selection.select(selected);
        hovering = false;
        scroll = 0;
        for (int i = 0; i < count; i++) {
            enabled[i] = true;
        }
    }

    /** Which entries can be confirmed now; the caller writes into it. */
    public boolean[] enabled() {
        return enabled;
    }

    /** Moves off an entry that cannot be confirmed, if the selection sits on one. */
    public void settle() {
        if (!enabled[selection.index()]) {
            step(1);
        }
    }

    /**
     * Reads this frame's navigation.
     *
     * @param panel where the page was drawn last frame, for the pointer
     * @param firstEntryLine the panel line the first shown entry is on
     * @param visible how many entries the panel shows at once
     * @return the entry confirmed, {@link #BACK}, or {@link #NOTHING}
     */
    public int handle(NavInput nav, MenuLayout layout, Rect panel, int firstEntryLine, int visible) {
        if (nav.up) {
            step(-1);
        }
        if (nav.down) {
            step(1);
        }
        int row = panel.isEmpty()
                ? -1
                : layout.entryAt(panel, firstEntryLine, Math.min(visible, count), nav.pointerX, nav.pointerY);
        int under = row < 0 ? -1 : scroll + row;
        if (nav.pointerMoved) {
            hovering = under >= 0 && under < count && enabled[under];
            if (hovering) {
                selection.select(under);
            }
        }
        follow(visible);
        if (nav.pointerPressed && under >= 0 && under < count && enabled[under]) {
            selection.select(under);
            return under;
        }
        if (nav.confirm && enabled[selection.index()]) {
            return selection.index();
        }
        if (nav.back || nav.pointerSecondary) {
            return BACK;
        }
        return NOTHING;
    }

    /** Jumps to an entry - a number key on a list - if it can be confirmed. */
    public boolean jump(int entry) {
        if (entry < 0 || entry >= count || !enabled[entry]) {
            return false;
        }
        return selection.select(entry);
    }

    public int index() {
        return selection.index();
    }

    /** The entry to ring, or -1 in pointer mode with nothing under the pointer. */
    public int shown(InputModality modality) {
        return modality.mode() == InputModality.Mode.POINTER && !hovering ? -1 : selection.index();
    }

    /** The first entry shown, so that the selected one is always in the window. */
    public int scroll() {
        return scroll;
    }

    private void follow(int visible) {
        int window = Math.max(1, visible);
        if (selection.index() < scroll) {
            scroll = selection.index();
        } else if (selection.index() >= scroll + window) {
            scroll = selection.index() - window + 1;
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, count - window)));
    }

    private void step(int direction) {
        hovering = false;
        for (int tries = 0; tries < count; tries++) {
            if (direction < 0) {
                selection.previous();
            } else {
                selection.next();
            }
            if (enabled[selection.index()]) {
                return;
            }
        }
    }
}
