package pl.mzebrows.pwt.ui.page;

import java.util.List;

/**
 * What one page shows: a title, an optional line of body text, its entries, and which is selected when
 * the player arrives with nothing remembered.
 *
 * @param titleKey key of the title in {@code i18n/ui.properties}, or empty for none
 * @param bodyKey key of a line under the title, or empty for none
 * @param defaultSelection the entry selected on arrival - on a confirm, the one that does nothing, so
 *     a repeated press cannot quit or leave by accident
 */
public record MenuPage(String titleKey, String bodyKey, List<MenuEntry> entries, int defaultSelection) {

    public MenuPage {
        entries = List.copyOf(entries);
        if (defaultSelection < 0 || defaultSelection >= Math.max(1, entries.size())) {
            throw new IllegalArgumentException("default selection " + defaultSelection + " of " + entries.size());
        }
    }
}
