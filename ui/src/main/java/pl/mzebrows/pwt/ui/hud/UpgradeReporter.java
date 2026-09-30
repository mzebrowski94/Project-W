package pl.mzebrows.pwt.ui.hud;

/**
 * Files the draft's news (G0049): an upgrade earned, one taken - and by whom - a draft passed on to
 * another player, nothing left to learn, and the swarm adapting (ADR 0017). Counted from the readout's draft counts and held list,
 * because several ticks can run between two frames.
 *
 * <p>Who took a shared upgrade is the player whose drafts went down as its rank went up; a personal
 * one names its holder outright.
 */
public final class UpgradeReporter implements Reporter {

    @Override
    @SuppressWarnings("EnumOrdinal") // a dispatch carries an enum as its number
    public void report(Readout previous, Readout now, DispatchSink out) {
        int before = 0;
        int after = 0;
        for (int slot = 0; slot < Readout.SLOTS; slot++) {
            before += previous.player(slot).drafts;
            after += now.player(slot).drafts;
        }
        boolean took = false;
        for (int i = 0; i < now.heldCount; i++) {
            int gained = now.heldRanks[i] - ranksBefore(previous, now, i);
            if (gained <= 0) {
                continue;
            }
            if (now.heldAlien[i]) {
                out.offer(DispatchKind.SWARM_ADAPTS, -1, now.heldKind[i].ordinal(), now.heldRanks[i]);
                continue;
            }
            took = true;
            int taker = now.heldSlot[i] >= 0 ? now.heldSlot[i] : fewerDrafts(previous, now);
            out.offer(
                    DispatchKind.UPGRADE_TAKEN,
                    taker < 0 ? -1 : Reporter.named(now, taker),
                    now.heldKind[i].ordinal(),
                    now.heldRanks[i]);
        }
        for (int slot = 0; slot < Readout.SLOTS; slot++) {
            if (now.player(slot).drafts <= previous.player(slot).drafts) {
                continue;
            }
            if (after > before || took) {
                out.offer(DispatchKind.DRAFT_EARNED, Reporter.named(now, slot), 0, 0);
            } else {
                out.offer(DispatchKind.DRAFT_PASSED, Reporter.named(now, slot), 0, 0);
            }
        }
        if (now.draftsDry > previous.draftsDry) {
            out.offer(DispatchKind.NOTHING_LEFT, -1, 0, 0);
        }
    }

    private static int ranksBefore(Readout previous, Readout now, int index) {
        for (int i = 0; i < previous.heldCount; i++) {
            if (previous.heldKind[i] == now.heldKind[index] && previous.heldSlot[i] == now.heldSlot[index]) {
                return previous.heldRanks[i];
            }
        }
        return 0;
    }

    /** The first player holding fewer drafts than he did, or -1. */
    private static int fewerDrafts(Readout previous, Readout now) {
        for (int slot = 0; slot < Readout.SLOTS; slot++) {
            if (now.player(slot).drafts < previous.player(slot).drafts) {
                return slot;
            }
        }
        return -1;
    }
}
