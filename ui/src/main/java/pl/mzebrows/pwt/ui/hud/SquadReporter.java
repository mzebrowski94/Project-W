package pl.mzebrows.pwt.ui.hud;

/**
 * The squad, as news: reinforcements arriving, and the moment the squad falls below a handful. Single
 * losses are not reported - they are the fight's ordinary noise, and the world shows them. A man shot
 * down is still standing in the count, so one stabilised in time changes nothing here.
 */
public final class SquadReporter implements Reporter {

    private final int few;

    /** Reports the squad as few once, as it falls below {@code few} troopers standing. */
    public SquadReporter(int few) {
        this.few = few;
    }

    @Override
    public void report(Readout previous, Readout now, DispatchSink out) {
        int change = now.troopersStanding - previous.troopersStanding;
        if (change > 0) {
            out.offer(DispatchKind.REINFORCED, -1, change, 0);
        } else if (previous.troopersStanding >= few && now.troopersStanding < few) {
            out.offer(DispatchKind.FEW_TROOPERS, -1, now.troopersStanding, 0);
        }
    }
}
