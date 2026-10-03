package pl.mzebrows.pwt.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ToastTest {

    /** Ten characters a second, two seconds held once typed, a queue of two. */
    private static final Toast.Settings SLOW = new Toast.Settings(10f, 2f, 2, 0.5f);

    private static Toast toast(Toast.Settings settings) {
        return new Toast(settings, dispatch -> dispatch.kind() + " " + dispatch.a());
    }

    private static void run(Toast toast, float seconds) {
        for (float t = 0f; t < seconds; t += 0.05f) {
            toast.advance(0.05f);
        }
    }

    @Test
    void typesAnUpgradeTakenAtItsRate() {
        Toast toast = toast(SLOW);
        toast.offer(DispatchKind.UPGRADE_TAKEN, 0, 3, 1);
        toast.advance(0f);

        assertThat(toast.showing()).isTrue();
        assertThat(toast.line()).isEqualTo("UPGRADE_TAKEN 3");
        assertThat(toast.revealed()).isZero();
        toast.advance(0.5f);

        assertThat(toast.revealed()).isEqualTo(5);
    }

    @Test
    void showsAnUpgradeEarned() {
        Toast toast = toast(SLOW);
        toast.offer(DispatchKind.DRAFT_EARNED, 1, 0, 0);
        toast.advance(0f);

        assertThat(toast.kind()).isEqualTo(DispatchKind.DRAFT_EARNED);
        assertThat(toast.slot()).isEqualTo(1);
    }

    @Test
    void showsAnUpgradePassedOn() {
        Toast toast = toast(SLOW);
        toast.offer(DispatchKind.DRAFT_PASSED, 2, 0, 0);
        toast.advance(0f);

        assertThat(toast.kind()).isEqualTo(DispatchKind.DRAFT_PASSED);
        assertThat(toast.slot()).isEqualTo(2);
    }

    @Test
    void showsItWholeAtOnceWithTypingOff() {
        Toast toast = toast(new Toast.Settings(0f, 2f, 2, 0.5f));
        toast.offer(DispatchKind.SWARM_ADAPTS, -1, 4, 1);
        toast.advance(0f);

        assertThat(toast.revealed()).isEqualTo(toast.line().length());
    }

    @Test
    void goesOnceTypedAndHeld() {
        Toast toast = toast(SLOW);
        toast.offer(DispatchKind.UPGRADE_TAKEN, 0, 3, 1);

        // Fifteen characters take 1.5 s, then it is held two.
        run(toast, 3.3f);
        assertThat(toast.showing()).isTrue();
        run(toast, 0.4f);

        assertThat(toast.showing()).isFalse();
    }

    @Test
    void ignoresWhatIsNotAnUpgradeOrAnAdaptation() {
        Toast toast = toast(SLOW);
        toast.offer(DispatchKind.COMMANDER_DOWN, 0, 0, 0);
        toast.offer(DispatchKind.NOTHING_LEFT, -1, 0, 0);
        toast.advance(0.1f);

        assertThat(toast.showing()).isFalse();
    }

    @Test
    void showsTheNextOnceTheFirstHasGone() {
        Toast toast = toast(SLOW);
        toast.offer(DispatchKind.UPGRADE_TAKEN, 0, 3, 1);
        toast.offer(DispatchKind.SWARM_ADAPTS, -1, 4, 1);
        toast.advance(0f);

        assertThat(toast.kind()).isEqualTo(DispatchKind.UPGRADE_TAKEN);
        run(toast, 4f);

        assertThat(toast.kind()).isEqualTo(DispatchKind.SWARM_ADAPTS);
        assertThat(toast.slot()).isEqualTo(-1);
    }

    @Test
    void dropsTheOldestWaitingWhenTheQueueIsFull() {
        Toast toast = toast(SLOW);
        toast.offer(DispatchKind.UPGRADE_TAKEN, 0, 1, 1);
        toast.advance(0f);
        toast.offer(DispatchKind.UPGRADE_TAKEN, 0, 2, 1);
        toast.offer(DispatchKind.UPGRADE_TAKEN, 0, 3, 1);
        toast.offer(DispatchKind.UPGRADE_TAKEN, 0, 4, 1);
        run(toast, 4f);

        assertThat(toast.line()).isEqualTo("UPGRADE_TAKEN 3");
    }

    @Test
    void forgetsEverythingOnANewWorld() {
        Toast toast = toast(SLOW);
        toast.offer(DispatchKind.UPGRADE_TAKEN, 0, 1, 1);
        toast.advance(0f);
        toast.offer(DispatchKind.UPGRADE_TAKEN, 0, 2, 1);

        toast.reset();
        toast.advance(0.1f);

        assertThat(toast.showing()).isFalse();
    }

    @Test
    void changesItsRevisionWhenANewOneShows() {
        Toast toast = toast(SLOW);
        int before = toast.revision();
        toast.offer(DispatchKind.UPGRADE_TAKEN, 0, 1, 1);
        toast.advance(0f);

        assertThat(toast.revision()).isNotEqualTo(before);
    }
}
