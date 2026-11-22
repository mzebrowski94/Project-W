package pl.mzebrows.pwt.client.input;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.SortedMap;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.client.draw.GraphicConfig;
import pl.mzebrows.pwt.sim.ConfigBinder;

/** The controls page says what the bindings say, in the words of the pad in the player's hands. */
class ControlsTableTest {

    private static final UnaryOperator<String> WORDS = shippedWords();

    @Test
    void showsEveryActionWithItsKeyAndItsPadButton() {
        List<String> lines =
                ControlsTable.of(ControlsConfig.defaults(), WORDS, false).lines();

        assertThat(lines.get(0)).matches("ACTION +KEYBOARD +GAMEPAD");
        assertThat(lines)
                .anyMatch(line -> line.matches("MOVE +W A S D +LEFT STICK"))
                .anyMatch(line -> line.matches(" +UP LEFT DOWN RIGHT"))
                .anyMatch(line -> line.matches("AIM +MOUSE +RIGHT STICK"))
                .anyMatch(line -> line.matches("FIRE +LEFT MOUSE +X/RT"))
                .anyMatch(line -> line.matches("STRIKE +RIGHT MOUSE +LT"))
                .anyMatch(line -> line.matches("GRENADE +Q +RB"))
                .anyMatch(line -> line.matches("DRAG A LOAD +F +-"))
                .anyMatch(line -> line.matches("DASH +SPACE L-SHIFT +B"))
                .anyMatch(line -> line.matches("ORDER: FOLLOW ME +1 +D-PAD UP"))
                .anyMatch(line -> line.matches("UPGRADES +C +Y"))
                .anyMatch(line -> line.matches("SHUT A CRATE +X +D-PAD DOWN"))
                .anyMatch(line -> line.matches("ZOOM OUT +TAB +LB"))
                .anyMatch(line -> line.matches("PAUSE +ESCAPE +START"));
    }

    @Test
    void showsAReboundKeyAsWhatItNowIs() {
        ControlsConfig rebound = ControlsConfig.FILE
                .read("controls.key.grenade=G\ncontrols.pad.grenade=EAST\n")
                .config();

        assertThat(ControlsTable.of(rebound, WORDS, false).lines()).anyMatch(line -> line.matches("GRENADE +G +B"));
    }

    @Test
    void callsAPlayStationPadsButtonsWhatItCallsThem() {
        List<String> lines =
                ControlsTable.of(ControlsConfig.defaults(), WORDS, true).lines();

        assertThat(lines)
                .anyMatch(line -> line.matches("GRENADE +Q +R1"))
                .anyMatch(line -> line.matches("DASH +SPACE L-SHIFT +CIRCLE"))
                .anyMatch(line -> line.matches("FIRE +LEFT MOUSE +SQUARE/R2"));
    }

    @Test
    void startsEachColumnAtTheSamePlaceOnEveryLine() {
        List<String> lines =
                ControlsTable.of(ControlsConfig.defaults(), WORDS, false).lines();
        int keyboard = lines.get(0).indexOf("KEYBOARD");
        int pad = lines.get(0).indexOf("GAMEPAD");

        assertThat(lines.get(2).substring(keyboard)).startsWith("W A S D");
        assertThat(lines.get(2).substring(pad)).isEqualTo("LEFT STICK");
        for (String line : lines) {
            assertThat(line.charAt(keyboard - 1)).as(line).isEqualTo(' ');
            assertThat(line.charAt(keyboard)).as(line).isNotEqualTo(' ');
        }
    }

    /** The headings and the actions are what the eye runs down; the bindings are what it stops at. */
    @Test
    void brightensTheHeadingsAndTheActionsButNotTheBindings() {
        ControlsTable table = ControlsTable.of(ControlsConfig.defaults(), WORDS, false);
        List<String> lines = table.lines();
        int fire = indexOf(lines, "FIRE ");

        assertThat(table.bright(0)).isEqualTo(lines.get(0).length());
        assertThat(lines.get(fire).substring(0, table.bright(fire)).strip()).isEqualTo("FIRE");
        assertThat(lines.get(fire).substring(table.bright(fire)).strip()).startsWith("LEFT MOUSE");
    }

    @Test
    void saysUnderTheHeadingsThatTheKeyboardIsPlayerOnesAndThePadEverybodyElses() {
        ControlsTable table = ControlsTable.of(ControlsConfig.defaults(), WORDS, false);
        List<String> lines = table.lines();
        String note = lines.get(1);

        assertThat(note).matches(" +PLAYER 1 +PLAYERS 2\\+");
        assertThat(note.indexOf("PLAYER 1")).isEqualTo(lines.get(0).indexOf("KEYBOARD"));
        assertThat(note.indexOf("PLAYERS 2+")).isEqualTo(lines.get(0).indexOf("GAMEPAD"));
        assertThat(table.bright(1)).isZero();
    }

    /** A note longer than its column is cut to it: the bindings are what the columns are sized by. */
    @Test
    void keepsTheColumnsWhereTheyAreHoweverLongTheNoteIs() {
        UnaryOperator<String> wordy = key -> key.endsWith(".note") ? "X".repeat(40) : WORDS.apply(key);
        List<String> lines =
                ControlsTable.of(ControlsConfig.defaults(), WORDS, false).lines();
        List<String> wordyLines =
                ControlsTable.of(ControlsConfig.defaults(), wordy, false).lines();
        int pad = lines.get(0).indexOf("GAMEPAD");

        assertThat(wordyLines.get(0)).isEqualTo(lines.get(0));
        assertThat(wordyLines.get(1).substring(0, pad)).endsWith("  ");
        assertThat(wordyLines.get(1).length())
                .isLessThanOrEqualTo(
                        lines.stream().mapToInt(String::length).max().orElseThrow());
    }

    /**
     * A line longer than a page is wide would be cut, and a word the interface lacks draws as its key - in
     * English and in a tongue every word of which runs 30 % longer, with diacritics.
     */
    @Test
    void fitsAPageWithEveryWordTheInterfaceHasInEnglishAndALongerTongue() {
        int columns = GraphicConfig.defaults().ui().page().columns();

        for (UnaryOperator<String> words : List.of(WORDS, pseudo(WORDS))) {
            for (boolean playStation : new boolean[] {false, true}) {
                assertThat(ControlsTable.of(ControlsConfig.defaults(), words, playStation)
                                .lines())
                        .allSatisfy(line -> assertThat(line)
                                .hasSizeLessThanOrEqualTo(columns)
                                .doesNotContain("#"));
            }
        }
    }

    private static int indexOf(List<String> lines, String start) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(start)) {
                return i;
            }
        }
        throw new AssertionError("no line starts with " + start);
    }

    /** Every word 30 % longer, padded with Polish letters, as a translation would run. */
    private static UnaryOperator<String> pseudo(UnaryOperator<String> words) {
        String letters = "ĄĘŚŻŹĆŁÓŃ";
        return key -> {
            String word = words.apply(key);
            int more = (word.length() * 3 + 9) / 10;
            StringBuilder longer = new StringBuilder(word);
            for (int i = 0; i < more; i++) {
                longer.append(letters.charAt(i % letters.length()));
            }
            return longer.toString();
        };
    }

    private static UnaryOperator<String> shippedWords() {
        try {
            SortedMap<String, String> words = ConfigBinder.parse(
                    Files.readString(Path.of("..", "assets", "i18n", "ui.properties"), StandardCharsets.UTF_8));
            return key -> words.getOrDefault(key, "#" + key);
        } catch (IOException e) {
            throw new IllegalStateException("assets/i18n/ui.properties must be readable from the core module", e);
        }
    }
}
