package pl.mzebrows.pwt.ui.hud;

/** Where reporters file what they notice: the {@link Terminal}, or a recorder in a test. */
public interface DispatchSink {

    /**
     * Files one line.
     *
     * @param slot the player it is about, or -1 for nobody
     */
    void offer(DispatchKind kind, int slot, int a, int b);

    /** Forgets everything filed: a new world has nothing to say about the last one. */
    void reset();
}
