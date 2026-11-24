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

    /** The controls page changes two things: the PROMPTS switch, first and selected, then PLAYER 1, above BACK. */
    @Test
    void offersThePromptsSwitchSelectedThenPlayerOneThenBackOnTheControlsPage() {
        MenuPage page = MenuTree.page(UiPage.CONTROLS);

        assertThat(page.entries()).extracting(MenuEntry::id).containsExactly("prompts", "playerOne", "back");
        assertThat(page.entries())
                .extracting(MenuEntry::intent)
                .containsExactly(
                        UiIntent.choose(MenuTree.CONTROLS_PROMPTS),
                        UiIntent.choose(MenuTree.CONTROLS_PLAYER_ONE),
                        UiIntent.BACK);
        assertThat(page.defaultSelection()).isZero();
    }

    /** A level seats player one as it starts, so over a running one PLAYER 1 is shown and locked; PROMPTS is not. */
    @Test
    void locksPlayerOneOverARunningLevelButNotOnTheMainMenu() {
        MenuEntry prompts = MenuTree.page(UiPage.CONTROLS).entries().get(MenuTree.CONTROLS_PROMPTS);
        MenuEntry playerOne = MenuTree.page(UiPage.CONTROLS).entries().get(MenuTree.CONTROLS_PLAYER_ONE);
        UiFlow menu = new UiFlow(UiPage.MAIN);
        menu.apply(UiIntent.open(UiPage.CONTROLS));
        UiFlow level = new UiFlow(UiPage.PLAY);
        level.apply(UiIntent.PAUSE);
        level.apply(UiIntent.open(UiPage.PAUSE_CONTROLS));

        assertThat(level.top()).isEqualTo(UiPage.PAUSE_CONTROLS);
        assertThat(MenuTree.enabled(playerOne, menu)).isTrue();
        assertThat(MenuTree.enabled(playerOne, level)).isFalse();
        assertThat(MenuTree.enabled(prompts, level)).isTrue();
    }

    /** Each entry above START writes into the code line; START MISSION alone starts anything. */
    @Test
    void offersTheThreeDailiesThenRandomThenItsLengthThenACodeThenStartWithMediumSelected() {
        for (UiPage missions : new UiPage[] {UiPage.MISSIONS, UiPage.BRIEFING}) {
            MenuPage page = MenuTree.page(missions);

            assertThat(page.entries())
                    .extracting(MenuEntry::id)
                    .containsExactly(
                            "easy", "medium", "hard", "random", "custom", "length", "health", "count", "code", "start",
                            "back");
            assertThat(page.entries().get(page.defaultSelection()).id()).isEqualTo("medium");
            TierKind[] tiers = TierKind.values();
            for (int i = 0; i < tiers.length; i++) {
                assertThat(page.entries().get(i).intent()).isEqualTo(UiIntent.mission(i));
            }
            assertThat(page.entries().get(MenuTree.MISSION_RANDOM).id()).isEqualTo("random");
            assertThat(page.entries().get(MenuTree.MISSION_LENGTH).id()).isEqualTo("length");
            assertThat(page.entries().get(MenuTree.MISSION_LENGTH).intent())
                    .isEqualTo(UiIntent.mission(MenuTree.MISSION_LENGTH));
            assertThat(page.entries().get(MenuTree.MISSION_HEALTH).id()).isEqualTo("health");
            assertThat(page.entries().get(MenuTree.MISSION_COUNT).intent())
                    .isEqualTo(UiIntent.mission(MenuTree.MISSION_COUNT));
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
