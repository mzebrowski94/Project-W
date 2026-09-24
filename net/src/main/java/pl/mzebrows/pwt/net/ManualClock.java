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
        return advanceNanos(millis * 1_000_000L);
    }

    /** Moves time forward by a fraction of a millisecond too - a tick is not a whole number of them. */
    public ManualClock advanceNanos(long delta) {
        if (delta < 0) {
            throw new IllegalArgumentException("time does not go backwards: " + delta);
        }
        nanos += delta;
        return this;
    }
}
