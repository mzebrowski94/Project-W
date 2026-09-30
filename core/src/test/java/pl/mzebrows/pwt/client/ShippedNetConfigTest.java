package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.net.NetConfig;

/**
 * {@code network.properties} is the whole of {@link NetConfig}, as {@code assets/sim/*.properties} are of the
 * simulation's: every value the records have and nothing else, so a misspelt key is a failure rather than
 * a number tuned to no effect.
 */
class ShippedNetConfigTest {

    private static final Path SHIPPED = Path.of("..", "assets", "network.properties");

    @Test
    void theShippedFileNamesEveryValueAndNothingElse() throws IOException {
        NetConfig.Parsed parsed = NetConfig.parse(Files.readString(SHIPPED, StandardCharsets.ISO_8859_1));

        assertThat(parsed.problems()).isEmpty();
    }

    /** Proves the compiled-in copy is this file rather than an older build of it. */
    @Test
    void isWhatTheNetworkDefaultsTo() throws IOException {
        NetConfig shipped = NetConfigLoader.parse(Files.readString(SHIPPED, StandardCharsets.ISO_8859_1));

        assertThat(shipped).isEqualTo(NetConfig.defaults());
    }

    @Test
    void namesAMisspeltKeyAndKeepsTheShippedValue() throws IOException {
        String misspelt = Files.readString(SHIPPED, StandardCharsets.ISO_8859_1)
                .replace("net.pacing.leadMaxTicks=", "net.pacing.leadMaxTick=");

        NetConfig.Parsed parsed = NetConfig.parse(misspelt);

        assertThat(parsed.config()).isEqualTo(NetConfig.defaults());
        assertThat(parsed.problems())
                .containsExactlyInAnyOrder("missing net.pacing.leadMaxTicks", "unknown key net.pacing.leadMaxTick");
    }

    @Test
    void readsTheLatencyStepsAsAList() {
        assertThat(NetConfig.defaults().latencySteps()).containsExactly(0, 25, 50, 100);
    }
}
