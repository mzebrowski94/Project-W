package pl.mzebrows.pwt.net.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.JdkLoggerFactory;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.net.Clock;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.net.log.LinkStats;
import pl.mzebrows.pwt.net.log.NetLog;
import pl.mzebrows.pwt.net.log.NetTrace;
import pl.mzebrows.pwt.net.transport.Delivery;
import pl.mzebrows.pwt.net.transport.Transport;
import pl.mzebrows.pwt.net.transport.TransportListener;

/**
 * The transport port over real sockets (ADR 0012): TCP for reliable messages, UDP for unreliable ones,
 * both on the one port number a host listens on.
 *
 * <p><strong>Opening a link is a handshake, because UDP has no connection to open.</strong> The joiner
 * connects over TCP; the host answers with a random token; the joiner sends that token over UDP until
 * the host has heard it, which is how the host learns the address the joiner's datagrams really come
 * from - through whatever NAT or Hamachi tunnel sits between them - and then says so over TCP. Only then
 * is the link reported open, at both ends. A link that never gets that far closes with the step it was
 * stuck on in the reason: no TCP connection, no hello, or UDP that never got through - three different
 * problems with three different fixes, and the log says which.
 *
 * <p><strong>Two threads, one of them ours.</strong> Netty's event loop reads the sockets and does
 * nothing but copy what arrives onto a queue - and answer a heartbeat on the spot, so the round trip it
 * measures is the network's and not the frame rate's. Everything else - every link's state, every
 * timeout, every call to the listener - happens on the thread that calls {@link #poll}.
 *
 * <p>A heartbeat goes out on every open link; one that hears nothing for {@code linkTimeoutMillis} is
 * closed. Every message is recorded in the log's trace, each link's numbers are written to the log every
 * {@code statsSeconds} - but not a link to this same machine, the host's own game above all, which
 * measures no network and was a third of the host's log - and a link that closes badly has its part of
 * the trace dumped to the log.
 */
@SuppressWarnings("FutureReturnValueIgnored") // failures surface through the channel closing, which is watched
public final class NettyTransport implements Transport {

    private static final byte TCP_HELLO = 1;
    private static final byte TCP_BOUND = 2;
    private static final byte TCP_DATA = 3;
    private static final byte TCP_BYE = 4;

    private static final byte UDP_BIND = 11;
    private static final byte UDP_DATA = 12;
    private static final byte UDP_PING = 13;
    private static final byte UDP_PONG = 14;

    /** "PW": every datagram opens with it, so a stray one from something else is thrown away unread. */
    private static final short MAGIC = 0x5057;

    private static final int UDP_HEADER = 2 + 1 + 8;
    private static final long BIND_RETRY_NANOS = 100_000_000L;
    private static final int MOST_DUMPED = 512;

    static {
        InternalLoggerFactory.setDefaultFactory(JdkLoggerFactory.INSTANCE);
    }

    private final NetConfig config;
    private final Clock clock;
    private final NetLog log;
    private final NetTrace trace;
    private final EventLoopGroup group;
    private final ConcurrentLinkedQueue<Inbound> inbound = new ConcurrentLinkedQueue<>();
    private final Map<Integer, Link> links = new TreeMap<>();
    private final Map<Long, Link> byToken = new HashMap<>();
    private final Map<Channel, Link> byChannel = new HashMap<>();
    private final ArrayDeque<Link> closedSinceLastPoll = new ArrayDeque<>();
    private final SecureRandom tokens = new SecureRandom();
    private @Nullable Channel server;
    private @Nullable Channel udp;
    private int nextLink = 1;
    private long nextStats;
    private long lastStats;
    private boolean closed;

    /** @param name what this transport's trace dumps are marked with - which end of the host it is, say */
    public NettyTransport(NetConfig config, Clock clock, NetLog log, String name) {
        this.config = config;
        this.clock = clock;
        this.log = log;
        this.trace = log.trace(name);
        this.group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        this.lastStats = clock.nanos();
        this.nextStats = lastStats + statsNanos();
        log.adopt("io.netty");
    }

    @Override
    public int listen(int firstPort, int search) {
        for (int port = firstPort; port <= firstPort + search; port++) {
            Channel tcp = bindTcp(port);
            if (tcp == null) {
                log.info("listen", "port=" + port + " event=taken tcp");
                continue;
            }
            Channel datagrams = bindUdp(port);
            if (datagrams == null) {
                tcp.close().syncUninterruptibly();
                log.info("listen", "port=" + port + " event=taken udp");
                continue;
            }
            server = tcp;
            udp = datagrams;
            log.info("listen", "port=" + port + " event=listening tcp=yes udp=yes");
            return port;
        }
        log.error("listen", "event=failed from=" + firstPort + " to=" + (firstPort + search), null);
        throw new IllegalStateException("every port in " + firstPort + ".." + (firstPort + search) + " is taken");
    }

