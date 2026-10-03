package pl.mzebrows.pwt.ui.hud;

/** Where reporters file what they notice: the {@link Terminal} and the {@link Toast}, or a recorder in a test. */
public interface DispatchSink {

    /**
     * Files one line.
     *
     * @param slot the player it is about, or -1 for nobody
     */
    void offer(DispatchKind kind, int slot, int a, int b);

    /** Forgets everything filed: a new world has nothing to say about the last one. */
    void reset();

    /** Files everything with both, in that order. */
    static DispatchSink both(DispatchSink first, DispatchSink second) {
        return new DispatchSink() {
            @Override
            public void offer(DispatchKind kind, int slot, int a, int b) {
                first.offer(kind, slot, a, b);
                second.offer(kind, slot, a, b);
            }

            @Override
            public void reset() {
                first.reset();
                second.reset();
            }
        };
    }
}
