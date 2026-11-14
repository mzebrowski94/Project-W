package pl.mzebrows.pwt.client.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

/**
 * What the controls page shows ({@code UI:main/controls}): every action a player has in a level, the key
 * or mouse button it is on and the pad's button for it, a line each under a line of headings - three
 * columns, each as wide as its longest word.
 *
 * <p>Written from the bindings themselves, so a rebound key shows as what it now is and the page cannot
 * say one thing while the file says another. A pad's buttons are called what that pad calls them ({@link
 * PadLabels}). It shows; it changes nothing.
 */
public final class ControlsTable {

    /** Spaces between one column and the next. */
    private static final int GAP = 2;

    private ControlsTable() {}

    /**
     * The table's lines, the headings first, in capitals.
     *
     * @param words the interface's words by key - {@code ui.controls.*}
     * @param playStation whether the pad's buttons are called what a PlayStation pad calls them
     */
    public static List<String> lines(ControlsConfig controls, UnaryOperator<String> words, boolean playStation) {
        ControlsConfig.Keys key = controls.key();
        ControlsConfig.Pad pad = controls.pad();
        List<String[]> rows = new ArrayList<>();
        rows.add(cells(
                words.apply("ui.controls.action"),
                words.apply("ui.controls.keyboard"),
                words.apply("ui.controls.pad")));
        rows.add(cells(
                words.apply("ui.controls.move"),
                keys(key.moveUp(), key.moveLeft(), key.moveDown(), key.moveRight()),
                PadLabels.of(pad.moveX(), playStation)));
        rows.add(
                cells("", keys(key.moveUpAlias(), key.moveLeftAlias(), key.moveDownAlias(), key.moveRightAlias()), ""));
        rows.add(cells(
                words.apply("ui.controls.aim"),
                words.apply("ui.controls.mouse"),
                PadLabels.of(pad.aimX(), playStation)));
        String fire = mouse(words, controls.mouse().fire());
        String strike = mouse(words, controls.mouse().strike());
        rows.add(row(words, "fire", fire, pad.fire(), pad.fireTrigger(), playStation));
        rows.add(row(words, "strike", strike, pad.strike(), pad.strikeTrigger(), playStation));
        rows.add(row(words, "grenade", keys(key.grenade()), pad.grenade(), pad.grenadeTrigger(), playStation));
        rows.add(row(words, "act", keys(key.action()), pad.action(), pad.actionTrigger(), playStation));
        rows.add(row(words, "drag", keys(key.drag()), pad.drag(), pad.dragTrigger(), playStation));
        rows.add(row(
                words,
                "orderFollowMe",
                keys(key.orderFollowMe()),
                pad.orderFollowMe(),
                pad.orderFollowMeTrigger(),
                playStation));
        rows.add(row(
                words,
                "orderForward",
                keys(key.orderForward()),
                pad.orderForward(),
                pad.orderForwardTrigger(),
                playStation));
        rows.add(row(
                words,
                "orderDefence",
                keys(key.orderDefence()),
                pad.orderDefence(),
                pad.orderDefenceTrigger(),
                playStation));
        rows.add(row(
                words,
                "orderCancel",
                keys(key.orderCancel()),
                pad.orderCancel(),
                pad.orderCancelTrigger(),
                playStation));
        rows.add(row(words, "draft", keys(key.draft()), pad.draft(), pad.draftTrigger(), playStation));
        // A pad shuts a crate with the button its defence order is on (CrateKeys).
        rows.add(row(
                words,
                "crateClose",
                keys(key.crateClose()),
                pad.orderDefence(),
                pad.orderDefenceTrigger(),
                playStation));
        rows.add(row(words, "zoom", keys(key.zoom()), pad.zoom(), pad.zoomTrigger(), playStation));
        // In a level the key that goes back a page opens the pause menu.
        rows.add(row(words, "pause", keys(controls.ui().key().back()), pad.pause(), pad.pauseTrigger(), playStation));
        return aligned(rows);
    }

    private static String[] row(
            UnaryOperator<String> words,
            String action,
            String keyboard,
            GamepadButton button,
            GamepadAxis trigger,
            boolean playStation) {
        return cells(words.apply("ui.controls." + action), keyboard, PadLabels.of(button, trigger, playStation));
    }

    private static String[] cells(String action, String keyboard, String pad) {
        return new String[] {action, keyboard, pad};
    }

    private static String mouse(UnaryOperator<String> words, MouseButton button) {
        return words.apply("ui.controls.mouse." + button.name());
    }

    /** The keys' names as the file gives them, a space between; {@code ?} for one libGDX has no name for. */
    private static String keys(KeyCode... keys) {
        StringBuilder names = new StringBuilder();
        for (KeyCode key : keys) {
            String name = key.name();
            names.append(names.isEmpty() ? "" : " ").append(name == null ? "?" : name);
        }
        return names.toString();
    }

    private static List<String> aligned(List<String[]> rows) {
        int action = 0;
        int keyboard = 0;
        for (String[] row : rows) {
            action = Math.max(action, row[0].length());
            keyboard = Math.max(keyboard, row[1].length());
        }
        List<String> lines = new ArrayList<>(rows.size());
        for (String[] row : rows) {
            String line = pad(row[0], action + GAP) + pad(row[1], keyboard + GAP) + row[2];
            lines.add(line.stripTrailing().toUpperCase(Locale.ROOT));
        }
        return lines;
    }

    private static String pad(String text, int width) {
        return text + " ".repeat(width - text.length());
    }
}
