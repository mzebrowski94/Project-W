package pl.mzebrows.pwt.relay;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.mzebrows.pwt.net.ManualClock;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.net.door.Door;
import pl.mzebrows.pwt.net.log.NetLog;
import pl.mzebrows.pwt.net.loopback.LoopbackNetwork;
import pl.mzebrows.pwt.net.session.RoomWire;
import pl.mzebrows.pwt.net.transport.Delivery;
import pl.mzebrows.pwt.net.transport.Transport;
import pl.mzebrows.pwt.net.transport.TransportListener;

/** The relay's abuse limits, each at its edge: one below, at, one above, and the window running out. */
class RelayLimitsTest {

    private static final String BUILD = "b412b68";
    private static final String VERSION = "2026.11.13-b412b68";

    @TempDir
    Path dir;

    private final NetConfig net = NetConfig.defaults();
    private final NetConfig.Relay limits = net.relay();
    private final LoopbackNetwork network = new LoopbackNetwork(net);
    private final ManualClock clock = new ManualClock();
    private final List<Machine> machines = new ArrayList<>();
    private NetLog log;
    private Relay relay;

    @BeforeEach
    void startRelay() {
        log = NetLog.open(new NetConfig.LogConfig(dir.toString(), "FINE", 64, 0), "relay");
        Transport transport = network.endpoint("relay");
        transport.listen(24_741, 0);
        relay = new Relay(transport, clock, log, net, new java.util.SplittableRandom(7));
    }

    @AfterEach
    void stop() {
        log.close();
    }

    @Test
    void anAddressHoldsLinksPerAddressMaxLinksAndTheNextIsClosedUnanswered() {
        for (int i = 0; i < limits.linksPerAddressMax(); i++) {
            assertThat(machine("a").closed).isFalse();
        }
        Machine over = machine("a");

        assertThat(over.closed).isTrue();
        assertThat(over.heard).isEmpty();
        assertThat(machine("b").closed).isFalse();
    }

    @Test
    void aLinkThatLeavesFreesItsPlaceAtTheAddress() {
        List<Machine> held = new ArrayList<>();
        for (int i = 0; i < limits.linksPerAddressMax(); i++) {
            held.add(machine("a"));
        }
        held.get(0).leave();

        assertThat(machine("a").closed).isFalse();
    }

    @Test
    void anAddressOpensRoomsPerAddressMaxRoomsAndTheNextHostIsRefused() {
        List<Machine> hosts = new ArrayList<>();
        for (int i = 0; i < limits.roomsPerAddressMax(); i++) {
            Machine host = machine("a");
            host.say(open());
            assertThat(host.last()).isInstanceOf(Door.Opened.class);
            hosts.add(host);
        }
        Machine over = machine("a");
        over.say(open());
        Machine other = machine("b");
        other.say(open());

        assertThat(over.last()).isInstanceOf(Door.Refused.class);
        assertThat(over.closed).isTrue();
        assertThat(other.last()).isInstanceOf(Door.Opened.class);

        hosts.get(0).leave();
        Machine again = machine("a");
        again.say(open());
        assertThat(again.last()).isInstanceOf(Door.Opened.class);
    }

    @Test
    void aNewLinkThatSaysNothingIsDroppedAtTheHelloTimeoutAndNotBefore() {
        long timeout = limits.helloTimeoutSeconds() * 1000L;
        Machine silent = machine("a");

        clock.advanceMillis(timeout - 1);
        relay.poll();
        silent.hear();
        assertThat(silent.closed).isFalse();

        clock.advanceMillis(1);
        relay.poll();
        silent.hear();
        assertThat(silent.closed).isTrue();
    }

    @Test
    void aLinkThatHasSpokenIsNotDroppedForSilenceAfterwards() {
        Machine host = machine("a");
        host.say(open());
        clock.advanceMillis(limits.helloTimeoutSeconds() * 1000L * 10);
        relay.poll();
        host.hear();

        assertThat(host.closed).isFalse();
    }

    @Test
    void anAddressIsIgnoredAfterRefusalsPerAddressMaxRefusalsUntilTheSilencePasses() {
        refuseTimes("a", limits.refusalsPerAddressMax() - 1);
        Machine atEdge = machine("a");
        atEdge.say(join("QQQQ"));
        assertThat(atEdge.last()).isInstanceOf(Door.Refused.class); // the cap-th is still answered

        Machine ignored = machine("a");
        ignored.say(join("QQQQ"));
        assertThat(ignored.closed).isTrue();
        assertThat(ignored.heard).isEmpty();
        Machine stranger = machine("b");
        stranger.say(join("QQQQ"));
        assertThat(stranger.last()).isInstanceOf(Door.Refused.class);

        clock.advanceMillis(limits.silenceSeconds() * 1000L - 1);
        relay.poll();
        Machine stillIgnored = machine("a");
        stillIgnored.say(join("QQQQ"));
        assertThat(stillIgnored.heard).isEmpty();

        clock.advanceMillis(1);
        relay.poll();
        Machine heard = machine("a");
        heard.say(join("QQQQ"));
        assertThat(heard.last()).isInstanceOf(Door.Refused.class);
    }

    @Test
    void refusalsOutsideTheWindowDoNotAddUp() {
        refuseTimes("a", limits.refusalsPerAddressMax() - 1);
        clock.advanceMillis(limits.refusalWindowSeconds() * 1000L);
        relay.poll();
        refuseTimes("a", limits.refusalsPerAddressMax() - 1);
        Machine next = machine("a");
        next.say(join("QQQQ"));

        assertThat(next.last()).isInstanceOf(Door.Refused.class);
    }

