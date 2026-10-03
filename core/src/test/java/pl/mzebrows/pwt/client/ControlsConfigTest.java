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

    /** The pad's order buttons are where the draft expects them: right, left and down on the d-pad. */
    @Test
    void findsTheDraftsDirectionsWhereThePadsOrdersAre() {
        ControlsConfig.Pad pad = ControlsConfig.load().pad();

        assertThat(pad.orderForward()).isEqualTo(GamepadButton.DPAD_RIGHT);
        assertThat(pad.orderCancel()).isEqualTo(GamepadButton.DPAD_LEFT);
        assertThat(pad.orderDefence()).isEqualTo(GamepadButton.DPAD_DOWN);
        assertThat(pad.draft()).isEqualTo(GamepadButton.NORTH);
    }

    /** Shutting a crate on the keyboard is a key nothing else in a level claims. */
    @Test
    void shutsACrateOnAKeyOfItsOwn() {
        ControlsConfig.Keys keys = ControlsConfig.load().key();

        assertThat(keys.crateClose()).isEqualTo(new KeyCode(Input.Keys.X));
        assertThat(keys.crateClose())
                .isNotIn(
                        keys.action(),
                        keys.grenade(),
                        keys.drag(),
                        keys.zoom(),
                        keys.draft(),
                        keys.orderFollowMe(),
                        keys.orderForward(),
                        keys.orderDefence(),
                        keys.orderCancel());
    }
}
