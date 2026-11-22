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
    void stripesItsTwoColoursByTurns() {
        assertThat(SHIPPED.colorOf(0)).isEqualTo(SHIPPED.first());
        assertThat(SHIPPED.colorOf(1)).isEqualTo(SHIPPED.second());
        assertThat(SHIPPED.colorOf(SHIPPED.stripes() - 1))
                .as("ends on the second, so it meets the first")
                .isEqualTo(SHIPPED.second());
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
    void shinesBetweenFullAndItsDepthAndIsSteadyWithNoMotion() {
        for (float seconds = 0f; seconds < 3f; seconds += 0.05f) {
            assertThat(SHIPPED.brightnessAt(seconds, 1f)).isBetween(1f - SHIPPED.pulseDepth() - 1e-4f, 1f + 1e-4f);
        }
        assertThat(SHIPPED.brightnessAt(0.3f, 0f)).isEqualTo(1f);
    }

    @Test
    void refusesStripesThatCannotAlternateAllTheWayRound() {
        assertThatThrownBy(() -> new ObjectiveRing(Color.WHITE, Color.YELLOW, 5, 2, 0.5f, 0.5f, 30f, 1f, 0.3f))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
