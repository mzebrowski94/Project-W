package pl.mzebrows.pwt.ui.hud;

/**
 * Notices one kind of fact changing between two frames' readouts and files what it saw (plan 8.2). Reads
 * counters where the world keeps them, because several ticks can run between frames and a flag that
 * flips twice in them is invisible. Never formats text: that is the terminal's words.
 */
public interface Reporter {

    /** Files what changed from {@code previous} to {@code now}. */
    void report(Readout previous, Readout now, DispatchSink out);

    /** Takes a new world as it is, silently: the first frame of a world reports nothing it already had. */
    default void bind(Readout now) {}

    /**
     * Who a line about this slot names: the slot while more than one player is in the game, else -1 - a
     * lone player needs no "P1" in front of everything.
     */
    static int named(Readout now, int slot) {
        int players = 0;
        for (int i = 0; i < Readout.SLOTS; i++) {
            if (now.player(i).posture != Posture.ABSENT) {
                players++;
            }
        }
        return players > 1 ? slot : -1;
    }
}
