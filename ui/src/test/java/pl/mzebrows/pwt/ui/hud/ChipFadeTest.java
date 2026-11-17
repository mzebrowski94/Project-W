package pl.mzebrows.pwt.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/** A prompt's backing thins while an alien body is under it, so the fight shows through (G0098). */
class ChipFadeTest {

    private static final Offset<Float> CLOSE = Offset.offset(1e-4f);

    private final ChipFade fade = new ChipFade(0.6f, 0.25f, 0.15f);

    @Test
    void startsAtItsOwnAlpha() {
        assertThat(fade.alpha(0)).isEqualTo(0.6f);
    }

    @Test
    void easesToTheOccludedAlphaOverItsSecondsAndBack() {
        fade.step(0, true, 0.075f);
        assertThat(fade.alpha(0)).isCloseTo(0.425f, CLOSE);

        fade.step(0, true, 1f);
        assertThat(fade.alpha(0)).isCloseTo(0.25f, CLOSE);

        fade.step(0, false, 0.15f);
        assertThat(fade.alpha(0)).isCloseTo(0.6f, CLOSE);
    }

    @Test
    void eachSlotFadesOnItsOwn() {
        fade.step(1, true, 1f);

        assertThat(fade.alpha(0)).isEqualTo(0.6f);
        assertThat(fade.alpha(1)).isCloseTo(0.25f, CLOSE);
    }

    @Test
    void noSecondsIsAnInstantChange() {
        ChipFade instant = new ChipFade(0.6f, 0.25f, 0f);

        instant.step(0, true, 0.001f);

        assertThat(instant.alpha(0)).isCloseTo(0.25f, CLOSE);
    }
}
