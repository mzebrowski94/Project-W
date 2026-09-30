package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.gdx.Input;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.ConfigBinder;

/**
 * {@code controls.properties} is the whole of {@link ControlsFile}: every binding the records have and
 * nothing else, so a misspelt key is a failure rather than a binding that quietly stays what it was.
 */
class ShippedControlsConfigTest {

    private static final Path SHIPPED = Path.of("..", "assets", "controls.properties");

    @Test
    void theShippedFileNamesEveryBindingAndNothingElse() throws IOException {
        assertThat(ControlsConfig.read(shipped()).problems()).isEmpty();
    }

    /** Proves the compiled-in copy is this file rather than an older build of it. */
    @Test
    void isWhatTheControlsDefaultTo() throws IOException {
        assertThat(ConfigBinder.parse(ShippedControlsConfig.TEXT)).isEqualTo(ConfigBinder.parse(shipped()));
    }

    @Test
    void namesAMisspeltKeyAndKeepsTheShippedBinding() throws IOException {
        String misspelt = shipped().replace("controls.key.grenade=", "controls.key.grenades=");

        ControlsConfig.Parsed parsed = ControlsConfig.read(misspelt);

        assertThat(parsed.problems())
                .containsExactlyInAnyOrder("missing controls.key.grenade", "unknown key controls.key.grenades");
        assertThat(parsed.config().keyboard().grenade())
                .isEqualTo(ControlsConfig.defaults().keyboard().grenade());
    }

    @Test
    void namesAKeyThatIsNoneAndKeepsTheShippedOne() throws IOException {
        String garbled = shipped().replace("controls.key.drag=F", "controls.key.drag=Eff");

        ControlsConfig.Parsed parsed = ControlsConfig.read(garbled);

        assertThat(parsed.problems()).containsExactly("controls.key.drag='Eff' names no key");
        assertThat(parsed.config().keyboard().drag()).isEqualTo(Input.Keys.F);
    }

    private static String shipped() throws IOException {
        return Files.readString(SHIPPED, StandardCharsets.ISO_8859_1);
    }
}
