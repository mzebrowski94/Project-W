package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.gdx.Input;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.ConfigFile;

/** A key and a mouse button are read by the names a player writes; a name that is none keeps the shipped one. */
class ControlsConfigTest {

    @Test
    void readsKeysAndMouseButtonsByName() {
        ControlsConfig shipped = ControlsConfig.defaults();

        assertThat(shipped.key().drag()).isEqualTo(new KeyCode(Input.Keys.F));
        assertThat(shipped.mouse().fire()).isEqualTo(new MouseButton(Input.Buttons.LEFT));
    }

    @Test
    void namesAKeyThatIsNoneAndKeepsTheShippedOne() {
        ConfigFile.Parsed<ControlsConfig> parsed = ControlsConfig.FILE.read("controls.key.drag=Eff\n");

        assertThat(parsed.problems()).contains("cannot read controls.key.drag=Eff as KeyCode ('Eff' names no key)");
        assertThat(parsed.config().key().drag()).isEqualTo(new KeyCode(Input.Keys.F));
    }

    @Test
    void namesAMouseButtonThatIsNoneAndKeepsTheShippedOne() {
        ConfigFile.Parsed<ControlsConfig> parsed = ControlsConfig.FILE.read("controls.mouse.strike=thumb\n");

        assertThat(parsed.problems())
                .contains("cannot read controls.mouse.strike=thumb as MouseButton ('thumb' names no mouse button)");
        assertThat(parsed.config().mouse().strike())
                .isEqualTo(ControlsConfig.defaults().mouse().strike());
    }
}
