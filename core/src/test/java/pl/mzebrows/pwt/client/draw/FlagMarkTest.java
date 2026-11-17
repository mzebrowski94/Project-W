package pl.mzebrows.pwt.client.draw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.badlogic.gdx.graphics.Color;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.def.Look;

/** A taken control point flies a flag that waves on the client's clock, pinned at its pole (pillar 5). */
class FlagMarkTest {

    private static final float RADIUS = 6f;

    private static final Offset<Float> CLOSE = Offset.offset(1e-5f);

    @Test
    void fliesOnlyOverAControlPointThatIsFull() {
        assertThat(FlagMark.flies(appearance(Look.CONTROL_POINT, 1f))).isTrue();
        assertThat(FlagMark.flies(appearance(Look.CONTROL_POINT, 0.99f)))
                .as("a point being taken")
                .isFalse();
        assertThat(FlagMark.flies(appearance(Look.CONTROL_POINT, 0f))).isFalse();
        assertThat(FlagMark.flies(appearance(Look.POST, 1f)))
                .as("a post is unchanged")
                .isFalse();
        assertThat(FlagMark.flies(appearance(Look.TROOPER, 1f))).isFalse();
    }

    @Test
    void wavesTheFreeEndWhilePinningTheEdgeAtThePole() {
        FlagMark flag = flag();

        float pinned = flag.liftAt(0, RADIUS, 0.3f, 1f);
        float free = maxLift(flag, flag.slices(), 1f);

        assertThat(pinned).isCloseTo(0f, CLOSE);
        assertThat(free).isCloseTo(flag.heightFor(RADIUS) * flag.sway(), Offset.offset(0.01f));
    }

    @Test
    void holdsTheFlagStillWithMotionOff() {
        FlagMark flag = flag();

        for (int at = 0; at <= flag.slices(); at++) {
            assertThat(flag.liftAt(at, RADIUS, 0.37f, 0f)).as("edge %d", at).isCloseTo(0f, CLOSE);
        }
    }

    @Test
    void scalesTheWaveByTheMotionScale() {
        FlagMark flag = flag();

        assertThat(flag.liftAt(flag.slices(), RADIUS, 0.1f, 0.5f))
                .isCloseTo(flag.liftAt(flag.slices(), RADIUS, 0.1f, 1f) * 0.5f, CLOSE);
    }

    @Test
    void movesTheFlagAsTimeRunsOn() {
        FlagMark flag = flag();

        assertThat(flag.liftAt(flag.slices(), RADIUS, 0.1f, 1f))
                .isNotCloseTo(flag.liftAt(flag.slices(), RADIUS, 0.3f, 1f), Offset.offset(0.01f));
    }

    @Test
    void runsTheRingInsideThePointsEdge() {
        FlagMark flag = flag();

        float outer = flag.ringFor(RADIUS) + flag.edgeFor(RADIUS) * 0.5f;

        assertThat(outer).isCloseTo(RADIUS, CLOSE);
    }

    @Test
    void refusesAFlagHangingPastItsPolesFootOrWavingFasterThanAPulseMay() {
        assertThatThrownBy(() -> flag(0.5f, 0.6f, 1f)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> flag(0.5f, 0.2f, GraphicConfig.MAX_PULSE_HZ + 1f))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shipsAFlagThatReadsAsThePlayersSideRatherThanTheAliens() {
        FlagMark shipped = GraphicConfig.defaults().flag();

        assertThat(shipped.color().b).as("blue").isGreaterThan(shipped.color().r);
    }

    private static FlagMark flag() {
        return flag(0.5f, 0.2f, 1f);
    }

    private static FlagMark flag(float poleHeight, float height, float waveHz) {
        return new FlagMark(
                Color.SKY, Color.LIGHT_GRAY, poleHeight, 0.05f, 0.35f, height, 8, waveHz, 1f, 0.25f, 0.08f, 40);
    }

    private static Appearance appearance(Look look, float charge) {
        Appearance appearance = new Appearance().set(look);
        appearance.charge = charge;
        return appearance;
    }

    /** The highest the edge rises over one whole wave, sampled finely. */
    private static float maxLift(FlagMark flag, int at, float motionScale) {
        float most = 0f;
        for (int step = 0; step < 1000; step++) {
            most = Math.max(most, flag.liftAt(at, RADIUS, step / 1000f / flag.waveHz(), motionScale));
        }
        return most;
    }
}
