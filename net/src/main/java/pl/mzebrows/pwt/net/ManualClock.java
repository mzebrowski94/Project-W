package pl.mzebrows.pwt.net;

/** A clock that moves only when told to - what makes a network test deterministic and instant. */
public final class ManualClock implements Clock {

    private long nanos;

    @Override
    public long nanos() {
        return nanos;
    }

    /** Moves time forward. */
    public ManualClock advanceMillis(long millis) {
        if (millis < 0) {
            throw new IllegalArgumentException("time does not go backwards: " + millis);
        }
        nanos += millis * 1_000_000L;
        return this;
    }
}
