package pl.mzebrows.pwt.net.log;

/**
 * The last few thousand things that crossed every link, as plain numbers in preallocated arrays.
 *
 * <p>This is what a per-message log would have been, without the cost: recording is four array writes
 * and no allocation, so it can run for every message a network sends. Nothing is written anywhere until
 * something asks - a link closing dumps its part of the ring into the {@link NetLog}, which is how the
 * last seconds before a drop reach the file without the seconds before that flooding it.
 *
 * <p>Single-threaded: the transport records from the thread that polls it.
 */
public final class NetTrace {

    /** What happened. */
    public enum Kind {
        SENT_RELIABLE,
        SENT_UNRELIABLE,
        RECEIVED_RELIABLE,
        RECEIVED_UNRELIABLE,
        PING,
        PONG,
        DROPPED,
        OPENED,
        CLOSED
    }

    private static final Kind[] KINDS = Kind.values();

    private final long[] nanos;
    private final byte[] kinds;
    private final int[] links;
    private final int[] sizes;
    private final int mask;
    private long written;

    NetTrace(int size) {
        if (size <= 0 || Integer.bitCount(size) != 1) {
            throw new IllegalArgumentException("trace size must be a power of two, was " + size);
        }
        nanos = new long[size];
        kinds = new byte[size];
        links = new int[size];
        sizes = new int[size];
        mask = size - 1;
    }

    /** Records one event. */
    @SuppressWarnings("EnumOrdinal") // stored as a byte in a ring and read back through the same enum, never persisted
    public void record(long atNanos, Kind kind, int link, int size) {
        int slot = (int) (written & mask);
        nanos[slot] = atNanos;
        kinds[slot] = (byte) kind.ordinal();
        links[slot] = link;
        sizes[slot] = size;
        written++;
    }

    /** How many events have been recorded since the start, including those the ring no longer holds. */
    public long written() {
        return written;
    }

    /**
     * Writes one link's events still in the ring to the log - the latest {@code most} of them, oldest
     * first - with times relative to the last, so a dump reads backwards from the moment it was taken.
     */
    public void dump(NetLog log, int link, String reason, int most) {
        long first = Math.max(0, written - nanos.length);
        long last = Long.MIN_VALUE;
        int count = 0;
        for (long i = first; i < written; i++) {
            int slot = (int) (i & mask);
            if (links[slot] == link) {
                last = Math.max(last, nanos[slot]);
                count++;
            }
        }
        int skip = Math.max(0, count - most);
        log.info(
                "trace",
                "link=" + link + " event=dump-begin reason=\"" + reason + "\" events=" + count + " shown="
                        + (count - skip));
        for (long i = first; i < written; i++) {
            int slot = (int) (i & mask);
            if (links[slot] != link) {
                continue;
            }
            if (skip > 0) {
                skip--;
                continue;
            }
            log.info(
                    "trace",
                    "link=" + link + " dt=" + String.format(java.util.Locale.ROOT, "%.1f", (nanos[slot] - last) / 1e6)
                            + "ms kind=" + KINDS[kinds[slot]] + " bytes=" + sizes[slot]);
        }
        log.info("trace", "link=" + link + " event=dump-end");
    }
}
