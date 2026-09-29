package pl.mzebrows.pwt.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The draft's arithmetic in the top bar (D18): the chooser on the left, the cards across the rest. */
class DraftLayoutTest {

    private static final DraftLayout LAYOUT = new DraftLayout(4, 32, 6);

    @Test
    void leavesTheChooserRoomForHisPortraitAndLabel() {
        assertThat(LAYOUT.chooserWidth()).isEqualTo(4 + 32 + 4 + 4 * 6 + 4);
    }

    @Test
    void laysTheCardsSideBySideWithoutOverlapOrOverflow() {
        int span = 640;

        for (int index = 0; index < 3; index++) {
            int right = LAYOUT.cardX(span, 3, index) + LAYOUT.cardWidth(span, 3);
            assertThat(right).isLessThanOrEqualTo(span);
            if (index > 0) {
                assertThat(LAYOUT.cardX(span, 3, index))
                        .isEqualTo(LAYOUT.cardX(span, 3, index - 1) + LAYOUT.cardWidth(span, 3));
            }
        }
    }

    /** "WEAPON HANDLING III" is 19 characters: a card holds it on the narrowest canvas. */
    @Test
    void holdsTheLongestNameWithItsRankOnEveryCanvas() {
        assertThat(LAYOUT.cardColumns(512, 3)).isGreaterThanOrEqualTo(19);
        assertThat(LAYOUT.cardColumns(640, 3)).isGreaterThanOrEqualTo(26);
    }

    @Test
    void givesOneCardTheWholeSpan() {
        assertThat(LAYOUT.cardWidth(640, 1)).isEqualTo(640 - LAYOUT.chooserWidth() - 4);
        assertThat(LAYOUT.cardWidth(640, 0)).isZero();
    }
}
