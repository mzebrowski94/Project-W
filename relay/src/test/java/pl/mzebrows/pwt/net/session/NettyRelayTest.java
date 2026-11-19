package pl.mzebrows.pwt.net.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.SplittableRandom;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.net.SystemClock;
import pl.mzebrows.pwt.net.door.DoorClient;
import pl.mzebrows.pwt.net.log.NetLog;
import pl.mzebrows.pwt.net.netty.NettyTransport;
import pl.mzebrows.pwt.relay.Relay;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.WorldCodec;

/**
 * A relay on real sockets, in real time: its rooms' sequencers each on a thread, the host's peer and a joiner each on
 * a Netty transport of their own, TCP for the door and the snapshot and UDP for the ticks - what two copies of the
 * game and a relay on one PC are ({@code NettySessionTest}, through the door).
 */
class NettyRelayTest {

    private static final long HOST_KEY = 0x0DD5_EEDL;
    private static final long FRAME_NANOS = 16_666_667L;

    private final NetLog log = NetLog.silent(256);
    private final NetConfig config = config();

    @Test
    void playsOneWorldOnTwoPeersThroughARelayOverUdpAndTcp() {
        NettyTransport relayEnd = transport();
        int port = relayEnd.listen(config.port(), config.portSearch());
        Relay relay = new Relay(
                relayEnd, SystemClock.INSTANCE, log, config, new SplittableRandom(3), Relay.Sequencing.THREADED);
        try (PeerSession host = peer();
                PeerSession joiner = peer()) {
            DoorClient hostDoor =
                    DoorClient.open(BuildId.current(), "test", HOST_KEY, RoomWire.sessionConfig(config.session()), "");
            host.connect("127.0.0.1", port, HOST_KEY, hostDoor);
            host.seat(0);
            host.start(new SessionSetup("", "Skirmish", 3L), 1f / 60f);
            PlayerInputs local = new PlayerInputs(Players.MAX);
            DoorClient joinerDoor = null;
            long end = SystemClock.INSTANCE.nanos() + 4_000_000_000L;
            int frame = 0;
            while (SystemClock.INSTANCE.nanos() < end) {
                if (joinerDoor == null && hostDoor.code() != null) {
                    joinerDoor = DoorClient.join(hostDoor.code(), "", BuildId.current(), "test");
                    joiner.connect("127.0.0.1", port, 0L, joinerDoor);
                    joiner.seat(0);
                }
                local.clear();
                double angle = frame++ * 0.05;
                local.set(
                        0,
                        new CommanderInput(
                                (float) StrictMath.cos(angle), (float) StrictMath.sin(angle), 80f, 45f, true, false));
                relay.poll();
                host.pump(SystemClock.INSTANCE, local);
                joiner.pump(SystemClock.INSTANCE, local);
                LockSupport.parkNanos(FRAME_NANOS / 4);
            }
            assertThat(hostDoor.state()).isEqualTo(DoorClient.State.IN);
            assertThat(host.state()).isEqualTo(PeerSession.State.PLAYING);
            assertThat(joiner.state()).isEqualTo(PeerSession.State.PLAYING);
            assertThat(joiner.slotOf(0)).isEqualTo(1);
            assertThat(joiner.tick()).as("ticks played in four seconds").isGreaterThan(100);

            host.pause(true);
            int settled = 0;
            for (int i = 0; i < 300 && settled < 30; i++) {
                int before = host.tick();
                for (int j = 0; j < 17; j++) {
                    relay.poll();
                    host.pump(SystemClock.INSTANCE, local);
                    joiner.pump(SystemClock.INSTANCE, local);
                    LockSupport.parkNanos(FRAME_NANOS / 4);
                }
                settled = host.paused() && host.tick() == before && joiner.tick() == host.tick() ? settled + 1 : 0;
            }
            assertThat(settled).as("both stopped on the same tick").isEqualTo(30);
            assertThat(hash(joiner)).isEqualTo(hash(host));
        } finally {
            relay.close();
        }
    }

    private static long hash(PeerSession peer) {
        Simulation world = peer.simulation();
        assertThat(world).isNotNull();
        return WorldCodec.hash(world);
    }

    private PeerSession peer() {
        return new PeerSession(transport(), config, log, new TestWorlds(), null, BuildId.current(), "netty");
    }

    private NettyTransport transport() {
        return new NettyTransport(config, SystemClock.INSTANCE, log, "test");
    }

    private static NetConfig config() {
        NetConfig shipped = NetConfig.defaults();
        return new NetConfig(
                47_600,
                8,
                2_000,
                5_000,
                100,
                shipped.maxDatagramBytes(),
                NetConfig.Conditions.NONE,
                shipped.latencySteps(),
                new NetConfig.LogConfig("unused", "INFO", 1024, 0),
                shipped.pacing(),
                shipped.session(),
                shipped.lan(),
                shipped.relay());
    }
}
