package pl.mzebrows.pwt.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** A level's time as the result screen writes it, {@code m:ss.t} (G0066). */
class LevelTimeTest {

    private static final float TICK = 1f / 60f;

    @Test
    void writesTicksAsMinutesSecondsAndTenths() {
        assertThat(LevelTime.text(41_237L, TICK)).isEqualTo("11:27.2");
    }

    @Test
    void cutsTheTenthsRatherThanRoundingThem() {
        assertThat(LevelTime.text(59L, TICK)).isEqualTo("0:00.9");
    }
}
