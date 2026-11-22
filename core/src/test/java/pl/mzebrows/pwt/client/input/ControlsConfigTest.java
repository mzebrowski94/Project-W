package pl.mzebrows.pwt.client.input;

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
                        keys.dash(),
                        keys.dashAlias(),
                        keys.zoom(),
                        keys.draft(),
                        keys.orderFollowMe(),
                        keys.orderForward(),
                        keys.orderDefence(),
                        keys.orderCancel());
    }

    /** libGDX names the left Shift key {@code L-Shift}, not {@code Shift}: the file has to say what it calls it. */
    @Test
    void dashesOnSpaceOrTheLeftShift() {
        ControlsConfig.Keys keys = ControlsConfig.defaults().key();

        assertThat(keys.dash()).isEqualTo(new KeyCode(Input.Keys.SPACE));
        assertThat(keys.dashAlias()).isEqualTo(new KeyCode(Input.Keys.SHIFT_LEFT));
        assertThat(ControlsConfig.FILE.read("").problems()).isEmpty();
    }

    /** The pad's B is the dash's (G0107), so taking hold of a load is off the pad for now. */
    @Test
    void dashesOnThePadsEastAndLeavesDragOffThePad() {
        ControlsConfig.Pad pad = ControlsConfig.defaults().pad();

        assertThat(pad.dash()).isEqualTo(GamepadButton.EAST);
        assertThat(pad.drag()).isEqualTo(GamepadButton.NONE);
        assertThat(pad.dragTrigger()).isEqualTo(GamepadAxis.NONE);
    }

    /**
     * Redeploy is the drag's key and the dash's button (G0109), in a context of their own: down, he can neither take
     * hold of a load nor dash, so the shared binding never means two things at once.
     */
    @Test
    void redeploysOnTheDragsKeyAndTheDashsButton() {
        ControlsConfig shipped = ControlsConfig.defaults();

        assertThat(shipped.key().redeploy()).isEqualTo(new KeyCode(Input.Keys.F));
        assertThat(shipped.key().redeploy()).isEqualTo(shipped.key().drag());
        assertThat(shipped.pad().redeploy()).isEqualTo(GamepadButton.EAST);
        assertThat(shipped.pad().redeploy()).isEqualTo(shipped.pad().dash());
        assertThat(shipped.pad().redeployTrigger()).isEqualTo(GamepadAxis.NONE);
    }

    @Test
    void drivesPagesWithEnterEscapeAndThePadsSouthAndEast() {
        ControlsConfig.Ui ui = ControlsConfig.load().ui();

        assertThat(ui.key().confirm()).isEqualTo(new KeyCode(Input.Keys.ENTER));
        assertThat(ui.key().back()).isEqualTo(new KeyCode(Input.Keys.ESCAPE));
        assertThat(ui.pad().confirm()).isEqualTo(GamepadButton.SOUTH);
        assertThat(ui.pad().back()).isEqualTo(GamepadButton.EAST);
        assertThat(ui.nav().repeatDelaySeconds()).isGreaterThan(ui.nav().repeatIntervalSeconds());
    }
}
