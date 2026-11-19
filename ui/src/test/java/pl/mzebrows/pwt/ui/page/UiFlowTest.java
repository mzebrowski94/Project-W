package pl.mzebrows.pwt.ui.page;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UiFlowTest {

    @Test
    void goesBackOnePageAtATime() {
        UiFlow flow = new UiFlow(UiPage.MAIN);
        flow.apply(UiIntent.open(UiPage.ONLINE));
        flow.apply(UiIntent.open(UiPage.ONLINE_HOST));

        flow.apply(UiIntent.BACK);

        assertThat(flow.top()).isEqualTo(UiPage.ONLINE);
        flow.apply(UiIntent.BACK);
        assertThat(flow.top()).isEqualTo(UiPage.MAIN);
    }

    @Test
    void neverQuitsOnBackFromTheMainPage() {
        UiFlow flow = new UiFlow(UiPage.MAIN);

        UiEffect effect = flow.apply(UiIntent.BACK);

        assertThat(effect).isEqualTo(UiEffect.SELECT_EXIT);
        assertThat(flow.top()).isEqualTo(UiPage.MAIN);
    }

    @Test
    void quitsOnlyFromTheExitConfirm() {
        UiFlow flow = new UiFlow(UiPage.MAIN);

        assertThat(flow.apply(UiIntent.QUIT_APP)).isEqualTo(UiEffect.NONE);
        flow.apply(UiIntent.open(UiPage.EXIT_CONFIRM));
        assertThat(flow.apply(UiIntent.QUIT_APP)).isEqualTo(UiEffect.QUIT_APP);
    }

    @Test
    void opensOnlyAPageThatBelongsUnderTheOneOpen() {
        UiFlow flow = new UiFlow(UiPage.MAIN);

        flow.apply(UiIntent.open(UiPage.ONLINE_JOIN));

        assertThat(flow.top()).isEqualTo(UiPage.MAIN);
    }

    @Test
    void opensTheUpgradesOfTheProvingGroundAndComesBackToIt() {
        UiFlow flow = new UiFlow(UiPage.MAIN);
        flow.apply(UiIntent.open(UiPage.PROVING_GROUND));

        flow.apply(UiIntent.open(UiPage.PROVING_UPGRADES));
        assertThat(flow.top()).isEqualTo(UiPage.PROVING_UPGRADES);
        flow.apply(UiIntent.BACK);

        assertThat(flow.top()).isEqualTo(UiPage.PROVING_GROUND);
    }

    /** A choice row's confirm steps it; the screen holds the setup, the flow only says which row. */
    @Test
    void stepsAChoiceOnlyOnTheProvingGroundsPagesAndTheControlsPage() {
        UiFlow flow = new UiFlow(UiPage.MAIN);

        assertThat(flow.apply(UiIntent.choose(2))).isEqualTo(UiEffect.NONE);
        flow.apply(UiIntent.open(UiPage.PROVING_GROUND));
        assertThat(flow.apply(UiIntent.choose(2))).isEqualTo(new UiEffect(UiEffect.Kind.CHOOSE, 2));
        flow.apply(UiIntent.open(UiPage.PROVING_UPGRADES));
        assertThat(flow.apply(UiIntent.choose(5))).isEqualTo(new UiEffect(UiEffect.Kind.CHOOSE, 5));

        UiFlow controls = new UiFlow(UiPage.MAIN);
        controls.apply(UiIntent.open(UiPage.CONTROLS));
        assertThat(controls.apply(UiIntent.choose(0))).isEqualTo(new UiEffect(UiEffect.Kind.CHOOSE, 0));
    }

    @Test
    void startsAMissionOnlyFromTheMissionsPage() {
        UiFlow flow = new UiFlow(UiPage.MAIN);

        assertThat(flow.apply(UiIntent.mission(MenuTree.MISSION_RANDOM))).isEqualTo(UiEffect.NONE);
        flow.apply(UiIntent.open(UiPage.MISSIONS));
        assertThat(flow.apply(UiIntent.mission(MenuTree.MISSION_RANDOM)))
                .isEqualTo(new UiEffect(UiEffect.Kind.MISSION, MenuTree.MISSION_RANDOM));
    }

    /** The host's briefing starts what it starts; a joiner's starts nothing (G0101). */
    @Test
    void startsAMissionFromTheBriefingForTheHostAndForNobodyElse() {
        UiFlow host = briefing(true, false);
        assertThat(host.top()).isEqualTo(UiPage.BRIEFING);
        assertThat(host.apply(UiIntent.mission(1))).isEqualTo(new UiEffect(UiEffect.Kind.BRIEFED_MISSION, 1));

        UiFlow joiner = briefing(false, true);
        assertThat(joiner.apply(UiIntent.mission(1))).isEqualTo(UiEffect.NONE);
    }

    @Test
    void endsTheSessionAtOnceOnBackFromABriefingNobodyJoinedAndAsksFirstWhenSomebodyHas() {
        UiFlow alone = briefing(true, false);
        assertThat(alone.apply(UiIntent.BACK)).isEqualTo(UiEffect.LEAVE);

        UiFlow crowded = briefing(true, true);
        assertThat(crowded.apply(UiIntent.BACK)).isEqualTo(UiEffect.NONE);
        assertThat(crowded.top()).isEqualTo(UiPage.BRIEFING_LEAVE);
        assertThat(crowded.apply(UiIntent.BACK)).isEqualTo(UiEffect.NONE);
        assertThat(crowded.top()).as("stay").isEqualTo(UiPage.BRIEFING);
        crowded.apply(UiIntent.BACK);
        assertThat(crowded.apply(UiIntent.LEAVE)).isEqualTo(UiEffect.LEAVE);
    }

    @Test
    void letsAJoinerLeaveFromTheBriefingWithoutAsking() {
        UiFlow joiner = briefing(false, true);

        assertThat(joiner.apply(UiIntent.BACK)).isEqualTo(UiEffect.LEAVE);
    }

    /** The host's MENU on a briefed session's result takes the machines back; a joiner's leaves only him (G0101). */
    @Test
    void takesTheMachinesBackToTheBriefingFromTheHostsResultAndAsksNothing() {
        UiFlow host = new UiFlow(UiPage.PLAY);
        host.observe(false, true);
        host.observeResult(true, true, true);

        assertThat(host.top()).isEqualTo(UiPage.RESULT);
        assertThat(host.apply(UiIntent.LEAVE)).isEqualTo(UiEffect.TO_BRIEFING);

        UiFlow joiner = new UiFlow(UiPage.PLAY);
        joiner.observe(false, false);
        joiner.observeResult(true, false, true);
        assertThat(joiner.apply(UiIntent.LEAVE)).isEqualTo(UiEffect.LEAVE);
    }

    private static UiFlow briefing(boolean host, boolean anybodyJoined) {
        UiFlow flow = new UiFlow(UiPage.MAIN);
        flow.apply(UiIntent.open(UiPage.ONLINE));
        flow.apply(UiIntent.open(UiPage.ONLINE_HOST));
        flow.apply(UiIntent.open(UiPage.BRIEFING));
        flow.observeBriefing(host, anybodyJoined);
        return flow;
    }

    @Test
    void stepsHostGamesChoiceRowsOnlyOnHostGame() {
        UiFlow flow = new UiFlow(UiPage.MAIN);
        flow.apply(UiIntent.open(UiPage.ONLINE));

        assertThat(flow.apply(UiIntent.choose(0))).isEqualTo(UiEffect.NONE);
        flow.apply(UiIntent.open(UiPage.ONLINE_HOST));
        assertThat(flow.apply(UiIntent.choose(0))).isEqualTo(new UiEffect(UiEffect.Kind.CHOOSE, 0));
        assertThat(flow.apply(UiIntent.choose(1))).isEqualTo(new UiEffect(UiEffect.Kind.CHOOSE, 1));
        assertThat(flow.top()).isEqualTo(UiPage.ONLINE_HOST);
    }

    @Test
    void provesTheSetupOnlyFromTheProvingGround() {
        UiFlow flow = new UiFlow(UiPage.MAIN);

        assertThat(flow.apply(UiIntent.PROVE)).isEqualTo(UiEffect.NONE);
        flow.apply(UiIntent.open(UiPage.PROVING_GROUND));
        assertThat(flow.apply(UiIntent.PROVE)).isEqualTo(UiEffect.PROVE);
    }

    @Test
    void startsTheScenarioChosenOnTheCampaignPage() {
        UiFlow flow = new UiFlow(UiPage.MAIN);
        flow.apply(UiIntent.open(UiPage.CAMPAIGN));

        UiEffect effect = flow.apply(UiIntent.start(3));

        assertThat(effect).isEqualTo(new UiEffect(UiEffect.Kind.START, 3));
    }

    @Test
    void opensTheMenuAtOnceAndAsksForThePause() {
        UiFlow flow = playing(false);

        UiEffect effect = flow.apply(UiIntent.PAUSE);

        assertThat(flow.top()).isEqualTo(UiPage.PAUSE);
        assertThat(flow.menuOpen()).isTrue();
        assertThat(effect).isEqualTo(UiEffect.REQUEST_PAUSE);
    }

    @Test
    void keepsTheMenuOpenWhileThePauseIsStillOnItsWay() {
        UiFlow flow = playing(false);
        flow.apply(UiIntent.PAUSE);

        flow.observe(false, true);
        flow.observe(false, true);

        assertThat(flow.top()).isEqualTo(UiPage.PAUSE);
    }

    @Test
    void asksToResumeAPauseThatHadNotLandedYet() {
        UiFlow flow = playing(false);
        flow.apply(UiIntent.PAUSE);

        UiEffect effect = flow.apply(UiIntent.BACK);

        assertThat(effect).isEqualTo(UiEffect.REQUEST_RESUME);
        assertThat(flow.top()).isEqualTo(UiPage.PLAY);
    }

    @Test
    void asksForNoPauseWhenTheGameIsAlreadyPaused() {
        UiFlow flow = playing(true);

        assertThat(flow.apply(UiIntent.PAUSE)).isEqualTo(UiEffect.NONE);
        assertThat(flow.top()).isEqualTo(UiPage.PAUSE);
    }

    @Test
    void showsTheBannerAndTakesNoSelectionWhenSomebodyElsePauses() {
        UiFlow flow = playing(false);

        flow.observe(true, true);

        assertThat(flow.top()).isEqualTo(UiPage.PLAY);
        assertThat(flow.menuOpen()).isFalse();
        assertThat(flow.showsPausedBanner()).isTrue();
    }

    @Test
    void closesTheMenuWhenAnybodyResumes() {
        UiFlow flow = playing(false);
        flow.apply(UiIntent.PAUSE);
        flow.observe(true, true);
        flow.apply(UiIntent.open(UiPage.LEAVE_CONFIRM));

        flow.observe(false, true);

        assertThat(flow.top()).isEqualTo(UiPage.PLAY);
    }

    @Test
    void resumesOnBackFromThePauseMenuAndStepsBackFromTheLeaveConfirm() {
        UiFlow flow = playing(true);
        flow.apply(UiIntent.PAUSE);
        flow.apply(UiIntent.open(UiPage.LEAVE_CONFIRM));

        assertThat(flow.apply(UiIntent.BACK)).isEqualTo(UiEffect.NONE);
        assertThat(flow.top()).isEqualTo(UiPage.PAUSE);
        assertThat(flow.apply(UiIntent.BACK)).isEqualTo(UiEffect.REQUEST_RESUME);
        assertThat(flow.top()).isEqualTo(UiPage.PLAY);
    }

    /** The controls over the pause step back to it, flip PROMPTS, and a pad's START still resumes from them. */
    @Test
    void opensTheControlsFromThePauseMenuAndComesBackToIt() {
        UiFlow flow = playing(true);
        flow.apply(UiIntent.PAUSE);
        flow.apply(UiIntent.open(UiPage.PAUSE_CONTROLS));

        assertThat(flow.top()).isEqualTo(UiPage.PAUSE_CONTROLS);
        assertThat(flow.menuOpen()).isTrue();
        assertThat(flow.apply(UiIntent.choose(0))).isEqualTo(new UiEffect(UiEffect.Kind.CHOOSE, 0));
        flow.apply(UiIntent.BACK);
        assertThat(flow.top()).isEqualTo(UiPage.PAUSE);
        flow.apply(UiIntent.open(UiPage.PAUSE_CONTROLS));
        assertThat(flow.apply(UiIntent.RESUME)).isEqualTo(UiEffect.REQUEST_RESUME);
        assertThat(flow.top()).isEqualTo(UiPage.PLAY);
    }

    @Test
    void leavesOnlyThroughTheConfirm() {
        UiFlow flow = playing(true);
        flow.apply(UiIntent.PAUSE);

        assertThat(flow.apply(UiIntent.LEAVE)).isEqualTo(UiEffect.NONE);
        flow.apply(UiIntent.open(UiPage.LEAVE_CONFIRM));
        assertThat(flow.apply(UiIntent.LEAVE)).isEqualTo(UiEffect.LEAVE);
    }

    @Test
    void hidesRestartFromAPeerThatDoesNotHost() {
        UiFlow flow = playing(true);
        flow.observe(true, false);
        flow.apply(UiIntent.PAUSE);

        assertThat(flow.canRestart()).isFalse();
        assertThat(flow.apply(UiIntent.RESTART)).isEqualTo(UiEffect.NONE);
        assertThat(flow.top()).isEqualTo(UiPage.PAUSE);
    }

    @Test
    void restartsFromThePauseMenuAndClosesIt() {
        UiFlow flow = playing(true);
        flow.apply(UiIntent.PAUSE);

        assertThat(flow.apply(UiIntent.RESTART)).isEqualTo(UiEffect.RESTART);
        assertThat(flow.top()).isEqualTo(UiPage.PLAY);
    }

    @Test
    void cannotOpenTheResultByHand() {
        UiFlow flow = playing(false);

        flow.apply(UiIntent.open(UiPage.RESULT));

        assertThat(flow.top()).isEqualTo(UiPage.PLAY);
    }

    @Test
    void opensTheResultOnceItIsDue() {
        UiFlow flow = playing(false);
        flow.observeResult(false, false);
        assertThat(flow.top()).isEqualTo(UiPage.PLAY);

        flow.observeResult(true, false);

        assertThat(flow.top()).isEqualTo(UiPage.RESULT);
        assertThat(flow.menuOpen()).isTrue();
    }

    @Test
    void retriesTheLevelFromTheResult() {
        UiFlow flow = resultShown(true, false);

        assertThat(flow.apply(UiIntent.RESTART)).isEqualTo(UiEffect.RESTART);
        assertThat(flow.top()).isEqualTo(UiPage.PLAY);
    }

    @Test
    void leavesFromTheResultAtOnceWhenNobodyElseIsPlaying() {
        UiFlow flow = resultShown(true, false);

        assertThat(flow.apply(UiIntent.LEAVE)).isEqualTo(UiEffect.LEAVE);
    }

    /** Leaving ends the session for everyone, so the host is asked first, as from the pause menu. */
    @Test
    void asksTheHostToConfirmLeavingFromTheResult() {
        UiFlow flow = resultShown(true, true);

        assertThat(flow.apply(UiIntent.LEAVE)).isEqualTo(UiEffect.NONE);
        assertThat(flow.top()).isEqualTo(UiPage.LEAVE_CONFIRM);
        assertThat(flow.apply(UiIntent.BACK)).isEqualTo(UiEffect.NONE);
        assertThat(flow.top()).isEqualTo(UiPage.RESULT);
        flow.apply(UiIntent.LEAVE);
        assertThat(flow.apply(UiIntent.LEAVE)).isEqualTo(UiEffect.LEAVE);
    }

    /** A lost level does not close on an accidental press. */
    @Test
    void copiesTheCodeOnlyFromTheResult() {
        UiFlow flow = new UiFlow(UiPage.PLAY);

        assertThat(flow.apply(UiIntent.COPY_CODE)).isEqualTo(UiEffect.NONE);
        flow.observeResult(true, false);
        assertThat(flow.apply(UiIntent.COPY_CODE)).isEqualTo(UiEffect.COPY_CODE);
        assertThat(flow.top()).isEqualTo(UiPage.RESULT);
    }

    @Test
    void ignoresBackOnTheResult() {
        UiFlow flow = resultShown(true, false);

        assertThat(flow.apply(UiIntent.BACK)).isEqualTo(UiEffect.NONE);
        assertThat(flow.apply(UiIntent.RESUME)).isEqualTo(UiEffect.NONE);
        assertThat(flow.top()).isEqualTo(UiPage.RESULT);
    }

    @Test
    void offersNoRetryToAPeerThatDoesNotHost() {
        UiFlow flow = resultShown(false, false);

        assertThat(flow.apply(UiIntent.RESTART)).isEqualTo(UiEffect.NONE);
        assertThat(flow.top()).isEqualTo(UiPage.RESULT);
    }

    private static UiFlow resultShown(boolean restartAllowed, boolean hosting) {
        UiFlow flow = new UiFlow(UiPage.PLAY);
        flow.observe(false, restartAllowed);
        flow.observeResult(true, hosting);
        return flow;
    }

    @Test
    void leavesWhenBackIsPressedWhileConnecting() {
        UiFlow flow = new UiFlow(UiPage.CONNECTING);

        assertThat(flow.apply(UiIntent.BACK)).isEqualTo(UiEffect.LEAVE);
    }

    private static UiFlow playing(boolean paused) {
        UiFlow flow = new UiFlow(UiPage.PLAY);
        flow.observe(paused, true);
        return flow;
    }
}
