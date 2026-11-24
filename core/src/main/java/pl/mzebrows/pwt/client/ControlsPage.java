package pl.mzebrows.pwt.client;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.client.hud.UiStrings;
import pl.mzebrows.pwt.client.input.ControlsConfig;
import pl.mzebrows.pwt.client.input.ControlsTable;
import pl.mzebrows.pwt.client.input.PlayerOne;
import pl.mzebrows.pwt.ui.page.MenuEntry;
import pl.mzebrows.pwt.ui.page.MenuTree;
import pl.mzebrows.pwt.ui.page.UiPage;

/**
 * The controls page's words, wherever it opens - {@code UI:main/controls} and {@code UI:play/pause/controls}: the
 * PROMPTS switch with its ON or OFF, the PLAYER 1 switch with its device, BACK, and the table of bindings under them.
 * The one place the switches are flipped and the table written, so the two pages cannot drift apart.
 */
final class ControlsPage {

    private final ControlsConfig controls;
    private final LocalSettings settings;
    private final UiStrings strings;
    private final List<String> table = new ArrayList<>();
    private @Nullable ControlsTable written;
    private boolean playStation;

    ControlsPage(ControlsConfig controls, LocalSettings settings, UiStrings strings) {
        this.controls = controls;
        this.settings = settings;
        this.strings = strings;
    }

    /**
     * The page opens: the table written in the words of the pad last pressed, as the hints are - Xbox's where
     * none was - and the entries' labels into {@code labels}.
     */
    void open(boolean playStation, List<String> labels) {
        this.playStation = playStation;
        write();
        label(labels);
    }

    private void write() {
        ControlsTable bindings = ControlsTable.of(controls, strings::get, playStation, playerOne());
        written = bindings;
        table.clear();
        table.addAll(bindings.lines());
    }

    /** What player one plays with on this computer: the switch, else what ships. */
    private PlayerOne playerOne() {
        return settings.playerOne(controls.playerOne());
    }

    /** The table's lines, as written when the page last opened: what goes under the entries. */
    List<String> table() {
        return table;
    }

    /** How many of a table line's first characters are bright: its headings and its actions. */
    int bright(int line) {
        ControlsTable bindings = written;
        return bindings == null ? 0 : bindings.bright(line);
    }

    /** Switches the in-world prompts on this computer, kept at once, and writes the switch again. */
    void flip(List<String> labels) {
        settings.prompts(!settings.prompts());
        label(labels);
    }

    /**
     * Switches what player one plays with on this computer, kept at once, and writes the switch and the table's notes
     * again. The next level seats him by it; the page locks it while one runs.
     */
    void flipPlayerOne(List<String> labels) {
        settings.switchPlayerOne(playerOne() == PlayerOne.PAD ? PlayerOne.KEYBOARD : PlayerOne.PAD);
        write();
        label(labels);
    }

    /** The entries' labels: PROMPTS with its ON or OFF, PLAYER 1 with its device, then BACK. */
    private void label(List<String> labels) {
        labels.clear();
        for (MenuEntry entry : MenuTree.page(UiPage.CONTROLS).entries()) {
            labels.add(strings.get(entry.labelKey()));
        }
        String state = strings.get(settings.prompts() ? "ui.controls.on" : "ui.controls.off");
        labels.set(MenuTree.CONTROLS_PROMPTS, strings.format("ui.controls.prompts", state));
        labels.set(
                MenuTree.CONTROLS_PLAYER_ONE,
                strings.format("ui.controls.playerOne", strings.get("ui.controls.playerOne." + playerOne())));
    }
}
