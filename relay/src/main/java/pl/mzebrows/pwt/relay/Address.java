package pl.mzebrows.pwt.relay;

/**
 * What the relay remembers of one address (stage 9's limits): the links and rooms it holds now, the refusals it
 * has collected in this window and how long it is ignored for. Memory only - a restart clears it.
 */
final class Address {

    int links;
    int rooms;
    private int refusals;
    private long windowStartNanos;
    private long silentUntilNanos;

    /** Whether the relay is ignoring this address at {@code now}. */
    boolean silent(long now) {
        return now < silentUntilNanos;
    }

    /**
     * Records a refusal; true when this one is the cap-th in the window and the address is now ignored.
     * A window opens with the first refusal after the last one expired and does not slide.
     */
    boolean refused(long now, int max, long windowNanos, long silenceNanos) {
        if (refusals == 0 || now - windowStartNanos >= windowNanos) {
            refusals = 0;
            windowStartNanos = now;
        }
        refusals++;
        if (refusals >= max) {
            refusals = 0;
            silentUntilNanos = now + silenceNanos;
            return true;
        }
        return false;
    }

    /** Nothing to remember: no link, no room, no live window, no silence. */
    boolean forgettable(long now, long windowNanos) {
        return links == 0 && rooms == 0 && !silent(now) && (refusals == 0 || now - windowStartNanos >= windowNanos);
    }
}
