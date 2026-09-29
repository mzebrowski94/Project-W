package pl.mzebrows.pwt.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** A solo draft pauses and resumes; with company it runs on; and it resumes only its own pause (D2). */
class DraftPauseTest {

    private final DraftPause pause = new DraftPause();

    @Test
    void pausesASoloGameWhileTheDraftIsOpenAndResumesIt() {
        DraftPause.Action opened = pause.next(true, true, false, false);
        DraftPause.Action held = pause.next(true, true, true, false);
        DraftPause.Action closed = pause.next(false, true, true, false);

        assertThat(opened).isEqualTo(DraftPause.Action.PAUSE);
        assertThat(held).isEqualTo(DraftPause.Action.NONE);
        assertThat(closed).isEqualTo(DraftPause.Action.RESUME);
        assertThat(pause.ours()).isFalse();
    }

    @Test
    void runsOnWithTwoPlayersPresent() {
        assertThat(pause.next(true, false, false, false)).isEqualTo(DraftPause.Action.NONE);
        assertThat(pause.next(false, false, false, false)).isEqualTo(DraftPause.Action.NONE);
    }

    /** A friend back from a dropped link makes it co-op, which never pauses: it resumes, the draft still open. */
    @Test
    void resumesWhenTheGameStopsBeingSoloWithTheDraftStillOpen() {
        pause.next(true, true, false, false);

        DraftPause.Action joined = pause.next(true, false, true, false);
        DraftPause.Action after = pause.next(true, false, false, false);

        assertThat(joined).isEqualTo(DraftPause.Action.RESUME);
        assertThat(after).isEqualTo(DraftPause.Action.NONE);
        assertThat(pause.ours()).isFalse();
    }

    @Test
    void leavesAPauseSomebodyElseMadeAlone() {
        DraftPause.Action opened = pause.next(true, true, true, false);
        DraftPause.Action closed = pause.next(false, true, true, false);

        assertThat(opened).isEqualTo(DraftPause.Action.NONE);
        assertThat(closed).isEqualTo(DraftPause.Action.NONE);
    }

    /** The menu opening closes the draft, and the pause becomes the menu's: closing must not resume. */
    @Test
    void handsItsPauseToAMenuThatOpens() {
        pause.next(true, true, false, false);

        DraftPause.Action menu = pause.next(false, true, true, true);

        assertThat(menu).isEqualTo(DraftPause.Action.NONE);
        assertThat(pause.ours()).isFalse();
    }
}
