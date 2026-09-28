package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.SimConfig;

/**
 * {@code game.properties} is the whole of {@link SimConfig}: every value the records have, and nothing
 * they do not. A player's copy may be missing a key or carry a stale one and the game still starts;
 * the shipped one may not, because a key nothing reads is a number somebody is tuning to no effect.
 */
class ShippedConfigTest {

    private static final Path SHIPPED = Path.of("..", "assets", "game.properties");

    @Test
    void theShippedFileNamesEveryValueAndNothingElse() throws IOException {
        SimConfig.Parsed parsed = SimConfig.parse(Files.readString(SHIPPED, StandardCharsets.ISO_8859_1));

        assertThat(parsed.problems()).isEmpty();
    }

    /** A join over a slow link waits for every byte the host ships, and seven in ten were comments. */
    @Test
    void shipsToPeersWithoutItsCommentsAsTheSameConfig() throws IOException {
        String file = Files.readString(SHIPPED, StandardCharsets.ISO_8859_1);

        String shipped = SimConfigLoader.withoutComments(file);

        assertThat(SimConfigLoader.parse(shipped)).isEqualTo(SimConfigLoader.parse(file));
        assertThat(shipped.lines()).noneMatch(line -> line.startsWith("#") || line.isBlank());
        assertThat(shipped.length()).isLessThan(file.length() / 3);
    }

    /** Proves the compiled-in copy is this file rather than an older build of it. */
    @Test
    void isWhatTheSimulationDefaultsTo() throws IOException {
        SimConfig shipped = SimConfigLoader.parse(Files.readString(SHIPPED, StandardCharsets.ISO_8859_1));

        assertThat(shipped).isEqualTo(SimConfig.defaults());
    }

    @Test
    void takesTheShippedValueForAKeyAPlayersFileGetsWrong() throws IOException {
        String broken = Files.readString(SHIPPED, StandardCharsets.ISO_8859_1)
                .replace("sim.commander.speed=", "sim.commander.speed=fast\n#");

        SimConfig.Parsed parsed = SimConfig.parse(broken);

        assertThat(parsed.config()).isEqualTo(SimConfig.defaults());
        assertThat(parsed.problems()).singleElement().asString().contains("sim.commander.speed");
    }
}
