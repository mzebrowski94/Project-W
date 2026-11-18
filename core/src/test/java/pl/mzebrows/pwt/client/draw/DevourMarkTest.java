package pl.mzebrows.pwt.client.draw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * A clinch ends in a devour (G0099): the one killed shrinks over his own death throes, and the pair sway side to
 * side across their line while the winner's lock runs, the phase counted from the lock's ticks so every machine agrees.
 */
class DevourMarkTest {

    private static final float TICK = 1f / 60f;

    private static final float RADIUS = 6f;

    private static final Offset<Float> CLOSE = Offset.offset(1e-5f);

    private static final float END_SCALE = 0.35f;

    private static final float SWAY_SHARE = 0.3f;

    private static final float SWAY_HZ = 2.5f;

    /** All of the body's throes still to run. */
    private static final float WHOLE = 1f;

    /** Motion at its ordinary scale. */
    private static final float MOTION = 1f;

    private final DevourMark devour = new DevourMark(END_SCALE, SWAY_SHARE, SWAY_HZ);

    @Test
    void shrinksTheBodyFromWholeToItsEndScaleOverTheThroes() {
        assertThat(devour.scaleFor(1f)).isCloseTo(1f, CLOSE);
        assertThat(devour.scaleFor(0.5f)).isCloseTo((1f + END_SCALE) / 2f, CLOSE);
        assertThat(devour.scaleFor(0f)).isCloseTo(END_SCALE, CLOSE);
    }

    @Test
    void swaysToBothSidesNoFurtherThanItsShareOfTheLosersRadius() {
        float most = 0f;
        float least = 0f;
        for (int ticks = 0; ticks <= 90; ticks++) {
            float sway = devour.swayFor(RADIUS, WHOLE, ticks, 0f, TICK, MOTION);
            assertThat(Math.abs(sway)).as("tick %d", ticks).isLessThanOrEqualTo(SWAY_SHARE * RADIUS + 1e-5f);
            most = Math.max(most, sway);
            least = Math.min(least, sway);
        }

        assertThat(most).isCloseTo(SWAY_SHARE * RADIUS, Offset.offset(0.01f));
        assertThat(least).as("the other side").isCloseTo(-SWAY_SHARE * RADIUS, Offset.offset(0.01f));
    }

    /** The phase runs out with the lock, so the pair come to rest where they stand the moment it ends. */
    @Test
    void comesToRestAsTheLockRunsOut() {
        assertThat(devour.swayFor(RADIUS, WHOLE, 0, 0f, TICK, MOTION)).isCloseTo(0f, CLOSE);
        assertThat(devour.swayFor(RADIUS, WHOLE, 1, 1f, TICK, MOTION)).isCloseTo(0f, CLOSE);
    }

    /**
     * A trooper's throes are shorter than a grunt's lock (G0099): the sway fades with them, so the grunt is left
     * standing still where it stands rather than snapping back when the body is removed.
     */
    @Test
    void fadesOutWithTheBodysThroes() {
        int midSwing = Math.round(1f / (4f * SWAY_HZ) / TICK);
        float full = devour.swayFor(RADIUS, WHOLE, midSwing, 0f, TICK, MOTION);

        assertThat(devour.swayFor(RADIUS, WHOLE / 2f, midSwing, 0f, TICK, MOTION))
                .isCloseTo(full / 2f, CLOSE);
        assertThat(devour.swayFor(RADIUS, 0f, midSwing, 0f, TICK, MOTION)).isCloseTo(0f, CLOSE);
    }

    /** Between two ticks it moves on smoothly, the same frame for frame on every machine. */
    @Test
    void movesOnSmoothlyBetweenTicks() {
        float late = devour.swayFor(RADIUS, WHOLE, 10, 1f, TICK, MOTION);
        float next = devour.swayFor(RADIUS, WHOLE, 9, 0f, TICK, MOTION);

        assertThat(late).isCloseTo(next, CLOSE);
    }

    @Test
    void holdsThePairStillWithMotionOff() {
        for (int ticks = 0; ticks <= 90; ticks++) {
            assertThat(devour.swayFor(RADIUS, WHOLE, ticks, 0.4f, TICK, 0f))
                    .as("tick %d", ticks)
                    .isZero();
        }
    }

    @ParameterizedTest
    @CsvSource({
        "0, 0.3, 2.5", // shrinking to nothing is a pickup's pop (G0078)
        "1.2, 0.3, 2.5", // growing
        "0.35, -0.1, 2.5", // a negative sway
        "0.35, 0.3, 3.5", // more than three swings a second
    })
    void refusesAShrinkToNothingOrAPaceAboveThreeSwingsASecond(float endScale, float swayShare, float swayHz) {
        assertThatThrownBy(() -> new DevourMark(endScale, swayShare, swayHz))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
