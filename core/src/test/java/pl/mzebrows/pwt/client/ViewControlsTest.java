package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.gdx.Input;
import org.junit.jupiter.api.Test;

class ViewControlsTest {

    private static final ControlsConfig.Keys BINDINGS = ControlsConfig.load().key();

    @Test
    void swapsTheViewOnTab() {
        assertThat(zoomToggled(keycode -> keycode == Input.Keys.TAB)).isTrue();
    }

    @Test
    void swapsTheBottomBarsViewOnTheBacktick() {
        assertThat(ViewControls.rollCallToggled(keycode -> keycode == Input.Keys.GRAVE, BINDINGS))
                .isTrue();
        assertThat(ViewControls.rollCallToggled(keycode -> keycode == Input.Keys.TAB, BINDINGS))
                .isFalse();
    }

    @Test
    void leavesTheViewAloneWhenNothingWasPressed() {
        assertThat(zoomToggled(keycode -> false)).isFalse();
    }

    private static boolean zoomToggled(KeyPresses keys) {
        return ViewControls.zoomToggled(keys, BINDINGS);
    }

    /** The harness keys are dev tools and must not double as game bindings. */
    @Test
    void ignoresTheHarnessKeys() {
        assertThat(zoomToggled(keycode -> keycode == Input.Keys.P
                        || keycode == Input.Keys.R
                        || keycode == Input.Keys.G
                        || keycode == Input.Keys.F1))
                .isFalse();
    }
}
