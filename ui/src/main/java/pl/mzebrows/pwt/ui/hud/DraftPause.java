package pl.mzebrows.pwt.ui.hud;

/**
 * Opening a draft pauses a solo game and closing it resumes (D2); with two or more players present the
 * game runs on (G0049). It resumes only a pause it made itself, so a game somebody else paused stays
 * paused - and it resumes the moment the game stops being solo, a friend back from a dropped link (S8),
 * with the draft still open: co-op never pauses.
 */
public final class DraftPause {

    /** What the session should do this frame. */
    public enum Action {
        NONE,
        PAUSE,
        RESUME
    }

    private boolean ours;

    /**
     * What to do about the pause this frame.
     *
     * @param open whether the draft is on show on this machine
     * @param solo whether exactly one player is in the game
     * @param paused whether the game is paused now
     * @param menu whether a menu is open on this machine: the pause is the menu's from then on
     */
    public Action next(boolean open, boolean solo, boolean paused, boolean menu) {
        if (menu) {
            ours = false;
            return Action.NONE;
        }
        if (open && solo && !paused && !ours) {
            ours = true;
            return Action.PAUSE;
        }
        if ((!open || !solo) && ours) {
            ours = false;
            return paused ? Action.RESUME : Action.NONE;
        }
        return Action.NONE;
    }

    /** Whether the pause in force is the draft's own, which the paused banner does not show over. */
    public boolean ours() {
        return ours;
    }
}
