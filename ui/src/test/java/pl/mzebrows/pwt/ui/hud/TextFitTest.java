package pl.mzebrows.pwt.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.sim.scenario.Scenario;
import pl.mzebrows.pwt.sim.scenario.Scenarios;

/**
 * Every template the bars draw, with its longest argument, against the slot it goes in - at the 640-wide
 * canvas every 16:9 and 16:10 screen gets, with four panels. The strings come from the shipped file, so a
 * copy change that stops fitting fails here rather than on somebody's screen.
 */
class TextFitTest {

    private static final int ADVANCE = 6;
    private static final int PADDING = 4;
    private static final int WEAPON_COLUMNS = 7;
    private static final PanelLayout PANEL = new PanelLayout(PADDING, 32, ADVANCE);
    private static final TopBarLayout TOP = new TopBarLayout(PADDING, 0.34f);
    private static final Properties STRINGS = strings();
    /** Pseudo-locale: every template 30 % longer and with Polish letters, as a translation would be. */
    private static final String PSEUDO = "ĄĘŚŻŹĆŁÓŃ";

    @Test
    void cutsTheArgumentBeforeTheTemplate() {
        assertThat(TextFit.fit("GOAL: {0}", "CAPTURE THE POINTS", 14)).isEqualTo("GOAL: CAPTU...");
    }

    @Test
    void cutsTheWholeLineWhenEvenTheTemplateDoesNotFit() {
        assertThat(TextFit.fit("CURRENT GOAL: {0}", "X", 8)).isEqualTo("CURRE...");
    }

    @Test
    void leavesALineThatFitsAlone() {
        assertThat(TextFit.fit("MISSION: {0}", "Duel", 20)).isEqualTo("MISSION: Duel");
    }

    @Test
    void wrapsADescriptionOnWordsIntoTwoLines() {
        assertThat(TextFit.wrap("Two squads and a nest in open ground", 20, 2))
                .containsExactly("Two squads and a", "nest in open ground");
    }

    @Test
    void cutsWhatTwoLinesCannotHoldAndSaysSo() {
        assertThat(TextFit.wrap("one two three four five six seven eight", 10, 2))
                .containsExactly("one two", "three f...");
    }

    @Test
    void cutsAWordLongerThanALine() {
        assertThat(TextFit.wrap("Feedinggroundfeeding", 8, 2)).containsExactly("Feedingg", "round...");
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"0, 0", "7, 7", "999, 999", "1000, 999+", "123456, 999+", "-3, 0"})
    void showsACountAboveNineHundredAndNinetyNineAsNineHundredAndNinetyNinePlus(int value, String shown) {
        assertThat(TextFit.count(value)).isEqualTo(shown);
    }

    @Test
    void namesEveryGunInSevenCharactersOrFewer() {
        for (WeaponKind kind : WeaponKind.values()) {
            String name = STRINGS.getProperty("ui.weapon." + kind.name());

            assertThat(name).as("ui.weapon.%s", kind).isNotNull();
            assertThat(name.length()).as("ui.weapon.%s", kind).isLessThanOrEqualTo(WEAPON_COLUMNS);
        }
    }

    @Test
    void fitsTheWorstStatusBesideTheWorstLabelInOnePanel() {
        String label = STRINGS.getProperty("ui.hud.player").replace("{0}", Integer.toString(Players.MAX));
        String kia = STRINGS.getProperty("ui.hud.kia").replace("{0}", "9.9");
        String longest = longestOf(kia, STRINGS.getProperty("ui.hud.down"), STRINGS.getProperty("ui.hud.aboard"));

        assertThat((label + " " + longest).length()).isLessThanOrEqualTo(PanelLayout.TEXT_COLUMNS);
        assertThat(STRINGS.getProperty("ui.hud.overheat").length() + WEAPON_COLUMNS + 1)
                .isLessThanOrEqualTo(PanelLayout.TEXT_COLUMNS);
    }

    @Test
    void fitsEveryTopBarTemplateWithItsLongestArgumentOnA640Canvas() {
        int columns = TextFit.columns(TOP.textWidth(640), ADVANCE);

        for (String line : topBarLines()) {
            assertThat(line.length()).as(line).isLessThanOrEqualTo(columns);
        }
    }

    @ParameterizedTest(name = "canvas {0}")
    @CsvSource({"640", "512"})
    void degradesEveryTopBarLineRatherThanOverflowingInALongerLanguage(int canvasWidth) {
        int columns = TextFit.columns(TOP.textWidth(canvasWidth), ADVANCE);

        for (String line : topBarLines()) {
            String longer = line + PSEUDO.repeat(1 + line.length() * 3 / 10 / PSEUDO.length());

            String fitted = TextFit.fit(longer, columns);

            assertThat(fitted.length()).as(longer).isLessThanOrEqualTo(columns);
        }
    }

    @Test
    void fitsAPanelsTextBesideItsPortraitOnEverySixteenByNineScreen() {
        assertThat(PANEL.textColumns(PANEL.quarterWidth(0, 640))).isGreaterThanOrEqualTo(PanelLayout.TEXT_COLUMNS);
    }

    private static String[] topBarLines() {
        String goal = STRINGS.getProperty("ui.hud.goal");
        return new String[] {
            STRINGS.getProperty("ui.hud.planet").replace("{0}", "ZXC-123") + "  "
                    + STRINGS.getProperty("ui.hud.mission").replace("{0}", longestScenarioName()),
            goal.replace("{0}", longestGoal()),
            goal.replace("{0}", STRINGS.getProperty("ui.hud.goal.complete")),
        };
    }

    private static String longestGoal() {
        String longest = "";
        for (Goal goal : Goal.values()) {
            String text = STRINGS.getProperty("ui.goal." + goal.name());
            if (text != null) {
                longest = longestOf(longest, text.replace("{0}", "99").replace("{1}", "99"));
            }
        }
        return longest;
    }

    private static String longestScenarioName() {
        String longest = "";
        for (Scenario scenario : Scenarios.all()) {
            longest = longestOf(longest, scenario.name());
        }
        return longest;
    }

    private static String longestOf(String... texts) {
        String longest = "";
        for (String text : texts) {
            if (text.length() > longest.length()) {
                longest = text;
            }
        }
        return longest;
    }

    private static Properties strings() {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of("../assets/i18n/ui.properties"), StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new IllegalStateException("assets/i18n/ui.properties must be readable from the ui module", e);
        }
        return properties;
    }
}
