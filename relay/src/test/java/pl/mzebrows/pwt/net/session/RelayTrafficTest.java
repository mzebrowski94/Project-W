package pl.mzebrows.pwt.net.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.mzebrows.pwt.net.ManualClock;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.net.door.Door;
import pl.mzebrows.pwt.net.log.NetLog;
import pl.mzebrows.pwt.net.loopback.LoopbackNetwork;
import pl.mzebrows.pwt.net.transport.Delivery;
import pl.mzebrows.pwt.net.transport.Transport;
import pl.mzebrows.pwt.net.transport.TransportListener;
import pl.mzebrows.pwt.relay.Relay;

/**
 * What a room's traffic costs the relay (stage 5): the byte meter per link, the idle room and the log of a late
 * seal. Raw links speaking the wire by hand, in the session's package for {@code Msg} and {@code WireWriter}.
 */
class RelayTrafficTest {

    private static final String BUILD = "b412b68";
    private static final String VERSION = "2026.11.13-b412b68";
    private static final long KEY = 42L;

    @TempDir
    Path dir;

    private final NetConfig net = NetConfig.defaults();
    private final NetConfig.Relay limits = net.relay();
    private final LoopbackNetwork network = new LoopbackNetwork(net);
    private final ManualClock clock = new ManualClock();
    private NetLog log;
    private Relay relay;
    private final List<Raw> raws = new ArrayList<>();

    @BeforeEach
    void startRelay() {
        log = NetLog.open(new NetConfig.LogConfig(dir.toString(), "FINE", 64, 0), "relay");
        Transport transport = network.endpoint("relay");
        transport.listen(24_741, 0);
        relay = new Relay(transport, clock, log, net, new SplittableRandom(7));
    }

    @AfterEach
    void stop() {
        log.close();
    }

    @Test
    void letsALinkSendItsRateInASecond() throws IOException {
        Raw under = raw("a");
        under.door(open());
        int most = limits.bytesPerSecondPerLink() / 1000;
        for (int i = 0; i < most; i++) {
            under.send(Delivery.RELIABLE, junk(99, 1000));
        }
        relay.poll();

        assertThat(written()).doesNotContain("event=over-rate");
    }

    @Test
    void dropsALinkThatSendsMoreThanItsRate() throws IOException {
        int most = limits.bytesPerSecondPerLink() / 1000;
        Raw over = raw("b");
        over.door(open());
        for (int i = 0; i <= most + 1; i++) {
            over.send(Delivery.RELIABLE, junk(99, 1000));
        }
        relay.poll();
        assertThat(written()).contains("event=over-rate");
        over.hear();
        assertThat(over.closedFor).contains("sending more than the relay takes");
    }

    @Test
    void doesNotCountASnapshotAgainstTheRate() throws IOException {
        Raw host = raw("a");
        host.door(open());
        for (int i = 0; i < 4; i++) {
            host.send(Delivery.RELIABLE, junk(Msg.SNAPSHOT_TAKEN, limits.bytesPerSecondPerLink()));
        }
        relay.poll();

        assertThat(written()).doesNotContain("event=over-rate");
    }

    @Test
    void closesARoomThatHeardNothingButAcknowledgementsAtTheIdleMinutesAndNotBefore() {
        Raw host = raw("a");
        String code = host.door(open());
        host.send(Delivery.RELIABLE, hello(KEY));
        relay.poll();
        long idleMillis = limits.idleRoomMinutes() * 60_000L;

        quietFor(idleMillis - 1_000);
        assertThat(relay.roomCount()).isEqualTo(1);

        clock.advanceMillis(1_000);
        relay.poll();
        assertThat(relay.roomCount()).isZero();
        assertThat(code).isNotNull();
        host.hear();
        assertThat(host.closedFor).contains("the room was idle");
        assertThat(host.ended)
                .as("told as the session's end, not a drop to come back from")
                .isEqualTo("the room was idle");
    }

    @Test
    void tellsEveryMachineInARoomThatTheRelayStoppedAsTheEndOfItsSession() {
        Raw host = raw("a");
        String code = host.door(open());
        host.send(Delivery.RELIABLE, hello(KEY));
        Raw guest = raw("b");
        guest.door(new Door.Join(code, "", BUILD, VERSION));
        guest.send(Delivery.RELIABLE, hello(0L));
        relay.poll();

        relay.close();
        host.hear();
        guest.hear();

        assertThat(host.ended).isEqualTo("the relay stopped");
        assertThat(guest.ended).isEqualTo("the relay stopped");
    }

