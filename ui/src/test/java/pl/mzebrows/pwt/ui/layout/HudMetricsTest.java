package pl.mzebrows.pwt.ui.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class HudMetricsTest {

    @Test
    void sizesEachBarFromWhatItHolds() {
        HudMetrics metrics = new HudMetrics(4, 8, 2, 4, 3, 32);

        assertThat(metrics.topMin()).isEqualTo(46);
        assertThat(metrics.bottomMin()).isEqualTo(40);
    }

    @Test
    void growsTheBottomBarForItsTextWhenTheTextOutgrowsThePortrait() {
        HudMetrics metrics = new HudMetrics(4, 12, 2, 4, 3, 32);

        assertThat(metrics.bottomMin()).isEqualTo(2 * 4 + 3 * 12 + 2 * 2);
    }

    @Test
    void growsTheTopBarWithEveryLineItHolds() {
        HudMetrics two = new HudMetrics(4, 8, 2, 4, 3, 32);
        HudMetrics three = new HudMetrics(4, 8, 2, 5, 3, 32);

        assertThat(three.topMin() - two.topMin()).isEqualTo(10);
    }

    @Test
    void refusesAFontWithNoHeight() {
        assertThatThrownBy(() -> new HudMetrics(4, 0, 2, 4, 3, 32)).isInstanceOf(IllegalArgumentException.class);
    }
}
