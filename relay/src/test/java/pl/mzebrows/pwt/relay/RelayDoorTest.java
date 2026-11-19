package pl.mzebrows.pwt.relay;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.mzebrows.pwt.net.Clock;
import pl.mzebrows.pwt.net.ManualClock;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.net.door.Door;
import pl.mzebrows.pwt.net.log.NetLog;
import pl.mzebrows.pwt.net.loopback.LoopbackNetwork;
import pl.mzebrows.pwt.net.session.RoomWire;
import pl.mzebrows.pwt.net.transport.Delivery;
import pl.mzebrows.pwt.net.transport.Transport;
import pl.mzebrows.pwt.net.transport.TransportListener;
import pl.mzebrows.pwt.sim.Players;

class RelayDoorTest {

    private static final String BUILD = "b412b68";
    private static final String VERSION = "2026.11.13-b412b68";

    @TempDir
    Path dir;

    private final NetConfig net = NetConfig.defaults();
    private LoopbackNetwork network = new LoopbackNetwork(net);
    private final Clock clock = new ManualClock();
    private final List<Machine> machines = new ArrayList<>();
    private NetLog log;
    private Transport relayTransport;
    private Relay relay;

    @BeforeEach
    void startRelay() {
        start(net, new java.util.SplittableRandom(7));
    }

    private void start(NetConfig config, RandomGenerator random) {
        network = new LoopbackNetwork(net);
        log = NetLog.open(new NetConfig.LogConfig(dir.toString(), "FINE", 64, 0), "relay");
        relayTransport = network.endpoint("relay");
        relayTransport.listen(24_741, 0);
        relay = new Relay(relayTransport, clock, log, config, random);
    }

    @AfterEach
    void stop() {
        log.close();
    }

    @Test
    void aHostIsGivenTheCodeOfItsRoomAndAJoinerIsLetIn() {
        Machine host = machine();
        host.say(open(""));
        assertThat(host.last()).isInstanceOf(Door.Opened.class);
        String code = ((Door.Opened) host.last()).code();

        Machine joiner = machine();
        joiner.say(join(code, ""));

        assertThat(joiner.last()).isEqualTo(new Door.Accepted(code));
        assertThat(relay.linksIn(code)).isEqualTo(2);
        assertThat(joiner.closed).isFalse();
    }

    @Test
    void refusesInOrderWithTheTextsOfTicketFour() {
        String code = openRoom("ACDE");
        Machine noRoom = machine();
        noRoom.say(join("QQQQ", ""));
        Machine noPassword = machine();
        noPassword.say(join(code, ""));
        Machine wrong = machine();
        wrong.say(join(code, "AAAA"));
        Machine otherBuild = machine();
        otherBuild.say(new Door.Join(code, "acde", "a1b2c3d", "2026.11.10-a1b2c3d"));

        assertThat(noRoom.last()).isEqualTo(Door.refusal("no room QQQQ"));
        assertThat(noPassword.last()).isEqualTo(Door.refusal("room " + code + " has a password"));
        assertThat(wrong.last()).isEqualTo(Door.refusal("wrong password for room " + code));
        assertThat(otherBuild.last())
                .isEqualTo(new Door.Refused("refused by the relay: room " + code + " runs " + VERSION
                        + ", this copy is 2026.11.10-a1b2c3d"));
        assertThat(List.of(noRoom, noPassword, wrong, otherBuild)).allMatch(m -> m.closed);
    }

    @Test
    void aRoomHoldsPlayersMaxMachinesAndTurnsTheNextAway() {
        assertThat(Relay.MACHINES_MAX).isEqualTo(Players.MAX);
        String code = openRoom("");
        for (int i = 1; i < Players.MAX; i++) {
            machine().say(join(code, ""));
        }
        Machine last = machine();
        last.say(join(code, ""));

        assertThat(relay.linksIn(code)).isEqualTo(Players.MAX);
        assertThat(last.last()).isEqualTo(Door.refusal("room " + code + " is full"));
        assertThat(last.closed).isTrue();
    }

    @Test
    void readsAPasswordAndACodeTheForgivingWay() {
        String code = openRoom("a c-d e");
        Machine joiner = machine();
        joiner.say(new Door.Join(code.toLowerCase(java.util.Locale.ROOT), "ACDE", BUILD, VERSION));

        assertThat(joiner.last()).isEqualTo(new Door.Accepted(code));
    }

