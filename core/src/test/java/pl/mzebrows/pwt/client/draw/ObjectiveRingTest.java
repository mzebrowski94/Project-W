package pl.mzebrows.pwt.client.draw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.badlogic.gdx.graphics.Color;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class ObjectiveRingTest {

    private static final Offset<Float> TOLERANCE = Offset.offset(1e-4f);

    private static final ObjectiveRing SHIPPED = GraphicConfig.defaults().siteRing();

    @Test
    void leavesAGapAsLongAsAStripeAfterEachStripe() {
        assertThat(SHIPPED.pieceAt(1, 0) - SHIPPED.pieceAt(0, 0)).isEqualTo(2 * SHIPPED.pieces());
        assertThat(SHIPPED.pieceAt(SHIPPED.stripes() - 1, SHIPPED.pieces() - 1) + 1 + SHIPPED.pieces())
                .as("the last gap closes the ring")
                .isEqualTo(SHIPPED.segments());
    }

    @Test
    void runsOutsideTheSitesEdge() {
        assertThat(SHIPPED.ringFor(6f) - SHIPPED.width() * 0.5f).isGreaterThan(6f);
    }

    @Test
    void turnsAsTheClockRunsAndHoldsStillWithNoMotion() {
        float now = SHIPPED.angleAt(0, 0f, 1f);

        assertThat(SHIPPED.angleAt(0, 1f, 1f) - now).isCloseTo(SHIPPED.spinDegrees(), TOLERANCE);
        assertThat(SHIPPED.angleAt(0, 1f, 0f)).isCloseTo(now, TOLERANCE);
    }

    @Test
    void fadesBetweenItsAlphaAndItsDepthAndIsSteadyWithNoMotion() {
        float top = SHIPPED.color().a;
        for (float seconds = 0f; seconds < 3f; seconds += 0.05f) {
            assertThat(SHIPPED.alphaAt(seconds, 1f)).isBetween(top * (1f - SHIPPED.pulseDepth()) - 1e-4f, top + 1e-4f);
        }
        assertThat(SHIPPED.alphaAt(0.3f, 0f)).isEqualTo(top);
    }

    @Test
    void isFaint() {
        assertThat(SHIPPED.color().a)
                .as("see-through, so it informs rather than shouts")
                .isLessThan(0.5f);
    }

    @Test
    void refusesARingWithNoStripes() {
        assertThatThrownBy(() -> new ObjectiveRing(Color.WHITE, 0, 2, 0.5f, 0.5f, 8f, 0.4f, 0.5f))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
