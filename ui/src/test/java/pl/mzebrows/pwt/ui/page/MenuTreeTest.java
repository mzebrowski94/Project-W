package pl.mzebrows.pwt.ui.page;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.def.TierKind;

class MenuTreeTest {

    @Test
    void offersResumeRestartControlsAndLeaveOnThePauseMenuInThatOrder() {
        assertThat(MenuTree.page(UiPage.PAUSE).entries())
                .extracting(MenuEntry::id)
                .containsExactly("resume", "restart", "controls", "leave");
    }

    /** The bindings are read where the player is stuck on one: the main menu's page, over the pause. */
    @Test
    void offersThePauseControlsTheMainControlsPagesEntries() {
        assertThat(MenuTree.page(UiPage.PAUSE_CONTROLS)).isEqualTo(MenuTree.page(UiPage.CONTROLS));
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

    /** Missions are the loop: the main page opens on them, the Adventures one press down (ticket 08). */
    @Test
    void opensTheMainPageOnDailyMissions() {
        MenuPage page = MenuTree.page(UiPage.MAIN);

        assertThat(page.entries().get(page.defaultSelection()).intent()).isEqualTo(UiIntent.open(UiPage.MISSIONS));
        assertThat(page.defaultSelection()).isZero();
    }

    @Test
    void offersTheMainPagesEntriesInTheSpecificationsOrder() {
        assertThat(MenuTree.page(UiPage.MAIN).entries())
                .extracting(MenuEntry::id)
                .containsExactly("missions", "campaign", "proving-ground", "online", "controls", "options", "exit");
    }

    /** A page that only tells the player something leaves by its one entry. */
    @Test
    void offersOnlyBackOnAPageThatOnlyTells() {
        for (UiPage telling : new UiPage[] {UiPage.CAMPAIGN, UiPage.OPTIONS}) {
            assertThat(MenuTree.page(telling).entries())
                    .as(telling.id())
                    .extracting(MenuEntry::intent)
                    .containsExactly(UiIntent.BACK);
        }
    }

    /** The controls page changes one thing: the PROMPTS switch, first and selected, above BACK. */
    @Test
    void offersThePromptsSwitchSelectedThenBackOnTheControlsPage() {
        MenuPage page = MenuTree.page(UiPage.CONTROLS);

        assertThat(page.entries()).extracting(MenuEntry::id).containsExactly("prompts", "back");
        assertThat(page.entries()).extracting(MenuEntry::intent).containsExactly(UiIntent.choose(0), UiIntent.BACK);
        assertThat(page.defaultSelection()).isZero();
    }

    /** Each entry above START writes into the code line; START MISSION alone starts anything. */
    @Test
    void offersTheThreeDailiesThenRandomThenACodeThenStartWithMediumSelected() {
        for (UiPage missions : new UiPage[] {UiPage.MISSIONS, UiPage.HOST_MISSIONS}) {
            MenuPage page = MenuTree.page(missions);

            assertThat(page.entries())
                    .extracting(MenuEntry::id)
                    .containsExactly("easy", "medium", "hard", "random", "code", "start", "back");
            assertThat(page.entries().get(page.defaultSelection()).id()).isEqualTo("medium");
            TierKind[] tiers = TierKind.values();
            for (int i = 0; i < tiers.length; i++) {
                assertThat(page.entries().get(i).intent()).isEqualTo(UiIntent.mission(i));
            }
            assertThat(page.entries().get(MenuTree.MISSION_RANDOM).id()).isEqualTo("random");
            assertThat(page.entries().get(MenuTree.MISSION_CODE).id()).isEqualTo("code");
            assertThat(page.entries().get(MenuTree.MISSION_START).intent())
                    .isEqualTo(UiIntent.mission(MenuTree.MISSION_START));
        }
    }

    /** COPY CODE is a mission's alone; RETRY stays selected either way (G0066). */
    @Test
    void offersCopyCodeOnAMissionsResultOnly() {
        MenuPage mission = MenuTree.result(true);

        assertThat(mission.entries()).extracting(MenuEntry::id).containsExactly("retry", "copy", "menu");
        assertThat(mission.entries().get(mission.defaultSelection()).intent()).isEqualTo(UiIntent.RESTART);
        assertThat(MenuTree.result(false)).isEqualTo(MenuTree.page(UiPage.RESULT));
    }
}
