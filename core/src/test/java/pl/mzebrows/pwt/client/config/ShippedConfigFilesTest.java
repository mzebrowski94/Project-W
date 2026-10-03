package pl.mzebrows.pwt.client.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import pl.mzebrows.pwt.client.draw.GraphicConfig;
import pl.mzebrows.pwt.client.input.ControlsConfig;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.sim.ConfigBinder;
import pl.mzebrows.pwt.sim.ConfigFile;
import pl.mzebrows.pwt.sim.SimConfig;

/**
 * Every shipped config file is held to the same three terms: it names every value and nothing else, the
 * copy compiled in is the file on disk, and a misspelt key is named and the shipped value stands for it.
 * What is particular to one file is tested beside that file.
 */
class ShippedConfigFilesTest {

    private static final Path ASSETS = Path.of("..", "assets");

    /** A shipped file: how it is read, and its text as it is on disk. */
    record Shipped(ConfigFile<?> file, String disk) {

        @Override
        public String toString() {
            return file.name();
        }
    }

    static Stream<Shipped> files() {
        StringBuilder sim = new StringBuilder();
        for (String file : SimConfig.shippedFiles()) {
            sim.append(read("sim/" + file)).append('\n');
        }
        return Stream.of(
                new Shipped(SimConfig.FILE, sim.toString()),
                new Shipped(NetConfig.FILE, read("network.properties")),
                new Shipped(ControlsConfig.FILE, read("controls.properties")),
                new Shipped(GraphicConfig.FILE, read("graphic.properties")));
    }

    @ParameterizedTest
    @MethodSource("files")
    void namesEveryValueAndNothingElse(Shipped shipped) {
        assertThat(shipped.file().read(shipped.disk()).problems()).isEmpty();
    }

    /** Proves the compiled-in copy is the file on disk rather than an older build of it. */
    @ParameterizedTest
    @MethodSource("files")
    void isTheFileCompiledIn(Shipped shipped) {
        assertThat(shipped.file().shippedValues()).isEqualTo(ConfigBinder.parse(shipped.disk()));
    }

    @ParameterizedTest
    @MethodSource("files")
    void namesAMisspeltKeyAndKeepsTheShippedValue(Shipped shipped) {
        String key = ConfigBinder.parse(shipped.disk()).lastKey();
        String misspelt = shipped.disk().replace(key + "=", key + "Typo=");

        ConfigFile.Parsed<?> parsed = shipped.file().read(misspelt);

        assertThat(parsed.problems()).containsExactlyInAnyOrder("missing " + key, "unknown key " + key + "Typo");
        assertThat(parsed.config()).isEqualTo(shipped.file().defaults());
    }

    private static String read(String file) {
        try {
            return Files.readString(ASSETS.resolve(file), StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