    @Test
    void aRoomLocksAfterWrongPasswordsPerRoomMaxWrongOnesAndOpensAgainAfterTheLock() {
        String code = openRoom("ACDE");
        for (int i = 0; i < limits.wrongPasswordsPerRoomMax() - 1; i++) {
            Machine wrong = machine("w" + i);
            wrong.say(new Door.Join(code, "AAAA", BUILD, VERSION));
            assertThat(wrong.last()).isEqualTo(Door.refusal("wrong password for room " + code));
        }
        Machine friend = machine("friend");
        friend.say(new Door.Join(code, "ACDE", BUILD, VERSION));
        assertThat(friend.last()).isEqualTo(new Door.Accepted(code)); // one below: still open

        Machine last = machine("w-last");
        last.say(new Door.Join(code, "AAAA", BUILD, VERSION));
        Machine locked = machine("late");
        locked.say(new Door.Join(code, "ACDE", BUILD, VERSION));

        assertThat(locked.last())
                .isEqualTo(new Door.Refused(
                        "refused by the relay: room " + code + " is not taking machines for a moment"));

        clock.advanceMillis(limits.roomLockSeconds() * 1000L - 1);
        Machine early = machine("early");
        early.say(new Door.Join(code, "ACDE", BUILD, VERSION));
        assertThat(early.last()).isInstanceOf(Door.Refused.class);

        clock.advanceMillis(1);
        Machine after = machine("after");
        after.say(new Door.Join(code, "ACDE", BUILD, VERSION));
        assertThat(after.last()).isEqualTo(new Door.Accepted(code));
    }

    @Test
    void wrongPasswordsOutsideTheWindowDoNotLockARoom() {
        String code = openRoom("ACDE");
        for (int i = 0; i < limits.wrongPasswordsPerRoomMax() - 1; i++) {
            machine("w" + i).say(new Door.Join(code, "AAAA", BUILD, VERSION));
        }
        clock.advanceMillis(limits.roomLockSeconds() * 1000L);
        for (int i = 0; i < limits.wrongPasswordsPerRoomMax() - 1; i++) {
            machine("x" + i).say(new Door.Join(code, "AAAA", BUILD, VERSION));
        }
        Machine friend = machine("friend");
        friend.say(new Door.Join(code, "ACDE", BUILD, VERSION));

        assertThat(friend.last()).isEqualTo(new Door.Accepted(code));
    }

    @Test
    void readsTheAddressOfARemoteTheWayEachTransportSpellsIt() {
        assertThat(Relay.addressOf("machine3")).isEqualTo("machine3");
        assertThat(Relay.addressOf("/203.0.113.9:51234")).isEqualTo("203.0.113.9");
        assertThat(Relay.addressOf("host.example/203.0.113.9:51234")).isEqualTo("203.0.113.9");
        assertThat(Relay.addressOf("/0:0:0:0:0:0:0:1:51234")).isEqualTo("0:0:0:0:0:0:0:1");
    }

    @Test
    void logsASilenceAndALockAsEventsAndNeverAPassword() throws IOException {
        String code = openRoom("TUV3");
        for (int i = 0; i < limits.wrongPasswordsPerRoomMax(); i++) {
            machine("w").say(new Door.Join(code, "WRNG", BUILD, VERSION));
        }
        for (int i = limits.wrongPasswordsPerRoomMax(); i < limits.refusalsPerAddressMax(); i++) {
            machine("w").say(join("QQQQ"));
        }
        log.close();

        String written = Files.readString(log.file());
        assertThat(written)
                .contains("event=room-locked code=" + code)
                .contains("event=address-silenced")
                .doesNotContain("TUV3")
                .doesNotContain("WRNG");
    }

    private void refuseTimes(String address, int times) {
        for (int i = 0; i < times; i++) {
            Machine machine = machine(address);
            machine.say(join("QQQQ"));
            assertThat(machine.last()).isInstanceOf(Door.Refused.class);
        }
    }

    private String openRoom(String password) {
        Machine host = machine("host");
        host.say(new Door.Open(BUILD, VERSION, 42L, RoomWire.sessionConfig(net.session()), password));
        return ((Door.Opened) host.last()).code();
    }

    private static Door.Open open() {
        return new Door.Open(
                BUILD, VERSION, 42L, RoomWire.sessionConfig(NetConfig.defaults().session()), "");
    }

    private static Door.Join join(String code) {
        return new Door.Join(code, "", BUILD, VERSION);
    }

    /** A link to the relay from the named address. */
    private Machine machine(String address) {
        Machine machine = new Machine(address);
        machines.add(machine);
        return machine;
    }

    private final class Machine implements TransportListener {
        final Transport transport;
        final int link;
        final List<Door.Message> heard = new ArrayList<>();
        boolean closed;

        Machine(String address) {
            transport = network.endpoint(address);
            link = transport.connect("relay", 24_741);
            relay.poll();
            transport.poll(this);
        }

        void say(Door.Message message) {
            byte[] bytes = Door.encode(message);
            transport.send(link, Delivery.RELIABLE, bytes, 0, bytes.length);
            relay.poll();
            transport.poll(this);
        }

        void leave() {
            transport.disconnect(link, "gone");
            relay.poll();
            transport.poll(this);
        }

        void hear() {
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
