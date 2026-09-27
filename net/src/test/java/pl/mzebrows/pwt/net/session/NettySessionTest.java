package pl.mzebrows.pwt.net.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.net.SystemClock;
import pl.mzebrows.pwt.net.log.NetLog;
import pl.mzebrows.pwt.net.netty.NettyTransport;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.WorldCodec;

/**
 * A session over real sockets on this machine, in real time: the sequencer on its own thread, the
 * host's peer and a joiner each on a Netty transport of their own - which is what two copies of the game
 * on one PC are. Unreliable traffic really goes by UDP here; everywhere else it is the loopback network.
 */
class NettySessionTest {

    private static final long HOST_KEY = 0x0DD5_EEDL;
    private static final long FRAME_NANOS = 16_666_667L;

    private final NetLog log = NetLog.silent(256);
    private final NetConfig config = config();

    @Test
    void playsOneWorldOnTwoPeersOverUdpAndTcp() {
        Sequencer sequencer = new Sequencer(
                transport(),
                config,
                log,
                new SessionSetup("", "Skirmish", 3L),
                Players.MAX,
                1f / 60f,
                HOST_KEY,
                BuildId.current());
        int port = sequencer.listen(config.port(), config.portSearch());
        try (SequencerThread thread = new SequencerThread(sequencer, SystemClock.INSTANCE, log).start();
                PeerSession host = peer();
                PeerSession joiner = peer()) {
            host.connect("127.0.0.1", port, HOST_KEY);
            host.seat(0);
            joiner.connect("127.0.0.1", port, 0L);
            joiner.seat(0);
            PlayerInputs local = new PlayerInputs(Players.MAX);

            play(host, joiner, local, 3_000);
            assertThat(thread.failure()).isNull();
            assertThat(host.state()).isEqualTo(PeerSession.State.PLAYING);
            assertThat(joiner.state()).isEqualTo(PeerSession.State.PLAYING);
            assertThat(joiner.slotOf(0)).isEqualTo(1);
            assertThat(joiner.tick()).as("ticks played in three seconds").isGreaterThan(100);

            host.pause(true);
            int settled = 0;
            for (int frame = 0; frame < 300 && settled < 30; frame++) {
                int before = host.tick();
                play(host, joiner, local, 17);
                settled = host.paused() && host.tick() == before && joiner.tick() == host.tick() ? settled + 1 : 0;
            }
            assertThat(settled).as("both stopped on the same tick").isEqualTo(30);
            assertThat(hash(joiner)).isEqualTo(hash(host));
        }
    }

    private void play(PeerSession host, PeerSession joiner, PlayerInputs local, long millis) {
        long end = SystemClock.INSTANCE.nanos() + millis * 1_000_000L;
        int frame = 0;
        while (SystemClock.INSTANCE.nanos() < end) {
            local.clear();
            double angle = frame++ * 0.05;
            local.set(
                    0,
                    new CommanderInput(
                            (float) StrictMath.cos(angle), (float) StrictMath.sin(angle), 80f, 45f, true, false));
            long now = SystemClock.INSTANCE.nanos();
            host.pump(now, local);
            joiner.pump(now, local);
            LockSupport.parkNanos(FRAME_NANOS / 4);
        }
    }

    private static long hash(PeerSession peer) {
        Simulation world = peer.simulation();
        assertThat(world).isNotNull();
        return WorldCodec.hash(world);
    }

    private PeerSession peer() {
        return new PeerSession(transport(), config, log, new TestWorlds(), null, BuildId.current());
    }

    private NettyTransport transport() {
        return new NettyTransport(config, SystemClock.INSTANCE, log, "test");
    }

    private static NetConfig config() {
        NetConfig shipped = NetConfig.defaults();
        return new NetConfig(
                47_500,
                8,
                2_000,
                5_000,
                100,
                shipped.maxDatagramBytes(),
                shipped.maxMessageBytes(),
                NetConfig.Conditions.NONE,
                shipped.latencySteps(),
                new NetConfig.LogConfig("unused", "INFO", 1024, 0),
                shipped.session(),
                shipped.lan());
    }
}