    @Override
    public int connect(String host, int port) {
        int id = nextLink++;
        long now = clock.nanos();
        Link link = new Link(id, true, host + ":" + port, now + millis(config.connectTimeoutMillis()));
        link.udpPeer = new InetSocketAddress(host, port);
        links.put(id, link);
        if (udp == null) {
            udp = bindUdp(0);
        }
        log.info("link", "link=" + id + " event=connecting remote=" + link.remote);
        new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.connectTimeoutMillis())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        framed(channel, false);
                    }
                })
                .connect(host, port)
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        inbound.add(new TcpConnected(id, future.channel()));
                    } else {
                        inbound.add(new ConnectFailed(id, String.valueOf(future.cause())));
                    }
                });
        return id;
    }

    @Override
    public void send(int id, Delivery delivery, byte[] data, int offset, int length) {
        int limit = delivery == Delivery.UNRELIABLE ? config.maxDatagramBytes() : Transport.MAX_MESSAGE_BYTES;
        if (length > limit) {
            throw new IllegalArgumentException(
                    delivery + " message of " + length + " bytes is over the " + limit + "-byte limit");
        }
        Link link = links.get(id);
        long now = clock.nanos();
        if (link == null || link.state != State.OPEN) {
            trace.record(now, NetTrace.Kind.DROPPED, id, length);
            return;
        }
        if (delivery == Delivery.RELIABLE) {
            byte[] frame = new byte[length + 1];
            frame[0] = TCP_DATA;
            System.arraycopy(data, offset, frame, 1, length);
            writeTcp(link, frame);
            trace.record(now, NetTrace.Kind.SENT_RELIABLE, id, length);
        } else {
            writeUdp(link, UDP_DATA, data, offset, length);
            trace.record(now, NetTrace.Kind.SENT_UNRELIABLE, id, length);
        }
        link.stats.sent(length);
    }

    @Override
    public void disconnect(int id, String reason) {
        Link link = links.get(id);
        if (link != null) {
            closeLink(link, reason, true, false);
        }
    }

    @Override
    public void poll(TransportListener listener) {
        long now = clock.nanos();
        for (Inbound event = inbound.poll(); event != null; event = inbound.poll()) {
            handle(event, now, listener);
        }
        for (Link link : new ArrayList<>(links.values())) {
            tend(link, now);
        }
        for (Link link = closedSinceLastPoll.poll(); link != null; link = closedSinceLastPoll.poll()) {
            listener.closed(link.id, link.closeReason);
        }
        if (statsNanos() > 0 && now >= nextStats) {
            double seconds = (now - lastStats) / 1e9;
            for (Link link : links.values()) {
                if (link.state == State.OPEN && !link.local) {
                    link.stats.writeTo(log, seconds);
                }
            }
            lastStats = now;
            nextStats = now + statsNanos();
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (Link link : new ArrayList<>(links.values())) {
            closeLink(link, "closed", true, false);
        }
        closedSinceLastPoll.clear();
        if (server != null) {
            server.close().syncUninterruptibly();
        }
        if (udp != null) {
            udp.close().syncUninterruptibly();
        }
        group.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
        log.info("transport", "event=closed");
    }

    private void handle(Inbound event, long now, TransportListener listener) {
        switch (event) {
            case TcpConnected connected -> {
                Link link = links.get(connected.link());
                if (link == null || link.state != State.CONNECTING) {
                    connected.channel().close();
                    return;
                }
                link.tcp = connected.channel();
                link.local = isLocal(connected.channel());
                link.state = State.HANDSHAKING;
                link.lastHeard = now;
                byChannel.put(connected.channel(), link);
                log.info("link", "link=" + link.id + " event=tcp-connected remote=" + link.remote + " next=hello");
            }
            case ConnectFailed failed -> {
                Link link = links.get(failed.link());
                if (link != null) {
                    closeLink(link, "tcp connect failed: " + failed.cause(), false, true);
                }
            }
            case TcpAccepted accepted -> accept(accepted.channel(), now);
            case TcpFrame frame -> frame(frame, listener);
            case TcpClosed gone -> {
                Link link = byChannel.get(gone.channel());
                if (link != null && link.state != State.CLOSED) {
                    closeLink(link, "connection lost", false, true);
                }
            }
            case Datagram datagram -> datagram(datagram, listener);
            case Pong pong -> {
                Link link = byToken.get(pong.token());
                if (link != null) {
                    link.lastHeard = pong.at();
                    link.stats.ponged(pong.roundTripMillis());
                    trace.record(pong.at(), NetTrace.Kind.PONG, link.id, 8);
                }
            }
        }
    }

    private void accept(Channel channel, long now) {
        int id = nextLink++;
        Link link = new Link(
                id, false, String.valueOf(channel.remoteAddress()), now + millis(config.connectTimeoutMillis()));
        link.state = State.HANDSHAKING;
        link.tcp = channel;
        link.local = isLocal(channel);
        link.lastHeard = now;
        long token;
        do {
            token = tokens.nextLong();
        } while (token == 0 || byToken.containsKey(token));
        link.token = token;
        links.put(id, link);
        byToken.put(token, link);
        byChannel.put(channel, link);
        byte[] hello = new byte[9];
        hello[0] = TCP_HELLO;
        putLong(hello, 1, token);
        writeTcp(link, hello);
        log.info("link", "link=" + id + " event=accepted remote=" + link.remote + " next=udp-bind from the joiner");
    }

    private void frame(TcpFrame frame, TransportListener listener) {
        Link link = byChannel.get(frame.channel());
        byte[] data = frame.data();
        if (link == null || link.state == State.CLOSED || data.length == 0) {
            return;
        }
        link.lastHeard = frame.at();
        switch (data[0]) {
            case TCP_HELLO -> {
                if (link.initiator && link.token == 0 && data.length >= 9) {
                    link.token = getLong(data, 1);
                    byToken.put(link.token, link);
                    link.nextBind = frame.at();
                    log.info(
                            "link",
                            "link=" + link.id + " event=hello udp-to=" + link.remote
                                    + " next=udp-bind (the far end must hear our datagrams)");
                }
            }
            case TCP_BOUND -> {
                if (link.initiator && link.state == State.HANDSHAKING) {
                    open(link, frame.at(), listener);
                }
            }
            case TCP_DATA -> {
                if (link.state == State.OPEN) {
                    trace.record(frame.at(), NetTrace.Kind.RECEIVED_RELIABLE, link.id, data.length - 1);
                    link.stats.received(data.length - 1);
                    listener.received(link.id, Delivery.RELIABLE, data, 1, data.length - 1);
                }
            }
            case TCP_BYE ->
                closeLink(
                        link, "remote: " + new String(data, 1, data.length - 1, StandardCharsets.UTF_8), false, false);
            default -> log.warn("link", "link=" + link.id + " event=unknown-frame type=" + data[0]);
        }
    }

    private void datagram(Datagram datagram, TransportListener listener) {
        Link link = byToken.get(datagram.token());
        if (link == null || link.state == State.CLOSED) {
            return;
        }
        link.lastHeard = datagram.at();
        if (datagram.type() == UDP_BIND) {
            if (!link.initiator && link.state == State.HANDSHAKING) {
                link.udpPeer = datagram.sender();
                writeTcp(link, new byte[] {TCP_BOUND});
                open(link, datagram.at(), listener);
                log.info("link", "link=" + link.id + " event=udp-bound from=" + datagram.sender());
            }
            return;
        }
        if (datagram.type() == UDP_DATA && link.state == State.OPEN) {
            if (!link.initiator) {
                link.udpPeer = datagram.sender();
            }
            byte[] data = datagram.payload();
            trace.record(datagram.at(), NetTrace.Kind.RECEIVED_UNRELIABLE, link.id, data.length);
            link.stats.received(data.length);
            listener.received(link.id, Delivery.UNRELIABLE, data, 0, data.length);
        }
    }

    private void open(Link link, long now, TransportListener listener) {
        link.state = State.OPEN;
        link.nextPing = now;
        trace.record(now, NetTrace.Kind.OPENED, link.id, 0);
        log.info("link", "link=" + link.id + " event=opened remote=" + link.remote);
        listener.opened(link.id, link.remote);
    }

    /** Timeouts, handshake retries and heartbeats for one link. */
    private void tend(Link link, long now) {
        switch (link.state) {
            case CONNECTING, HANDSHAKING -> {
                if (now > link.deadline) {
                    closeLink(link, stuckReason(link), true, true);
                } else if (link.initiator && link.token != 0 && now >= link.nextBind) {
                    writeUdp(link, UDP_BIND, new byte[0], 0, 0);
                    link.nextBind = now + BIND_RETRY_NANOS;
                }
            }
            case OPEN -> {
                if (now - link.lastHeard > millis(config.linkTimeoutMillis())) {
                    closeLink(link, "timeout: heard nothing for " + config.linkTimeoutMillis() + " ms", true, true);
                } else if (now >= link.nextPing) {
                    byte[] stamp = new byte[8];
                    putLong(stamp, 0, now);
                    writeUdp(link, UDP_PING, stamp, 0, 8);
                    link.stats.pinged();
                    trace.record(now, NetTrace.Kind.PING, link.id, 8);
                    link.nextPing = now + millis(config.heartbeatMillis());
                }
            }
            case CLOSED -> {}
        }
    }

    private static boolean isLocal(Channel channel) {
        return channel.remoteAddress() instanceof InetSocketAddress address
                && address.getAddress() != null
                && address.getAddress().isLoopbackAddress();
    }

    private String stuckReason(Link link) {
        if (link.state == State.CONNECTING) {
            return "timeout: no tcp connection to " + link.remote;
        }
        if (!link.initiator) {
            return "timeout: udp unreachable - never heard the joiner's datagrams";
        }
        return link.token == 0
                ? "timeout: the host accepted but never said hello"
                : "timeout: udp unreachable - the host never heard our datagrams";
    }

    private void closeLink(Link link, String reason, boolean tellRemote, boolean dump) {
        if (link.state == State.CLOSED) {
            return;
        }
        long now = clock.nanos();
        link.state = State.CLOSED;
        link.closeReason = reason;
        links.remove(link.id);
        if (link.token != 0) {
            byToken.remove(link.token);
        }
        Channel tcp = link.tcp;
        if (tcp != null) {
            byChannel.remove(tcp);
            if (tellRemote && tcp.isActive()) {
                byte[] reasonBytes = reason.getBytes(StandardCharsets.UTF_8);
                byte[] bye = new byte[reasonBytes.length + 1];
                bye[0] = TCP_BYE;
                System.arraycopy(reasonBytes, 0, bye, 1, reasonBytes.length);
                tcp.writeAndFlush(Unpooled.wrappedBuffer(bye)).addListener(ChannelFutureListener.CLOSE);
            } else {
                tcp.close();
            }
        }
        trace.record(now, NetTrace.Kind.CLOSED, link.id, 0);
        log.info("link", "link=" + link.id + " event=closed reason=\"" + reason + "\"");
        if (dump) {
            trace.dump(log, link.id, reason, MOST_DUMPED);
        }
        closedSinceLastPoll.add(link);
    }

    private void writeTcp(Link link, byte[] frame) {
        Channel tcp = link.tcp;
        if (tcp != null) {
            tcp.writeAndFlush(Unpooled.wrappedBuffer(frame));
        }
    }

    private void writeUdp(Link link, byte type, byte[] data, int offset, int length) {
        Channel channel = udp;
        InetSocketAddress peer = link.udpPeer;
        if (channel == null || peer == null) {
            return;
        }
        ByteBuf buffer = Unpooled.buffer(UDP_HEADER + length);
        buffer.writeShort(MAGIC).writeByte(type).writeLong(link.token).writeBytes(data, offset, length);
        channel.writeAndFlush(new DatagramPacket(buffer, peer));
    }

    private @Nullable Channel bindTcp(int port) {
        ChannelFuture bound = new ServerBootstrap()
                .group(group)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        framed(channel, true);
                    }
                })
                .bind(port)
                .awaitUninterruptibly();
        return bound.isSuccess() ? bound.channel() : null;
    }

    private @Nullable Channel bindUdp(int port) {
        ChannelFuture bound = new Bootstrap()
                .group(group)
                .channel(NioDatagramChannel.class)
                .handler(new DatagramHandler())
                .bind(port)
                .awaitUninterruptibly();
        if (!bound.isSuccess()) {
            return null;
        }
        return bound.channel();
    }

    private void framed(SocketChannel channel, boolean accepted) {
        channel.pipeline()
                .addLast(new LengthFieldBasedFrameDecoder(Transport.MAX_MESSAGE_BYTES + 1, 0, 4, 0, 4))
                .addLast(new LengthFieldPrepender(4))
                .addLast(new FrameHandler(accepted));
    }

    private long statsNanos() {
        return config.log().statsSeconds() * 1_000_000_000L;
    }

    private static long millis(long millis) {
        return millis * 1_000_000L;
    }

    private static void putLong(byte[] into, int at, long value) {
        for (int i = 7; i >= 0; i--) {
            into[at + i] = (byte) value;
            value >>>= 8;
        }
    }

    private static long getLong(byte[] from, int at) {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (from[at + i] & 0xFFL);
        }
        return value;
    }

    /** TCP frames, copied off the event loop onto the queue. */
    private final class FrameHandler extends SimpleChannelInboundHandler<ByteBuf> {

        private final boolean accepted;

        FrameHandler(boolean accepted) {
            this.accepted = accepted;
        }

        @Override
        public void channelActive(ChannelHandlerContext context) throws Exception {
            if (accepted) {
                inbound.add(new TcpAccepted(context.channel()));
            }
            super.channelActive(context);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext context, ByteBuf frame) {
            byte[] data = new byte[frame.readableBytes()];
            frame.readBytes(data);
            inbound.add(new TcpFrame(context.channel(), data, clock.nanos()));
        }

        @Override
        public void channelInactive(ChannelHandlerContext context) throws Exception {
            inbound.add(new TcpClosed(context.channel()));
            super.channelInactive(context);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
            log.warn("tcp", "event=error remote=" + context.channel().remoteAddress() + " error=\"" + cause + "\"");
            context.close();
        }
    }

    /** Datagrams: a heartbeat is answered on the spot, everything else copied onto the queue. */
    private final class DatagramHandler extends SimpleChannelInboundHandler<DatagramPacket> {

        @Override
        protected void channelRead0(ChannelHandlerContext context, DatagramPacket packet) {
            ByteBuf content = packet.content();
            if (content.readableBytes() < UDP_HEADER || content.readShort() != MAGIC) {
                return;
            }
            byte type = content.readByte();
            long token = content.readLong();
            long now = clock.nanos();
            if (type == UDP_PING && content.readableBytes() >= 8) {
                ByteBuf reply = Unpooled.buffer(UDP_HEADER + 8);
                reply.writeShort(MAGIC).writeByte(UDP_PONG).writeLong(token).writeBytes(content, 8);
                context.writeAndFlush(new DatagramPacket(reply, packet.sender()));
                return;
            }
            if (type == UDP_PONG && content.readableBytes() >= 8) {
                inbound.add(new Pong(token, (now - content.readLong()) / 1e6, now));
                return;
            }
            byte[] payload = new byte[content.readableBytes()];
            content.readBytes(payload);
            inbound.add(new Datagram(packet.sender(), type, token, payload, now));
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
            log.warn("udp", "event=error error=\"" + cause + "\"");
        }
    }

    private enum State {
        CONNECTING,
        HANDSHAKING,
        OPEN,
        CLOSED
    }

    /** One link's state. Touched only by the thread that polls. */
    private static final class Link {
        final int id;
        final boolean initiator;
        final String remote;
        final long deadline;
        final LinkStats stats;
        State state = State.CONNECTING;

        /** To this same machine: its numbers say nothing about a network, and are not written. */
        boolean local;

        @Nullable
        Channel tcp;

        @Nullable
        InetSocketAddress udpPeer;

        long token;
        long lastHeard;
        long nextPing;
        long nextBind;
        String closeReason = "";

        Link(int id, boolean initiator, String remote, long deadline) {
            this.id = id;
            this.initiator = initiator;
            this.remote = remote;
            this.deadline = deadline;
            this.stats = new LinkStats(id);
        }
    }

    /** What the event loop puts on the queue for the polling thread. */
    private sealed interface Inbound
            permits TcpConnected, ConnectFailed, TcpAccepted, TcpFrame, TcpClosed, Datagram, Pong {}

    private record TcpConnected(int link, Channel channel) implements Inbound {}

    private record ConnectFailed(int link, String cause) implements Inbound {}

    private record TcpAccepted(Channel channel) implements Inbound {}

    @SuppressWarnings("ArrayRecordComponent") // handed over once, read once, never compared
    private record TcpFrame(Channel channel, byte[] data, long at) implements Inbound {}

    private record TcpClosed(Channel channel) implements Inbound {}

    @SuppressWarnings("ArrayRecordComponent") // handed over once, read once, never compared
    private record Datagram(InetSocketAddress sender, byte type, long token, byte[] payload, long at)
            implements Inbound {}

    private record Pong(long token, double roundTripMillis, long at) implements Inbound {}
}
