package pl.mzebrows.pwt.ui.nav;

import java.util.Arrays;
import pl.mzebrows.pwt.ui.page.UiPage;

/**
 * Which entry each page had selected when the player last left it, so coming back lands where he was.
 * A page never visited reports its own default.
 */
public final class SelectionMemory {

    private final int[] selected = new int[UiPage.values().length];

    public SelectionMemory() {
        Arrays.fill(selected, -1);
    }

    /** The remembered entry, or {@code fallback} if the page has none or it no longer exists. */
    @SuppressWarnings("EnumOrdinal")
    public int get(UiPage page, int fallback, int entries) {
        int remembered = selected[page.ordinal()];
        return remembered >= 0 && remembered < entries ? remembered : fallback;
    }

    @SuppressWarnings("EnumOrdinal")
    public void set(UiPage page, int entry) {
        selected[page.ordinal()] = entry;
    }

    /** Forgets a page - a confirm always opens on its harmless answer, never on the last one chosen. */
    @SuppressWarnings("EnumOrdinal")
    public void forget(UiPage page) {
        selected[page.ordinal()] = -1;
    }
}
