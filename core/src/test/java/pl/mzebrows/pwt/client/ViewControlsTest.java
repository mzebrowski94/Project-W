package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.gdx.Input;
import org.junit.jupiter.api.Test;

class ViewControlsTest {

    @Test
    void swapsTheViewOnTab() {
        assertThat(ViewControls.zoomToggled(keycode -> keycode == Input.Keys.TAB))
                .isTrue();
    }

    @Test
    void leavesTheViewAloneWhenNothingWasPressed() {
        assertThat(ViewControls.zoomToggled(keycode -> false)).isFalse();
    }

    /** The harness keys are dev tools and must not double as game bindings. */
    @Test
    void ignoresTheHarnessKeys() {
        assertThat(ViewControls.zoomToggled(keycode -> keycode == Input.Keys.P
                        || keycode == Input.Keys.R
                        || keycode == Input.Keys.G
                        || keycode == Input.Keys.F1))
                .isFalse();
    }
}
