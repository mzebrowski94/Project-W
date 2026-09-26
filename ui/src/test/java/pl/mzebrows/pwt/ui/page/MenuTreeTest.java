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
}
