package pl.mzebrows.pwt.client;

import java.util.Locale;
import pl.mzebrows.pwt.client.input.ControlsConfig;
import pl.mzebrows.pwt.client.input.KeyCode;
import pl.mzebrows.pwt.client.input.PadLabels;
import pl.mzebrows.pwt.ui.nav.InputModality;

/**
 * What a page's hints call the buttons: keys after a key, the pad's own labels after a pad - Xbox or
 * PlayStation by the pad's name (plan 6.3). A keyboard hint after a pad press is what a Steam Deck
 * review fails a game for.
 */
final class PageHints {

    private final ControlsConfig controls;
    private final UiStrings strings;

    PageHints(ControlsConfig controls, UiStrings strings) {
        this.controls = controls;
        this.strings = strings;
    }

    /** {@code ENTER: SELECT   ESCAPE: BACK}, in the device's own words. */
    String selectAndBack(InputModality modality) {
        return strings.format("ui.hint.select", confirm(modality)) + "   " + back(modality);
    }

    /** {@code ESCAPE: BACK}. */
    String back(InputModality modality) {
        String button = modality.padHints()
                ? PadLabels.of(controls.ui().pad().back(), modality.playStation())
                : keyName(controls.ui().key().back());
        return strings.format("ui.hint.back", button);
    }

    /** The button that opens the pause menu. */
    String menuButton(InputModality modality) {
        return modality.padHints()
                ? PadLabels.of(controls.pad().pause(), controls.pad().pauseTrigger(), modality.playStation())
                : keyName(controls.ui().key().back());
    }

    private String confirm(InputModality modality) {
        return modality.padHints()
                ? PadLabels.of(controls.ui().pad().confirm(), modality.playStation())
                : keyName(controls.ui().key().confirm());
    }

    static String keyName(KeyCode key) {
        String name = key.name();
        return name == null ? "?" : name.toUpperCase(Locale.ROOT);
    }
}
