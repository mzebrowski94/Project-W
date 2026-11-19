package pl.mzebrows.pwt.net.lan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.net.ManualClock;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.net.log.NetLog;

/** A beacon and listeners on this machine's loopback, over real datagram sockets. */
class LanDiscoveryTest {

    private static final int PORT = 47_690;
    private static final NetConfig.Lan LAN = new NetConfig.Lan(PORT, 1_000, 3_500);

    private final ManualClock clock = new ManualClock();
    private final NetLog log = NetLog.silent(64);

    @Test
    void carriesAnAnnouncementWhole() {
        LanAnnouncement sent = announcement(7L, "Skirmish");

        LanAnnouncement read = LanAnnouncement.decode(sent.encode(ByteBuffer.allocate(LanAnnouncement.MAX_BYTES)));

        assertThat(read).isEqualTo(sent);
    }

    @Test
    void carriesWhetherASessionHasARoomAndItsPasswordWhole() {
        LanAnnouncement roomed = new LanAnnouncement(
                7L, new RoomCode("ACDE"), "d8d4", 24_741, "Skirmish", "komputer", 2, 8, true, "KJ34");

        LanAnnouncement read = LanAnnouncement.decode(roomed.encode(ByteBuffer.allocate(LanAnnouncement.MAX_BYTES)));

        assertThat(read).isEqualTo(roomed);
        assertThat(read.roomed()).isTrue();
        assertThat(read.password()).isEqualTo("KJ34");
        assertThat(announcement(7L, "Skirmish").roomed())
                .as("a session of the network is no room")
                .isFalse();
        assertThat(announcement(7L, "Skirmish").password()).isEmpty();
    }

    @Test
    void refusesAPasswordThatIsNoPasswordOrBelongsToNoRoom() {
        RoomCode code = new RoomCode("ACDE");

        assertThatThrownBy(() -> new LanAnnouncement(7L, code, "d8d4", 24_741, "x", "k", 1, 8, true, "KJ3"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LanAnnouncement(7L, code, "d8d4", 47_100, "x", "k", 1, 8, false, "KJ34"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cutsANameTooLongToCarry() {
        LanAnnouncement long_ = announcement(7L, "x".repeat(200));

        assertThat(long_.scenario()).hasSize(LanAnnouncement.MAX_TEXT);
        assertThat(LanAnnouncement.decode(long_.encode(ByteBuffer.allocate(LanAnnouncement.MAX_BYTES))))
                .isEqualTo(long_);
    }

    @Test
    void refusesADatagramThatIsNotAnAnnouncement() {
        assertThatThrownBy(() -> LanAnnouncement.decode(ByteBuffer.wrap(new byte[] {1, 2, 3, 4, 5, 6, 7, 8})))
                .isInstanceOf(IllegalArgumentException.class);
        ByteBuffer cut = announcement(7L, "Skirmish").encode(ByteBuffer.allocate(LanAnnouncement.MAX_BYTES));
        cut.limit(cut.limit() - 3);
        assertThatThrownBy(() -> LanAnnouncement.decode(cut)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hearsAHostOnceHoweverOftenItAnnouncesAndForgetsItWhenItGoesQuiet() {
        try (LanListener listener = new LanListener(LAN, clock, log);
                LanBeacon beacon = new LanBeacon(LAN, clock, log, loopback())) {
            beacon.announce(announcement(99L, "Defend"));
            beacon.pump();
            List<LanListener.Heard> first = listenFor(listener, 1);
            clock.advanceMillis(1_000);
            beacon.pump();
            settle(listener);

            assertThat(first).hasSize(1);
            assertThat(first.get(0).address()).isEqualTo("127.0.0.1");
            assertThat(first.get(0).announcement().scenario()).isEqualTo("Defend");
            assertThat(listener.heard()).hasSize(1);

            clock.advanceMillis(3_600);
            listener.poll();
            assertThat(listener.heard()).isEmpty();
        }
    }

    @Test
    void letsASecondCopyOnThePcListenToo() {
        try (LanListener first = new LanListener(LAN, clock, log);
                LanListener second = new LanListener(LAN, clock, log)) {
            assertThat(first.listening()).isTrue();
            assertThat(second.listening()).isTrue();
        }
    }

    @Test
    void findsSomewhereToAnnounceEvenWithNoNetwork() {
        assertThat(LanAddresses.beaconTargets(PORT))
                .contains(new InetSocketAddress(InetAddress.getLoopbackAddress(), PORT));
    }

    private static List<InetSocketAddress> loopback() {
        return List.of(new InetSocketAddress(InetAddress.getLoopbackAddress(), PORT));
    }

    /** Polls for up to a second of real time until this many sessions are heard. */
    private static List<LanListener.Heard> listenFor(LanListener listener, int sessions) {
        for (int i = 0; i < 100 && listener.heard().size() < sessions; i++) {
            LockSupport.parkNanos(10_000_000L);
            listener.poll();
        }
        return List.copyOf(listener.heard());
    }

    /** Polls for a fifth of a second of real time, so whatever was sent has arrived. */
    private static void settle(LanListener listener) {
        for (int i = 0; i < 20; i++) {
            LockSupport.parkNanos(10_000_000L);
            listener.poll();
        }
    }

    private static LanAnnouncement announcement(long session, String scenario) {
        return new LanAnnouncement(session, RoomCode.of(session), "d8d4", 47_100, scenario, "komputer", 2, 8);
    }
}
