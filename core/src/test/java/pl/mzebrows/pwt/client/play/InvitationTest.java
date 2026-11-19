package pl.mzebrows.pwt.client.play;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.net.ManualClock;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.net.lan.LanBeacon;
import pl.mzebrows.pwt.net.lan.LanListener;
import pl.mzebrows.pwt.net.lan.RoomCode;
import pl.mzebrows.pwt.net.log.NetLog;
import pl.mzebrows.pwt.net.session.BuildId;

/** A hosting copy's invitation, heard by a joiner's listener on this machine's loopback. */
class InvitationTest {

    private static final NetConfig.Lan LAN = new NetConfig.Lan(47_691, 1_000, 3_500);

    @Test
    void announcesTheSessionUnderItsCodeAndReadsItOutWithTheAddresses() {
        ManualClock clock = new ManualClock();
        LanBeacon beacon = new LanBeacon(
                LAN,
                clock,
                NetLog.silent(64),
                List.of(new InetSocketAddress(InetAddress.getLoopbackAddress(), LAN.port())));
        try (LanListener listener = new LanListener(LAN, clock, NetLog.silent(64));
                Invitation invitation =
                        new Invitation(beacon, 42L, 47_100, "KOMPUTER", List.of("192.168.1.5", "25.1.2.3"))) {
            invitation.pump(2, "Defend");
            for (int i = 0; i < 100 && listener.heard().isEmpty(); i++) {
                LockSupport.parkNanos(10_000_000L);
                listener.poll();
            }

            assertThat(invitation.code()).isEqualTo(RoomCode.of(42L));
            assertThat(invitation.line())
                    .isEqualTo("room " + RoomCode.of(42L) + "   join at 192.168.1.5, 25.1.2.3   port 47100");
            assertThat(listener.heard()).hasSize(1);
            var heard = listener.heard().get(0).announcement();
            assertThat(heard.code()).isEqualTo(RoomCode.of(42L));
            assertThat(heard.scenario()).isEqualTo("Defend");
            assertThat(heard.players()).isEqualTo(2);
            assertThat(heard.build()).isEqualTo(BuildId.current());
        }
    }
}
