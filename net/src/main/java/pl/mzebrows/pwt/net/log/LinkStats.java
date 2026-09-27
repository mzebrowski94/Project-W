package pl.mzebrows.pwt.net.log;

import java.util.Locale;

/**
 * One link's numbers: how much crossed it each way, how long a round trip takes, how many heartbeats
 * went unanswered. Counted per message, written to the log once every {@code statsSeconds} - never per message.
 *
 * <p>Plain fields, reset after each line, so counting costs an increment and nothing else.
 */
public final class LinkStats {

    private final int link;

    private int sentMessages;
    private long sentBytes;
    private int receivedMessages;
    private long receivedBytes;
    private int pings;
    private int pongs;
    private double roundTripMillis = Double.NaN;

    public LinkStats(int link) {
        this.link = link;
    }

    public void sent(int bytes) {
        sentMessages++;
        sentBytes += bytes;
    }

    public void received(int bytes) {
        receivedMessages++;
        receivedBytes += bytes;
    }

    public void pinged() {
        pings++;
    }

    /** A heartbeat came back after this long. Smoothed, so one slow packet does not swing the number. */
    public void ponged(double millis) {
        pongs++;
        roundTripMillis = Double.isNaN(roundTripMillis) ? millis : roundTripMillis * 0.8 + millis * 0.2;
    }

    /** The smoothed round trip in milliseconds, or NaN before the first heartbeat came back. */
    public double roundTripMillis() {
        return roundTripMillis;
    }

    /** Writes the numbers since the last line, and starts counting again. */
    public void writeTo(NetLog log, double overSeconds) {
        int unanswered = Math.max(0, pings - pongs);
        log.info(
                "stats",
                String.format(
                        Locale.ROOT,
                        "link=%d rtt=%.1fms up=%d/%.1fkB down=%d/%.1fkB heartbeats=%d unanswered=%d over=%.1fs",
                        link,
                        roundTripMillis,
                        sentMessages,
                        sentBytes / 1024.0,
                        receivedMessages,
                        receivedBytes / 1024.0,
                        pings,
                        unanswered,
                        overSeconds));
        sentMessages = 0;
        sentBytes = 0;
        receivedMessages = 0;
        receivedBytes = 0;
        pings = 0;
        pongs = 0;
    }
}
