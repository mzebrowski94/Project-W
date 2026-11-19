package pl.mzebrows.pwt.relay;

/**
 * A token bucket for bytes: holds up to a second's worth of a rate, refills at that rate, and says whether a
 * message fits. One per link, on the relay's thread only.
 */
final class ByteBucket {

    private static final long SECOND_NANOS = 1_000_000_000L;

    private final long perSecond;
    private long tokens;
    private long lastNanos;

    ByteBucket(int perSecond, long nowNanos) {
        this.perSecond = perSecond;
        this.tokens = perSecond;
        this.lastNanos = nowNanos;
    }

    /** Takes {@code bytes} out; false, and nothing taken, when the link has sent more than it may. */
    boolean take(int bytes, long nowNanos) {
        long elapsed = Math.min(SECOND_NANOS, Math.max(0L, nowNanos - lastNanos));
        lastNanos = nowNanos;
        tokens = Math.min(perSecond, tokens + elapsed * perSecond / SECOND_NANOS);
        if (bytes > tokens) {
            return false;
        }
        tokens -= bytes;
        return true;
    }
}
