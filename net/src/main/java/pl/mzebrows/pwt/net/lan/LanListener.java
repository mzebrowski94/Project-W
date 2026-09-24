package pl.mzebrows.pwt.net.lan;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.net.Clock;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.net.log.NetLog;

/**
 * A joiner listening for the sessions on its local network.
 *
 * <p>It binds the shared discovery port with address reuse on, so a second copy of the game on the same
 * PC can listen too. A session heard over two networks - the LAN and Hamachi, or loopback and the LAN - is
 * one entry, reached at whichever address it was heard on first. One not heard from in {@code
 * forgetMillis} drops off the list. A datagram that is not an announcement is ignored: the port is not
 * ours alone.
 *
 * <p>If the port cannot be had at all, it says so once and hears nothing; joining by address still works.
 */
public final class LanListener implements AutoCloseable {

    /** A session heard on the network: what it said, and the address it said it from. */
    public record Heard(LanAnnouncement announcement, String address) {}

    private final NetConfig.Lan config;
    private final Clock clock;
    private final NetLog log;
    private final ByteBuffer in = ByteBuffer.allocate(LanAnnouncement.MAX_BYTES + 1);
    private final List<Heard> heard = new ArrayList<>();
    private final List<Heard> heardView = Collections.unmodifiableList(heard);
    private final List<Long> lastHeardNanos = new ArrayList<>();
    private final @Nullable DatagramChannel channel;

    public LanListener(NetConfig.Lan config, Clock clock, NetLog log) {
        this.config = config;
        this.clock = clock;
        this.log = log;
        DatagramChannel opened = null;
        try {
            opened = DatagramChannel.open();
            opened.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            opened.configureBlocking(false);
            opened.bind(new InetSocketAddress(config.port()));
            log.info("lan", "event=listening port=" + config.port());
        } catch (IOException e) {
            log.warn("lan", "event=listen-failed port=" + config.port() + " reason=\"" + e.getMessage() + "\"");
            LanBeacon.closeQuietly(opened);
            opened = null;
        }
        this.channel = opened;
    }

    /** Whether it is listening at all. */
    public boolean listening() {
        return channel != null;
    }

    /** Hears everything that has arrived and forgets whoever has gone quiet. */
    public void poll() {
        DatagramChannel open = channel;
        long now = clock.nanos();
        if (open != null) {
            try {
                for (SocketAddress from = receive(open); from != null; from = receive(open)) {
                    heard(from, now);
                }
            } catch (IOException e) {
                log.warn("lan", "event=receive-failed reason=\"" + e.getMessage() + "\"");
            }
        }
        long forget = config.forgetMillis() * 1_000_000L;
        for (int i = heard.size() - 1; i >= 0; i--) {
            if (now - lastHeardNanos.get(i) > forget) {
                heard.remove(i);
                lastHeardNanos.remove(i);
            }
        }
    }

    /**
     * Every session heard lately, in the order they were first heard. A live view, read every frame by the
     * join screen, so asking costs nothing; it changes on the next {@link #poll}.
     */
    public List<Heard> heard() {
        return heardView;
    }

    @Override
    public void close() {
        LanBeacon.closeQuietly(channel);
    }

    private @Nullable SocketAddress receive(DatagramChannel open) throws IOException {
        in.clear();
        return open.receive(in);
    }

    private void heard(SocketAddress from, long now) {
        if (!(from instanceof InetSocketAddress sender)) {
            return;
        }
        LanAnnouncement announcement;
        try {
            announcement = LanAnnouncement.decode(in.flip());
        } catch (IllegalArgumentException e) {
            return;
        }
        for (int i = 0; i < heard.size(); i++) {
            if (heard.get(i).announcement().session() == announcement.session()) {
                heard.set(i, new Heard(announcement, heard.get(i).address()));
                lastHeardNanos.set(i, now);
                return;
            }
        }
        heard.add(new Heard(announcement, sender.getAddress().getHostAddress()));
        lastHeardNanos.add(now);
        log.info(
                "lan",
                "event=heard code=" + announcement.code() + " from="
                        + sender.getAddress().getHostAddress() + " scenario=\"" + announcement.scenario() + "\"");
    }
}
