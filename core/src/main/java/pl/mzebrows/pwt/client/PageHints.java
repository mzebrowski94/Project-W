package pl.mzebrows.pwt.client;

import com.badlogic.gdx.Input;
import java.util.Locale;
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
                ? PadLabels.of(controls.ui().padBack(), modality.playStation())
                : keyName(controls.ui().back());
        return strings.format("ui.hint.back", button);
    }

    /** The button that opens the pause menu. */
    String menuButton(InputModality modality) {
        return modality.padHints()
                ? PadLabels.of(controls.pad().pause(), modality.playStation())
                : keyName(controls.ui().back());
    }

    private String confirm(InputModality modality) {
        return modality.padHints()
                ? PadLabels.of(controls.ui().padConfirm(), modality.playStation())
                : keyName(controls.ui().confirm());
    }

    static String keyName(int keycode) {
        String name = Input.Keys.toString(keycode);
        return name == null ? "?" : name.toUpperCase(Locale.ROOT);
    }
}
