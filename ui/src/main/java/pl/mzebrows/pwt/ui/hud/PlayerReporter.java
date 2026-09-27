package pl.mzebrows.pwt.ui.hud;

/**
 * Each player's commander, as the battlefield hears of him: a new commander joining, a commander down,
 * that same line turning to lost if he dies, and a new gun in his hands. Getting up, respawning and
 * climbing onto a turret are left to his panel - they are his, not news to everyone.
 */
public final class PlayerReporter implements Reporter {

    private final boolean[] seen = new boolean[Readout.SLOTS];

    @Override
    public void bind(Readout now) {
        for (int slot = 0; slot < seen.length; slot++) {
            seen[slot] = now.player(slot).posture != Posture.ABSENT;
        }
    }

    @Override
    @SuppressWarnings("EnumOrdinal")
    public void report(Readout previous, Readout now, DispatchSink out) {
        for (int slot = 0; slot < Readout.SLOTS; slot++) {
            PlayerReadout was = previous.player(slot);
            PlayerReadout is = now.player(slot);
            int named = Reporter.named(now, slot);
            if (is.posture != Posture.ABSENT && !seen[slot]) {
                seen[slot] = true;
                out.offer(DispatchKind.COMMANDER_JOINED, slot, 0, 0);
            }
            if (is.posture == Posture.CASUALTY && was.posture != Posture.CASUALTY && was.posture != Posture.DYING) {
                out.offer(DispatchKind.COMMANDER_DOWN, named, 0, 0);
            }
            boolean died = is.posture == Posture.DYING && was.posture != Posture.DYING;
            boolean gone = was.posture.present() && !is.posture.present() && was.posture != Posture.DYING;
            if (died || gone) {
                out.offer(DispatchKind.COMMANDER_LOST, named, 0, 0);
            }
            if (is.posture == Posture.STANDING && was.posture == Posture.STANDING && is.weapon != was.weapon) {
                out.offer(DispatchKind.WEAPON_ACQUIRED, named, is.weapon.ordinal(), 0);
            }
        }
    }
}
