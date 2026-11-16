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
        List<String> lines = ControlsTable.lines(ControlsConfig.defaults(), WORDS, false);

        assertThat(lines.get(0)).matches("ACTION +KEYBOARD +GAMEPAD");
        assertThat(lines)
                .anyMatch(line -> line.matches("MOVE +W A S D +LEFT STICK"))
                .anyMatch(line -> line.matches(" +UP LEFT DOWN RIGHT"))
                .anyMatch(line -> line.matches("AIM +MOUSE +RIGHT STICK"))
                .anyMatch(line -> line.matches("FIRE +LEFT MOUSE +X/RT"))
                .anyMatch(line -> line.matches("STRIKE +RIGHT MOUSE +LT"))
                .anyMatch(line -> line.matches("GRENADE +Q +RB"))
                .anyMatch(line -> line.matches("DRAG A LOAD +F +B"))
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

        assertThat(ControlsTable.lines(rebound, WORDS, false)).anyMatch(line -> line.matches("GRENADE +G +B"));
    }

    @Test
    void callsAPlayStationPadsButtonsWhatItCallsThem() {
        List<String> lines = ControlsTable.lines(ControlsConfig.defaults(), WORDS, true);

        assertThat(lines)
                .anyMatch(line -> line.matches("GRENADE +Q +R1"))
                .anyMatch(line -> line.matches("DRAG A LOAD +F +CIRCLE"))
                .anyMatch(line -> line.matches("FIRE +LEFT MOUSE +SQUARE/R2"));
    }

    @Test
    void startsEachColumnAtTheSamePlaceOnEveryLine() {
        List<String> lines = ControlsTable.lines(ControlsConfig.defaults(), WORDS, false);
        int keyboard = lines.get(0).indexOf("KEYBOARD");
        int pad = lines.get(0).indexOf("GAMEPAD");

        assertThat(lines.get(1).substring(keyboard)).startsWith("W A S D");
        assertThat(lines.get(1).substring(pad)).isEqualTo("LEFT STICK");
        for (String line : lines) {
            assertThat(line.charAt(keyboard - 1)).as(line).isEqualTo(' ');
            assertThat(line.charAt(keyboard)).as(line).isNotEqualTo(' ');
        }
    }

    /** A line longer than a page is wide would be cut, and a word the interface lacks draws as its key. */
    @Test
    void fitsAPageWithEveryWordTheInterfaceHas() {
        int columns = GraphicConfig.defaults().ui().page().columns();

        for (boolean playStation : new boolean[] {false, true}) {
            assertThat(ControlsTable.lines(ControlsConfig.defaults(), WORDS, playStation))
                    .allSatisfy(line ->
                            assertThat(line).hasSizeLessThanOrEqualTo(columns).doesNotContain("#"));
        }
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
