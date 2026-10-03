package pl.mzebrows.pwt.client.draw;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.PreviousPosition;

/** An entity is drawn between where the last tick found it and where it is, by how far the frame has got. */
class DrawnTest {

    @Test
    void drawsAnEntityWhereTheFrameHasGotToBetweenItsTwoPlaces() {
        Position at = new Position().set(10f, 20f);
        PreviousPosition was = new PreviousPosition();
        was.x = 6f;
        was.y = 24f;

        assertThat(Drawn.x(at, was, 0f)).isEqualTo(6f);
        assertThat(Drawn.x(at, was, 0.25f)).isEqualTo(7f);
        assertThat(Drawn.y(at, was, 0.25f)).isEqualTo(23f);
        assertThat(Drawn.y(at, was, 1f)).isEqualTo(20f);
    }
}