    @Test
    void neverWritesAPasswordToTheLog() throws IOException {
        String code = openRoom("TUV3");
        machine().say(join(code, "WRNG"));
        machine().say(join(code, "TUV3"));
        machine().say(join(code, ""));
        log.close();

        String written = Files.readString(log.file());
        assertThat(written).contains("password=yes").doesNotContain("TUV3").doesNotContain("WRNG");
    }

    @Test
    void drawsACodeFromTheFreeOnesAndNeverOneALiveRoomHolds() {
        // A generator that gives the same four draws again and again: the second room must redraw.
        int[] script = {0, 1, 2, 3, 0, 1, 2, 3, 4, 5, 6, 7};
        int[] next = {0};
        RandomGenerator stuck = new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0;
            }

            @Override
            public int nextInt(int bound) {
                return script[next[0]++ % script.length];
            }
        };
        log.close();
        machines.clear();
        start(net, stuck);

        String first = openRoom("");
        String second = openRoom("");

        assertThat(first).isEqualTo("ACDE");
        assertThat(second).isEqualTo("FGHJ");
        assertThat(relay.roomCount()).isEqualTo(2);
    }

    @Test
    void refusesARoomPastRoomsMaxAndABadPassword() {
        log.close();
        start(withRoomsMax(1), new java.util.SplittableRandom(1));
        Machine bad = machine();
        bad.say(open("0000"));
        openRoom("");
        Machine over = machine();
        over.say(open(""));

        assertThat(bad.last()).isInstanceOf(Door.Refused.class);
        assertThat(over.last()).isEqualTo(Door.refusal("no room is free"));
        assertThat(relay.roomCount()).isEqualTo(1);
    }

    @Test
    void closesARoomWhenItsLastLinkLeaves() {
        Machine host = machine();
        host.say(open(""));
        String code = ((Door.Opened) host.last()).code();

        host.transport.disconnect(host.link, "gone");
        relay.poll();

        assertThat(relay.roomCount()).isZero();
        assertThat(relay.linksIn(code)).isZero();
    }

    @Test
    void dropsALinkThatSendsNonsense() {
        Machine machine = machine();
        machine.send(new byte[] {99, 1, 2});

        assertThat(machine.closed).isTrue();
    }

    private NetConfig withRoomsMax(int roomsMax) {
        NetConfig.Relay base = net.relay();
        return net.with(new NetConfig.Relay(
                roomsMax,
                base.roomsPerAddressMax(),
                base.linksPerAddressMax(),
                base.helloTimeoutSeconds(),
                base.refusalsPerAddressMax(),
                base.refusalWindowSeconds(),
                base.silenceSeconds(),
                base.wrongPasswordsPerRoomMax(),
                base.roomLockSeconds(),
                base.bytesPerSecondPerLink(),
                base.idleRoomMinutes(),
                base.sealLateWarnMs(),
                base.host(),
                base.port()));
    }

    private String openRoom(String password) {
        Machine host = machine();
        host.say(open(password));
        return ((Door.Opened) host.last()).code();
    }

    private static Door.Open open(String password) {
        return new Door.Open(
                BUILD, VERSION, 42L, RoomWire.sessionConfig(NetConfig.defaults().session()), password);
    }

    private static Door.Join join(String code, String password) {
        return new Door.Join(code, password, BUILD, VERSION);
    }

    private Machine machine() {
        Machine machine = new Machine("machine" + machines.size());
        machines.add(machine);
        return machine;
    }

    /** A peer at the door: says one thing, lets the relay answer, and remembers what came back. */
    private final class Machine implements TransportListener {
        final Transport transport;
        final int link;
        final List<Door.Message> heard = new ArrayList<>();
        boolean closed;

        Machine(String name) {
            transport = network.endpoint(name);
            link = transport.connect("relay", 24_741);
            relay.poll();
            transport.poll(this);
        }

        void say(Door.Message message) {
            send(Door.encode(message));
        }

        void send(byte[] bytes) {
            transport.send(link, Delivery.RELIABLE, bytes, 0, bytes.length);
            relay.poll();
            transport.poll(this);
        }

        Door.Message last() {
            return heard.get(heard.size() - 1);
        }

        @Override
        public void opened(int l, String remote) {}

        @Override
        public void received(int l, Delivery delivery, byte[] data, int offset, int length) {
            heard.add(Door.read(data, offset, length));
        }

        @Override
        public void closed(int l, String reason) {
            closed = true;
        }
    }
}
