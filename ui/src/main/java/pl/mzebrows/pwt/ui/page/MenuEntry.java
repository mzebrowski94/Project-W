package pl.mzebrows.pwt.ui.page;

/**
 * One thing on a page that can be selected and confirmed.
 *
 * @param id the entry's name in the specification's tables
 * @param labelKey its words, a key in {@code i18n/ui.properties}
 * @param intent what confirming it asks for
 */
public record MenuEntry(String id, String labelKey, UiIntent intent) {}
