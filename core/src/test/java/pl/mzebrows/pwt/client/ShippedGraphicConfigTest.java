package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.ConfigBinder;
import pl.mzebrows.pwt.sim.Players;

/**
 * {@code graphic.properties} is the whole of {@link GraphicFile}, as {@code network.properties} is of the
 * network's config: every value the records have and nothing else, so a misspelt key is a failure rather
 * than a look tuned to no effect, and there is no default in the code to drift from what ships.
 */
class ShippedGraphicConfigTest {

    private static final Path SHIPPED = Path.of("..", "assets", "graphic.properties");

    @Test
    void theShippedFileNamesEveryValueAndNothingElse() throws IOException {
        assertThat(GraphicConfig.read(shipped()).problems()).isEmpty();
    }

    /** Proves the compiled-in copy is this file rather than an older build of it. */
    @Test
    void isWhatTheGraphicsDefaultTo() throws IOException {
        assertThat(ConfigBinder.parse(ShippedGraphicConfig.TEXT)).isEqualTo(ConfigBinder.parse(shipped()));
    }

    @Test
    void namesAMisspeltKeyAndKeepsTheShippedValue() throws IOException {
        String misspelt = shipped().replace("graphic.tile.shades=", "graphic.tile.shade=");

        GraphicConfig.Parsed parsed = GraphicConfig.read(misspelt);

        assertThat(parsed.problems())
                .containsExactlyInAnyOrder("missing graphic.tile.shades", "unknown key graphic.tile.shade");
        assertThat(parsed.config().terrainPalette().tileShades())
                .isEqualTo(GraphicConfig.defaults().terrainPalette().tileShades());
    }

    @Test
    void namesAColourThatIsNotOneAndKeepsTheShippedColour() throws IOException {
        String garbled = shipped().replaceFirst("graphic\\.shield\\.color=\\w+", "graphic.shield.color=bluish");

        GraphicConfig.Parsed parsed = GraphicConfig.read(garbled);

        assertThat(parsed.problems()).containsExactly("graphic.shield.color=bluish is not a colour");
        assertThat(parsed.config().shieldMark().color())
                .isEqualTo(GraphicConfig.defaults().shieldMark().color());
    }

    /** A colour for every player the game can hold, so the ceiling cannot move without the file following. */
    @Test
    void holdsAColourForEveryPlayerAGameCanSeat() {
        assertThat(GraphicFile.Variants.class.getRecordComponents()).hasSize(Players.MAX - 1);
    }

    private static String shipped() throws IOException {
        return Files.readString(SHIPPED, StandardCharsets.ISO_8859_1);
    }
}
