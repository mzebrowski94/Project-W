package pl.mzebrows.pwt.ui.page;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.ui.layout.Rect;

class MenuLayoutTest {

    private static final MenuLayout LAYOUT = new MenuLayout(4, 12, 6, 9);
    private static final Rect VIEW = new Rect(0, 41, 640, 266);

    @Test
    void docksThePauseMenuToTheLeftThirdOfTheView() {
        Rect panel = LAYOUT.panel(VIEW, MenuLayout.Anchor.DOCKED_LEFT, 6, 13);

        assertThat(panel.x()).isEqualTo(8);
        assertThat(panel.width()).isEqualTo(640 / 3);
        assertThat(panel.top()).isEqualTo(VIEW.top() - 8);
        assertThat(panel.right()).isLessThan(VIEW.x() + VIEW.width() / 2);
    }

    @Test
    void centresAPanelOnTheMainMenu() {
        Rect panel = LAYOUT.panel(VIEW, MenuLayout.Anchor.CENTRED, 4, 20);

        assertThat(panel.x() + panel.width() / 2).isEqualTo(320);
    }

    @Test
    void findsTheEntryUnderThePointer() {
        Rect panel = LAYOUT.panel(VIEW, MenuLayout.Anchor.DOCKED_LEFT, 6, 13);
        int second = LAYOUT.lineTop(panel, 2 + 1) - 3;

        assertThat(LAYOUT.entryAt(panel, 2, 3, panel.x() + 20, second)).isEqualTo(1);
    }

    @Test
    void findsNothingBesideThePanelOrPastTheLastEntry() {
        Rect panel = LAYOUT.panel(VIEW, MenuLayout.Anchor.DOCKED_LEFT, 6, 13);
        int first = LAYOUT.lineTop(panel, 2) - 3;

        assertThat(LAYOUT.entryAt(panel, 2, 3, panel.right() + 5, first)).isEqualTo(-1);
        assertThat(LAYOUT.entryAt(panel, 2, 3, panel.x() + 20, LAYOUT.lineTop(panel, 6) - 3))
                .isEqualTo(-1);
    }
}
