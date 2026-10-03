package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.gdx.Input;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pl.mzebrows.pwt.client.input.KeyPresses;
import pl.mzebrows.pwt.client.play.HarnessCommand;

class HarnessControlsTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("bindings")
    void mapsEachHarnessKeyToItsCommand(String key, int keycode, HarnessCommand expected) {
        assertThat(HarnessControls.poll(pressed(keycode)))
                .as("%s should mean %s", key, expected)
                .containsExactly(expected);
    }

    @Test
    void producesNothingWhenNoHarnessKeyIsPressed() {
        assertThat(HarnessControls.poll(pressed())).isEmpty();
    }

    @Test
    void reportsEveryHarnessKeyPressedInTheSameFrame() {
        assertThat(HarnessControls.poll(pressed(Input.Keys.P, Input.Keys.F1)))
                .containsExactlyInAnyOrder(HarnessCommand.PAUSE, HarnessCommand.TOGGLE_OVERLAY);
    }

    /** Esc opens the pause menu now, which is the game's; the harness must not also leave on it. */
    @Test
    void leavesEscapeToThePauseMenu() {
        assertThat(HarnessControls.poll(pressed(Input.Keys.ESCAPE))).isEmpty();
    }

    @Test
    void ignoresKeysThatAreNotHarnessControls() {
        assertThat(HarnessControls.poll(pressed(Input.Keys.A, Input.Keys.SPACE, Input.Keys.SHIFT_LEFT)))
                .isEmpty();
    }

    private static Stream<Arguments> bindings() {
        return Stream.of(
                Arguments.of("p", Input.Keys.P, HarnessCommand.PAUSE),
                Arguments.of("period", Input.Keys.PERIOD, HarnessCommand.STEP),
                Arguments.of("r", Input.Keys.R, HarnessCommand.RESTART),
                Arguments.of("f1", Input.Keys.F1, HarnessCommand.TOGGLE_OVERLAY),
                Arguments.of("g", Input.Keys.G, HarnessCommand.TOGGLE_GOD_MODE),
                Arguments.of("f5", Input.Keys.F5, HarnessCommand.TAKE_FIRST_CARD),
                Arguments.of("f6", Input.Keys.F6, HarnessCommand.TAKE_SECOND_CARD),
                Arguments.of("f7", Input.Keys.F7, HarnessCommand.TAKE_THIRD_CARD));
    }

    private static KeyPresses pressed(int... keycodes) {
        Set<Integer> down = java.util.Arrays.stream(keycodes).boxed().collect(java.util.stream.Collectors.toSet());
        return down::contains;
    }
}
