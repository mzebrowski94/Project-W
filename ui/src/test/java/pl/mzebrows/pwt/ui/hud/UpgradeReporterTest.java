package pl.mzebrows.pwt.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.upgrade.UpgradeKind;

/** The draft's news (G0049): earned, taken and by whom, passed on, and nothing left to learn. */
@SuppressWarnings("EnumOrdinal") // a dispatch carries an enum as its number
class UpgradeReporterTest {

    private final List<String> lines = new ArrayList<>();
    private final DispatchSink out = new DispatchSink() {
        @Override
        public void offer(DispatchKind kind, int slot, int a, int b) {
            lines.add(kind + " " + slot + " " + a + "/" + b);
        }

        @Override
        public void reset() {}
    };

    private final UpgradeReporter reporter = new UpgradeReporter();

    @Test
    void saysADraftWasEarned() {
        Readout was = players(2);
        Readout now = players(2);
        now.player(1).drafts = 1;

        reporter.report(was, now, out);

        assertThat(lines).containsExactly("DRAFT_EARNED 1 0/0");
    }

    @Test
    void namesWhoTookASharedUpgradeAndItsNewRank() {
        Readout was = players(2);
        was.player(1).drafts = 1;
        held(was, UpgradeKind.LARGER_ARMY, -1, 1);
        Readout now = players(2);
        held(now, UpgradeKind.LARGER_ARMY, -1, 2);

        reporter.report(was, now, out);

        assertThat(lines).containsExactly("UPGRADE_TAKEN 1 " + UpgradeKind.LARGER_ARMY.ordinal() + "/2");
    }

    @Test
    void namesThePersonalUpgradesHolder() {
        Readout was = players(2);
        Readout now = players(2);
        held(now, UpgradeKind.GRENADIER, 0, 1);

        reporter.report(was, now, out);

        assertThat(lines).containsExactly("UPGRADE_TAKEN 0 " + UpgradeKind.GRENADIER.ordinal() + "/1");
    }

    @Test
    void saysADraftPassedOnToAnotherPlayer() {
        Readout was = players(2);
        was.player(0).drafts = 1;
        Readout now = players(2);
        now.player(1).drafts = 1;

        reporter.report(was, now, out);

        assertThat(lines).containsExactly("DRAFT_PASSED 1 0/0");
    }

    @Test
    void tellsAnEarnedDraftFromAPassedOneWhenACardWasTakenInTheSameFrame() {
        Readout was = players(2);
        was.player(0).drafts = 1;
        Readout now = players(2);
        now.player(1).drafts = 1;
        held(now, UpgradeKind.MARKSMEN, -1, 1);

        reporter.report(was, now, out);

        assertThat(lines)
                .containsExactly("UPGRADE_TAKEN 0 " + UpgradeKind.MARKSMEN.ordinal() + "/1", "DRAFT_EARNED 1 0/0");
    }

    /** Nobody took it: the swarm adapted, with its new rank, and no player is named. */
    @Test
    void saysTheSwarmAdaptedAndNamesNoPlayer() {
        Readout was = players(2);
        Readout now = players(2);
        adapted(now, UpgradeKind.RAPID_SPIT, 2);

        reporter.report(was, now, out);

        assertThat(lines).containsExactly("SWARM_ADAPTS -1 " + UpgradeKind.RAPID_SPIT.ordinal() + "/2");
    }

    /** An adaptation is no card taken, so a draft that moved in the same frame still moved by passing on. */
    @Test
    void tellsAPassedDraftFromAnEarnedOneWhenTheSwarmAdaptedInTheSameFrame() {
        Readout was = players(2);
        was.player(0).drafts = 1;
        Readout now = players(2);
        now.player(1).drafts = 1;
        adapted(now, UpgradeKind.RAPID_SPIT, 1);

        reporter.report(was, now, out);

        assertThat(lines)
                .containsExactly("SWARM_ADAPTS -1 " + UpgradeKind.RAPID_SPIT.ordinal() + "/1", "DRAFT_PASSED 1 0/0");
    }

    @Test
    void saysWhenNothingIsLeftToLearn() {
        Readout was = players(1);
        Readout now = players(1);
        now.draftsDry = 1;

        reporter.report(was, now, out);

        assertThat(lines).containsExactly("NOTHING_LEFT -1 0/0");
    }

    @Test
    void namesNobodyInASoloGame() {
        Readout was = players(1);
        Readout now = players(1);
        now.player(0).drafts = 1;

        reporter.report(was, now, out);

        assertThat(lines).containsExactly("DRAFT_EARNED -1 0/0");
    }

    private static Readout players(int count) {
        Readout readout = new Readout();
        for (int slot = 0; slot < count; slot++) {
            readout.player(slot).posture = Posture.STANDING;
        }
        return readout;
    }

    private static void held(Readout readout, UpgradeKind kind, int slot, int ranks) {
        int i = readout.heldCount++;
        readout.heldKind[i] = kind;
        readout.heldSlot[i] = slot;
        readout.heldRanks[i] = ranks;
        readout.heldMaxRanks[i] = 3;
    }

    private static void adapted(Readout readout, UpgradeKind kind, int ranks) {
        held(readout, kind, -1, ranks);
        readout.heldAlien[readout.heldCount - 1] = true;
    }
}
