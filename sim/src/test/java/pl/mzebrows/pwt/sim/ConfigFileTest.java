package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Every config file is read the same way: no text is what ships, and whatever the text lacks, garbles or
 * adds is named, with the shipped value standing for it.
 */
class ConfigFileTest {

    record Body(int count, float radius) {}

    private static final String SHIPPED = "b.count=3\nb.radius=1.5\n";

    private final ConfigFile<Body> file =
            new ConfigFile<>("body.properties", Body.class, "b", SHIPPED, ConfigBinder.Conversions.none());

    @Test
    void readsNoTextAtAllAsWhatShips() {
        ConfigFile.Parsed<Body> parsed = file.read("  \n");

        assertThat(parsed.config()).isEqualTo(new Body(3, 1.5f));
        assertThat(parsed.problems()).isEmpty();
    }

    @Test
    void takesWhatTheTextLacksOrGarblesFromWhatShipsAndNamesIt() {
        ConfigFile.Parsed<Body> parsed = file.read("b.count=lots\n");

        assertThat(parsed.config()).isEqualTo(new Body(3, 1.5f));
        assertThat(parsed.problems())
                .containsExactlyInAnyOrder(
                        "cannot read b.count=lots as int (For input string: \"lots\")", "missing b.radius");
    }

    @Test
    void namesAKeyNoRecordHasAndOneGivenTwice() {
        ConfigFile.Parsed<Body> parsed = file.read("b.count=4\nb.count=5\nb.radius=2\nb.colour=red\n");

        assertThat(parsed.config()).isEqualTo(new Body(5, 2f));
        assertThat(parsed.problems()).containsExactlyInAnyOrder("key given twice: b.count", "unknown key b.colour");
    }

    @Test
    void bindsWhatShipsOnceAndStrictly() {
        assertThat(file.defaults()).isEqualTo(new Body(3, 1.5f));
        assertThat(file.shippedValues()).containsEntry("b.radius", "1.5");
    }
}
