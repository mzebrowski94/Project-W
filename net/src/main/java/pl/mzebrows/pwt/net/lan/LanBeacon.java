package pl.mzebrows.pwt.net.lan;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.net.Clock;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.net.log.NetLog;

/**
 * A host announcing its session to the local network once every {@code announceMillis}.
 *
 * <p>It sends from a port of its own choosing to the joiners' shared port, so any number of hosts can
 * run on one PC. A target it cannot reach - an adapter that has gone down, a network with no route - is
 * skipped and said once, and the rest are still sent to. {@link #pump} does the sending, from whichever
 * thread owns this.
 */
public final class LanBeacon implements AutoCloseable {

    private final NetConfig.Lan config;
    private final Clock clock;
    private final NetLog log;
    private final List<InetSocketAddress> targets;
    private final List<InetSocketAddress> complainedAbout = new ArrayList<>();
    private final ByteBuffer out = ByteBuffer.allocate(LanAnnouncement.MAX_BYTES);
    private final @Nullable DatagramChannel channel;
    private @Nullable LanAnnouncement announcing;
    private long nextNanos;

    /** @param targets where to announce: {@link LanAddresses#beaconTargets} for real, loopback in a test */
    public LanBeacon(NetConfig.Lan config, Clock clock, NetLog log, List<InetSocketAddress> targets) {
        this.config = config;
        this.clock = clock;
        this.log = log;
        this.targets = List.copyOf(targets);
        DatagramChannel opened = null;
        try {
            opened = DatagramChannel.open();
            opened.setOption(StandardSocketOptions.SO_BROADCAST, true);
            opened.configureBlocking(false);
            opened.bind(null);
        } catch (IOException e) {
            log.warn("lan", "event=beacon-failed reason=\"" + e.getMessage() + "\"");
            closeQuietly(opened);
            opened = null;
        }
        this.channel = opened;
        this.nextNanos = clock.nanos();
    }

    /** What to announce from now on; the next {@link #pump} sends it at once. */
    public void announce(LanAnnouncement what) {
        boolean first = announcing == null;
        announcing = what;
        if (first) {
            nextNanos = clock.nanos();
            log.info(
                    "lan", "event=announcing code=" + what.code() + " port=" + what.gamePort() + " targets=" + targets);
        }
    }

    /** Sends the announcement if one is due. */
    public void pump() {
        DatagramChannel open = channel;
        LanAnnouncement what = announcing;
        long now = clock.nanos();
        if (open == null || what == null || now - nextNanos < 0) {
            return;
        }
        nextNanos = now + config.announceMillis() * 1_000_000L;
        what.encode(out);
        for (InetSocketAddress target : targets) {
            try {
                out.rewind();
                open.send(out, target);
            } catch (IOException e) {
                if (!complainedAbout.contains(target)) {
                    complainedAbout.add(target);
                    log.info("lan", "event=unreachable target=" + target + " reason=\"" + e.getMessage() + "\"");
                }
            }
        }
    }

    @Override
    public void close() {
        closeQuietly(channel);
    }

    static void closeQuietly(@Nullable DatagramChannel channel) {
        if (channel == null) {
            return;
        }
        try {
            channel.close();
        } catch (IOException e) {
            // Closing is best effort; there is nothing left to do with it either way.
        }
    }
}
