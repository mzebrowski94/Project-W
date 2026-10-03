package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.ConfigBinder;
import pl.mzebrows.pwt.sim.SimConfig;

/**
 * {@code assets/sim/*.properties} are together the whole of {@link SimConfig}: every value the records
 * have, and nothing they do not. A player's copy may be missing a key or carry a stale one and the game
 * still starts; the shipped ones may not, because a key nothing reads is a number somebody is tuning to
 * no effect - and neither may give one key in two files, where only one of them would count.
 */
class ShippedConfigTest {

    private static final Path SHIPPED = Path.of("..", "assets", "sim");

    /** The build writes the list from the directory; this holds the two to one another. */
    @Test
    void readsEveryFileTheDirectoryHoldsAndNoOther() throws IOException {
        List<String> onDisk;
        try (Stream<Path> files = Files.list(SHIPPED)) {
            onDisk = files.map(file -> file.getFileName().toString())
                    .filter(name -> name.endsWith(".properties"))
                    .sorted()
                    .toList();
        }

        assertThat(SimConfig.shippedFiles()).isEqualTo(onDisk);
    }

    @Test
    void givesEveryKeyInOneFileOnly() throws IOException {
        Map<String, String> fileOfKey = new TreeMap<>();
        List<String> twice = new ArrayList<>();

        for (String file : SimConfig.shippedFiles()) {
            for (String key : ConfigBinder.parse(read(file)).keySet()) {
                String earlier = fileOfKey.put(key, file);
                if (earlier != null) {
                    twice.add(key + " in " + earlier + " and " + file);
                }
            }
        }

        assertThat(twice).isEmpty();
    }

    /** A join over a slow link waits for every byte the host ships, and seven in ten were comments. */
    @Test
    void shipsToPeersWithoutItsCommentsAsTheSameConfig() throws IOException {
        String files = shipped();

        String shipped = SimConfigLoader.withoutComments(files);

        assertThat(SimConfigLoader.parse(shipped)).isEqualTo(SimConfigLoader.parse(files));
        assertThat(shipped.lines()).noneMatch(line -> line.startsWith("#") || line.isBlank());
        assertThat(shipped.length()).isLessThan(files.length() / 3);
    }

    /** Every shipped file, in the order the loader reads them. */
    private static String shipped() throws IOException {
        StringBuilder text = new StringBuilder();
        for (String file : SimConfig.shippedFiles()) {
            text.append(read(file)).append('\n');
        }
        return text.toString();
    }

    private static String read(String file) throws IOException {
        return Files.readString(SHIPPED.resolve(file), StandardCharsets.ISO_8859_1);
    }
}