    @Test
    void keepsARoomWhereSomethingWasSaid() {
        Raw host = raw("a");
        host.door(open());
        host.send(Delivery.RELIABLE, hello(KEY));
        relay.poll();
        long idleMillis = limits.idleRoomMinutes() * 60_000L;

        quietFor(idleMillis - 1_000);
        host.send(
                Delivery.RELIABLE,
                new WireWriter(16).putByte(Msg.NOTE).putBytes(new byte[] {1}).toBytes());
        relay.poll();
        quietFor(idleMillis - 1_000);

        assertThat(relay.roomCount()).isEqualTo(1);
    }

    @Test
    void logsTheLatestLateSealWhenTheRoomCloses() throws IOException {
        Raw host = raw("a");
        host.door(open());
        host.send(Delivery.RELIABLE, hello(KEY));
        relay.poll();

        host.leave();
        relay.poll();
        quietFor(windowMillis() + 1_000);

        assertThat(relay.roomCount()).isZero();
        assertThat(written()).contains("event=room-closed").contains("sealLateMostMs=");
    }

    @Test
    void keepsTheRoomForItsLinksWhenTheHostsLinkIsGonePastTheWindowAndAnotherCanTakeOver() {
        Raw host = raw("a");
        String code = host.door(open());
        host.send(Delivery.RELIABLE, hello(KEY));
        Raw guest = raw("b");
        guest.door(new Door.Join(code, "", BUILD, VERSION));
        guest.send(Delivery.RELIABLE, hello(0L));
        relay.poll();
        assertThat(relay.linksIn(code)).isEqualTo(2);

        host.leave();
        relay.poll();
        assertThat(relay.roomCount())
                .as("the host's seats are kept for its window")
                .isEqualTo(1);
        quietFor(windowMillis() + 1_000);
        guest.hear();

        assertThat(relay.roomCount()).as("the guest is the host now (G0104)").isEqualTo(1);
        assertThat(guest.closedFor).isNull();
    }

    @Test
    void endsTheRoomAndTellsItsLinksWhenTheHostLeavesOnPurpose() {
        Raw host = raw("a");
        String code = host.door(open());
        host.send(Delivery.RELIABLE, hello(KEY));
        Raw guest = raw("b");
        guest.door(new Door.Join(code, "", BUILD, VERSION));
        guest.send(Delivery.RELIABLE, hello(0L));
        relay.poll();
        assertThat(relay.linksIn(code)).isEqualTo(2);

        host.send(Delivery.RELIABLE, new WireWriter(4).putByte(Msg.LEAVE).toBytes());
        host.leave();
        quietFor(2_000);
        guest.hear();

        assertThat(relay.roomCount()).isZero();
        assertThat(guest.closedFor).contains("the host left the game");
    }

    @Test
    void endsTheRoomWhenTheHostsLinkIsGonePastTheWindowAndNobodyElseIsThere() {
        Raw host = raw("a");
        host.door(open());
        host.send(Delivery.RELIABLE, hello(KEY));
        relay.poll();

        host.leave();
        relay.poll();
        quietFor(windowMillis() + 1_000);

        assertThat(relay.roomCount()).isZero();
    }

    @Test
    void dropsALinkLetInToComeBackThatSaysHelloInstead() {
        Raw host = raw("a");
        String code = host.door(open());
        host.send(Delivery.RELIABLE, hello(KEY));
        relay.poll();

        Raw stranger = raw("b");
        assertThat(stranger.door(new Door.Rejoin(code))).isEqualTo(code);
        stranger.send(Delivery.RELIABLE, hello(0L));
        relay.poll();
        stranger.hear();

        relay.poll();

        assertThat(stranger.closedFor).contains("did not come back");
        assertThat(relay.linksIn(code)).isEqualTo(1);
    }

    @Test
    void dropsAJoinedLinkThatSaysNothingToItsRoomAtTheHelloTimeoutAndKeepsOneThatSaidHello() {
        Raw host = raw("a");
        String code = host.door(open());
        host.send(Delivery.RELIABLE, hello(KEY));
        Raw silent = raw("b");
        silent.door(new Door.Join(code, "", BUILD, VERSION));
        long timeout = limits.helloTimeoutSeconds() * 1000L;

        clock.advanceMillis(timeout - 1);
        relay.poll();
        silent.hear();
        assertThat(silent.closedFor).isNull();

        clock.advanceMillis(1);
        relay.poll();
        silent.hear();
        host.hear();
        relay.poll();
        assertThat(silent.closedFor).contains("no hello");
        assertThat(host.closedFor).isNull();
        assertThat(relay.linksIn(code)).isEqualTo(1);
    }

