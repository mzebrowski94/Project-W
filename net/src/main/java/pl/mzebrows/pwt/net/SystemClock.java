package pl.mzebrows.pwt.net;

/** The real monotonic clock. The one class in {@code net} allowed to read it. */
public enum SystemClock implements Clock {
    INSTANCE;

    @Override
    public long nanos() {
        return System.nanoTime();
    }
}
