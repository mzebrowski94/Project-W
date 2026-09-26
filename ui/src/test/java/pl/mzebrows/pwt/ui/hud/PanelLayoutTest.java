package pl.mzebrows.pwt.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PanelLayoutTest {

    private static final PanelLayout LAYOUT = new PanelLayout(4, 32, 6);

    @Test
    void fitsFourPlayersInQuartersOf160() {
        for (int seat = 0; seat < PanelLayout.QUARTERS; seat++) {
            int width = LAYOUT.quarterWidth(seat, 640);

            assertThat(width).isEqualTo(160);
            assertThat(LAYOUT.showsPortrait(width)).isTrue();
            assertThat(LAYOUT.textColumns(width)).isGreaterThanOrEqualTo(PanelLayout.TEXT_COLUMNS);
        }
    }

    @Test
    void dropsOnlyThePortraitOnANarrowCanvas() {
        int width = LAYOUT.quarterWidth(0, 512);

        assertThat(width).isEqualTo(128);
        assertThat(LAYOUT.showsPortrait(width)).isFalse();
        assertThat(LAYOUT.textColumns(width)).isGreaterThanOrEqualTo(PanelLayout.TEXT_COLUMNS);
    }

    @Test
    void tilesTheViewsSpanWithoutAGapOnAnOddWidth() {
        int left = 99;
        int span = 655;

        for (int seat = 0; seat < PanelLayout.QUARTERS - 1; seat++) {
            assertThat(LAYOUT.quarterX(seat, left, span) + LAYOUT.quarterWidth(seat, span))
                    .isEqualTo(LAYOUT.quarterX(seat + 1, left, span));
        }
        int last = PanelLayout.QUARTERS - 1;
        assertThat(LAYOUT.quarterX(last, left, span) + LAYOUT.quarterWidth(last, span))
                .isEqualTo(left + span);
    }
}
