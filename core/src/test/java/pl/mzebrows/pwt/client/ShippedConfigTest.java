package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.def.Defs;

/**
 * Every simulation test runs on {@link SimConfig#defaults()}; the game runs on {@code game.properties}.
 * Where the two differ, the tests are proving things about a game nobody plays - 21 numbers had drifted
 * apart before this test existed. The shipped file is the truth: a number tuned there is copied into
 * the defaults in the same change.
 *
 * <p>The seed is left out: it names a run rather than tuning one, and tests pick their own.
 */
class ShippedConfigTest {

    private static final Path SHIPPED = Path.of("..", "assets", "game.properties");

    @Test
    void theSimulationDefaultsAreTheShippedNumbers() throws IOException, ReflectiveOperationException {
        SimConfig defaults = SimConfig.defaults();
        SimConfig shipped = SimConfigLoader.parse(Files.readString(SHIPPED)).withSeed(defaults.seed());

        List<String> drift = new ArrayList<>();
        differences("sim", defaults, shipped, drift);

        assertThat(drift).as("defaults -> game.properties").isEmpty();
    }

    /** Every leaf that differs, as {@code path: default -> shipped}, walking records and kind tables. */
    private static void differences(String path, @Nullable Object defaults, @Nullable Object shipped, List<String> out)
            throws IllegalAccessException, InvocationTargetException {
        if (defaults != null && shipped != null && defaults.getClass().isRecord()) {
            for (RecordComponent component : defaults.getClass().getRecordComponents()) {
                differences(
                        path + "." + component.getName(),
                        component.getAccessor().invoke(defaults),
                        component.getAccessor().invoke(shipped),
                        out);
            }
        } else if (defaults instanceof Defs<?, ?> table && shipped instanceof Defs<?, ?> other) {
            for (int i = 0; i < table.values().size(); i++) {
                differences(
                        path + "[" + i + "]",
                        table.values().get(i),
                        other.values().get(i),
                        out);
            }
        } else if (!Objects.equals(defaults, shipped)) {
            out.add(path + ": " + defaults + " -> " + shipped);
        }
    }
}
