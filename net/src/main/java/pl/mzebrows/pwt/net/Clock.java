package pl.mzebrows.pwt.net;

/**
 * Monotonic time, for the parts of networking that have to know how long something took.
 *
 * <p>A port rather than {@code System.nanoTime()} called wherever it is wanted, so a test can drive a
 * whole network through a timeout, a heartbeat or a second of lag without waiting for any of them.
 * {@code NetArchitectureTest} lets only {@link SystemClock} read the real one.
 */
public interface Clock {

    /** Nanoseconds from some fixed, arbitrary origin. Only differences mean anything. */
    long nanos();

    /** Milliseconds, for the places a config speaks in them. */
    default long millis() {
        return nanos() / 1_000_000L;
    }
}
