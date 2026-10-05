package pl.mzebrows.pwt.ui.page;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MenuTreeTest {

    @Test
    void offersResumeRestartAndLeaveOnThePauseMenuInThatOrder() {
        assertThat(MenuTree.page(UiPage.PAUSE).entries())
                .extracting(MenuEntry::id)
                .containsExactly("resume", "restart", "leave");
    }

    /** RETRY selected: the next attempt is one press away (G0066). */
    @Test
    void offersRetryThenMenuOnTheResultWithRetrySelected() {
        MenuPage page = MenuTree.page(UiPage.RESULT);

        assertThat(page.entries()).extracting(MenuEntry::id).containsExactly("retry", "menu");
        assertThat(page.entries().get(page.defaultSelection()).intent()).isEqualTo(UiIntent.RESTART);
    }

    @Test
    void selectsTheHarmlessAnswerFirstOnEveryConfirm() {
        for (UiPage confirm : new UiPage[] {UiPage.EXIT_CONFIRM, UiPage.LEAVE_CONFIRM}) {
            MenuPage page = MenuTree.page(confirm);

            assertThat(page.entries().get(page.defaultSelection()).intent()).isEqualTo(UiIntent.BACK);
        }
    }

    @Test
    void opensOnlyPagesThatBelongUnderTheOneTheyAreOn() {
        for (UiPage page : UiPage.values()) {
            if (!MenuTree.hasFixedEntries(page)) {
                continue;
            }
            for (MenuEntry entry : MenuTree.page(page).entries()) {
                if (entry.intent().kind() == UiIntent.Kind.OPEN) {
                    assertThat(entry.intent().page().parent()).as(entry.id()).isEqualTo(page);
                }
            }
        }
    }

    @Test
    void offersRestartOnlyWhereTheFlowAllowsIt() {
        MenuEntry restart = MenuTree.page(UiPage.PAUSE).entries().get(1);
        UiFlow peer = new UiFlow(UiPage.PLAY);
        peer.observe(false, false);

        assertThat(MenuTree.enabled(restart, peer)).isFalse();
    }

    /** The setup's rows, then the way to its upgrades, then START - selected, so two confirms fight it. */
    @Test
    void offersTheProvingGroundsRowsThenItsUpgradesThenStart() {
        MenuPage page = MenuTree.page(UiPage.PROVING_GROUND);

        assertThat(page.entries())
                .extracting(MenuEntry::id)
                .containsExactly("strain", "count", "gun", "squad", "others", "upgrades", "start", "back");
        assertThat(page.entries().get(page.defaultSelection()).intent()).isEqualTo(UiIntent.PROVE);
        // A choice row's index is its row's place in ProvingRows.Row, so the screen can find it again.
        ProvingRows.Row[] rows = ProvingRows.Row.values();
        for (int i = 0; i < rows.length; i++) {
            assertThat(page.entries().get(i).intent()).isEqualTo(UiIntent.choose(i));
            assertThat(page.entries().get(i).id()).isEqualTo(rows[i].name().toLowerCase(java.util.Locale.ROOT));
        }
    }
}
