package pl.mzebrows.pwt.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TallyIconTest {

    @Test
    void lightsSomethingInEveryIcon() {
        for (TallyIcon icon : TallyIcon.values()) {
            int lit = 0;
            for (int y = 0; y < TallyIcon.SIZE; y++) {
                for (int x = 0; x < TallyIcon.SIZE; x++) {
                    lit += icon.lit(x, y) ? 1 : 0;
                }
            }
            assertThat(lit).as(icon.name()).isPositive();
        }
    }

    /** A plus, not a cross: the arms are the same width, and nothing is drawn in the corners. */
    @Test
    void drawsThePlusWithEmptyCorners() {
        assertThat(TallyIcon.STABILISED.lit(0, 0)).isFalse();
        assertThat(TallyIcon.STABILISED.lit(TallyIcon.SIZE - 1, TallyIcon.SIZE - 1))
                .isFalse();
        assertThat(TallyIcon.STABILISED.lit(TallyIcon.SIZE / 2, 0)).isTrue();
        assertThat(TallyIcon.STABILISED.lit(0, TallyIcon.SIZE / 2)).isTrue();
    }

    @Test
    void capsACountAtFourCharacters() {
        assertThat(TallyIcon.count(0)).isEqualTo("0");
        assertThat(TallyIcon.count(999)).isEqualTo("999");
        assertThat(TallyIcon.count(1000)).isEqualTo("999+");
        assertThat(TallyIcon.count(123456)).hasSize(4);
    }

    /** Three icons and three four-character counts fit the panel's text column, as 7.2 promises. */
    @Test
    void fitsTheWholeRowInThePanelsTextColumn() {
        int group = TallyIcon.SIZE + 2 + 4 * 6;
        int row = 3 * group + 2 * 6;

        assertThat(row).isLessThanOrEqualTo(PanelLayout.TEXT_COLUMNS * 6);
    }
}
