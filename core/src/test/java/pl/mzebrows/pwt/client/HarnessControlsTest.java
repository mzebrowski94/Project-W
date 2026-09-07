package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.gdx.Input;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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

    /**
     * Escape together with anything else means leave, not do-the-other-thing-and-then-leave. The
     * order is a promise the session relies on, so it is pinned here rather than left to the caller.
     */
    @Test
    void putsLeaveFirstWhenItIsPressedAlongsideSomethingElse() {
        assertThat(HarnessControls.poll(pressed(Input.Keys.R, Input.Keys.ESCAPE)))
                .startsWith(HarnessCommand.LEAVE);
    }

    @Test
    void ignoresKeysThatAreNotHarnessControls() {
        assertThat(HarnessControls.poll(pressed(Input.Keys.A, Input.Keys.SPACE, Input.Keys.SHIFT_LEFT)))
                .isEmpty();
    }

    private static Stream<Arguments> bindings() {
        return Stream.of(
                Arguments.of("escape", Input.Keys.ESCAPE, HarnessCommand.LEAVE),
                Arguments.of("p", Input.Keys.P, HarnessCommand.PAUSE),
                Arguments.of("period", Input.Keys.PERIOD, HarnessCommand.STEP),
                Arguments.of("r", Input.Keys.R, HarnessCommand.RESTART),
                Arguments.of("f1", Input.Keys.F1, HarnessCommand.TOGGLE_OVERLAY));
    }

    private static KeyPresses pressed(int... keycodes) {
        Set<Integer> down = java.util.Arrays.stream(keycodes).boxed().collect(java.util.stream.Collectors.toSet());
        return down::contains;
    }
}