    @Test
    void stopsTheSequencerThreadOfARoomThatEndedOnItsOwn() throws InterruptedException {
        long before = sequencerThreads();
        Transport transport = network.endpoint("threaded");
        transport.listen(24_742, 0);
        Relay threaded = new Relay(transport, clock, log, net, new SplittableRandom(7), Relay.Sequencing.THREADED);
        Transport machine = network.endpoint("c");
        int link = machine.connect("threaded", 24_742);
        threaded.poll();
        for (byte[] message : List.of(
                Door.encode(open()),
                hello(KEY),
                new WireWriter(4).putByte(Msg.LEAVE).toBytes())) {
            machine.send(link, Delivery.RELIABLE, message, 0, message.length);
            threaded.poll();
        }
        assertThat(sequencerThreads()).isEqualTo(before + 1);
        machine.disconnect(link, "gone");

        long deadline = System.nanoTime() + 5_000_000_000L;
        while ((threaded.roomCount() > 0 || sequencerThreads() > before) && System.nanoTime() < deadline) {
            threaded.poll();
            Thread.sleep(1);
        }

        assertThat(threaded.roomCount()).isZero();
        assertThat(sequencerThreads())
                .as("the room's sequencer thread is gone with it")
                .isLessThanOrEqualTo(before);
    }

    private static long sequencerThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().equals("pwt-sequencer") && t.isAlive())
                .count();
    }

    @Test
    void refusesAHostWhoseSessionConfigItCannotRead() {
        Raw host = raw("a");
        host.send(Delivery.RELIABLE, Door.encode(new Door.Open(BUILD, VERSION, KEY, new byte[] {1, 2, 3}, "")));
        relay.poll();
        host.hear();

        assertThat(host.last).isInstanceOf(Door.Refused.class);
        assertThat(relay.roomCount()).isZero();
    }

    private long windowMillis() {
        return net.rejoin().windowSeconds() * 1_000L;
    }

    private void quietFor(long millis) {
        long step = 5_000;
        for (long spent = 0; spent < millis; spent += step) {
            clock.advanceMillis(Math.min(step, millis - spent));
            relay.poll();
            for (Raw raw : raws) {
                raw.hear();
            }
        }
    }

    private String written() throws IOException {
        log.close();
        return Files.readString(log.file());
    }

    private Door.Open open() {
        return new Door.Open(BUILD, VERSION, KEY, RoomWire.sessionConfig(net.session()), "");
    }

    private static byte[] junk(int type, int length) {
        byte[] bytes = new byte[length];
        bytes[0] = (byte) type;
        return bytes;
    }

    private static byte[] hello(long key) {
        return new WireWriter(64)
                .putByte(Msg.HELLO)
                .putInt(Msg.PROTOCOL)
                .putString(BUILD)
                .putLong(key)
                .putString("raw")
                .toBytes();
    }

    private Raw raw(String address) {
        Raw raw = new Raw(address);
        raws.add(raw);
        return raw;
    }

    /** A link to the relay that speaks the wire by hand and remembers the last thing it heard. */
    private final class Raw implements TransportListener {
        final Transport transport;
        final int link;
        Object last;
        String closedFor;

        /** What the session's end said, if this link heard one. */
        String ended;

        Raw(String address) {
            transport = network.endpoint(address);
            link = transport.connect("relay", 24_741);
            relay.poll();
            transport.poll(this);
        }

        /** Says a door message, and returns the code in its answer, if it was one. */
        String door(Door.Message message) {
            send(Delivery.RELIABLE, Door.encode(message));
            relay.poll();
            hear();
            return last instanceof Door.Opened opened
                    ? opened.code()
                    : last instanceof Door.Accepted accepted ? accepted.code() : null;
        }

        void send(Delivery delivery, byte[] bytes) {
            transport.send(link, delivery, bytes, 0, bytes.length);
        }

        void leave() {
            transport.disconnect(link, "gone");
        }

        void hear() {
            transport.poll(this);
        }

        @Override
        public void opened(int opened, String remote) {}

        @Override
        public void received(int from, Delivery delivery, byte[] data, int offset, int length) {
            if (data[offset] >= 40) {
                last = Door.read(data, offset, length);
            } else if (data[offset] == Msg.ENDED) {
                ended = new WireReader().wrap(data, offset + 1, length - 1).getString(Msg.MAX_TEXT);
            }
        }

        @Override
        public void closed(int closed, String reason) {
            closedFor = reason;
        }
    }
}
