package pl.mzebrows.pwt.net.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.net.ManualClock;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.net.NetConfig.Conditions;
import pl.mzebrows.pwt.net.log.NetLog;
import pl.mzebrows.pwt.net.loopback.LoopbackNetwork;
import pl.mzebrows.pwt.net.transport.ConditionedTransport;
import pl.mzebrows.pwt.net.transport.Delivery;
import pl.mzebrows.pwt.net.transport.Transport;
import pl.mzebrows.pwt.net.transport.TransportListener;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.WorldCodec;
import pl.mzebrows.pwt.sim.def.Order;
import pl.mzebrows.pwt.sim.upgrade.UpgradeKind;

/**
 * A whole session in one thread: a sequencer on machine "host", the host's own peer beside it, and
 * other machines joining over a loopback network that can be made as bad as a test likes. Time is a
 * manual clock, moved one 60 Hz frame at a time; each frame pumps the sequencer, then every peer with
 * its players' scripted input.
 */
final class SessionHarness implements AutoCloseable {

    static final long FRAME_NANOS = 16_666_667L;
    static final long HOST_KEY = 0x5EC7_E7L;

    final ManualClock clock = new ManualClock();
    final NetConfig config;
    final LoopbackNetwork network;
    final Sequencer sequencer;
    final int port;
    final List<Machine> machines = new ArrayList<>();

    /** The largest snapshot a machine joining from now on sends - lowered for a world too big to send. */
    int snapshotLimit = Transport.MAX_MESSAGE_BYTES;

    /** Where a machine joining from now on writes - nowhere, unless a test is about what it writes. */
    NetLog peerLog = NetLog.silent(64);

    private int frame;

    /** How long one frame is - every machine's, and the sequencer's pump. 60 Hz unless a test says. */
    long frameNanos = FRAME_NANOS;

    /** Every byte the host has sent unreliably - ticks, nearly all of it - on every link. */
    long unreliableBytesSent;

    SessionHarness(String scenario, long seed, Conditions atTheHost) {
        this(NetConfig.defaults(), scenario, seed, atTheHost);
    }

    SessionHarness(NetConfig config, String scenario, long seed, Conditions atTheHost) {
        this(config, scenario, seed, atTheHost, NetLog.silent(64));
    }

    /** @param sequencerLog where the sequencer writes, for a test about what it writes */
    SessionHarness(NetConfig config, String scenario, long seed, Conditions atTheHost, NetLog sequencerLog) {
        this.config = config;
        network = new LoopbackNetwork(config);
        Transport hostEnd = new Counted(conditioned(network.endpoint("host"), atTheHost));
        sequencer = new Sequencer(
                hostEnd,
                config,
                sequencerLog,
                new SessionSetup("", scenario, seed),
                Players.MAX,
                1f / 60f,
                HOST_KEY,
                BuildId.current());
        port = sequencer.listen(config.port(), 0);
    }

    /** The host's own peer: on the host's machine, over a perfect link, knowing the key. */
    Machine host() {
        return join("host", Conditions.NONE, HOST_KEY, null, BuildId.current(), 0);
    }

    Machine join(String name, Conditions conditions) {
        return join(name, conditions, 0L, null, BuildId.current(), machines.size() * 37);
    }

    /** A machine whose own {@code network.properties} says {@code own} - its local config, not the host's. */
    Machine join(String name, Conditions conditions, NetConfig own) {
        return join(name, conditions, 0L, null, BuildId.current(), machines.size() * 37, own);
    }

    Machine join(String name, Conditions conditions, long key, @Nullable Path dumpDir, String build, int phase) {
        return join(name, conditions, key, dumpDir, build, phase, config);
    }

    private Machine join(
            String name,
            Conditions conditions,
            long key,
            @Nullable Path dumpDir,
            String build,
            int phase,
            NetConfig own) {
        Transport end = conditioned(network.endpoint(name), conditions);
        PeerSession session = new PeerSession(end, own, peerLog, new TestWorlds(), dumpDir, build, name, snapshotLimit);
        session.connect("host", port, key);
        session.seat(0);
        Machine machine = new Machine(name, session, phase);
        machines.add(machine);
        return machine;
    }

    /** Runs this many frames. */
    void run(int frames) {
        for (int i = 0; i < frames; i++) {
            clock.advanceNanos(frameNanos);
            long now = clock.nanos();
            sequencer.pump(now);
            for (Machine machine : machines) {
                if (machine.frozenFrames > 0) {
                    machine.frozenFrames--;
                    continue;
                }
                if (machine.cadence.charAt(frame % machine.cadence.length()) != 'x') {
                    continue;
                }
                machine.script(frame);
                machine.session.pump(clock, machine.local);
            }
            frame++;
        }
    }

    /**
     * Pauses the session, lets every machine play every tick sealed so far, checks they all hold the same
     * world, and resumes. The one moment two worlds on different machines can be compared: the same tick.
     */
    long agreedHash() {
        Machine asker = machines.get(0);
        asker.session.pause(true);
        for (int i = 0; i < 600 && !allCaughtUp(); i++) {
            run(1);
        }
        assertThat(sequencer.sealedTick()).as("ticks sealed").isPositive();
        assertThat(sequencer.paused()).as("paused").isTrue();
        assertThat(allCaughtUp())
                .as("every machine at tick %d", sequencer.sealedTick())
                .isTrue();
        long reference = WorldCodec.hash(world(machines.get(0)));
        for (Machine machine : machines) {
            assertThat(WorldCodec.hash(world(machine)))
                    .as("%s at tick %d", machine.name, machine.session.tick())
                    .isEqualTo(reference);
        }
        asker.session.pause(false);
        run(2);
        return reference;
    }

    private boolean allCaughtUp() {
        for (Machine machine : machines) {
            if (machine.session.state() != PeerSession.State.PLAYING
                    || machine.session.tick() != sequencer.sealedTick()) {
                return false;
            }
        }
        return sequencer.paused();
    }

    /** This machine's link drops: it closes, and plays no further part. */
    void leave(Machine machine) {
        machine.session.close();
        machines.remove(machine);
    }

    static Simulation world(Machine machine) {
        Simulation world = machine.session.simulation();
        assertThat(world).as("%s has a world", machine.name).isNotNull();
        return world;
    }

    private Transport conditioned(Transport transport, Conditions conditions) {
        return conditions.any() ? new ConditionedTransport(transport, conditions, clock) : transport;
    }

    @Override
    public void close() {
        for (Machine machine : machines) {
            machine.session.close();
        }
        sequencer.close();
    }

    /** The host's end of the network, counting what it sends. */
    private final class Counted implements Transport {
        private final Transport inner;

        Counted(Transport inner) {
            this.inner = inner;
        }

        @Override
        public int listen(int firstPort, int search) {
            return inner.listen(firstPort, search);
        }

        @Override
        public int connect(String host, int port) {
            return inner.connect(host, port);
        }

        @Override
        public void send(int link, Delivery delivery, byte[] data, int offset, int length) {
            if (delivery == Delivery.UNRELIABLE) {
                unreliableBytesSent += length;
            }
            inner.send(link, delivery, data, offset, length);
        }

        @Override
        public void disconnect(int link, String reason) {
            inner.disconnect(link, reason);
        }

        @Override
        public void poll(TransportListener listener) {
            inner.poll(listener);
        }

        @Override
        public void close() {
            inner.close();
        }
    }

    /** One computer in the session, with one player on its keyboard walking a circle and giving orders. */
    static final class Machine {
        final String name;
        final PeerSession session;
        final PlayerInputs local = new PlayerInputs(Players.MAX);
        final int phase;

        /** Frames this machine's game will not run at all - a window being dragged, a pause for GC. */
        int frozenFrames;

        /**
         * Which of the 60 Hz frames this machine's game draws, over and over: {@code x} draws one, {@code .}
         * lets it pass - {@code "x.."} is a machine drawing 20 frames a second.
         */
        String cadence = "x";

        /** A card this machine's player takes on its next frame, then forgets. */
        private @Nullable UpgradeKind pickKind;

        private int pickSerial;

        /** Takes this card from the draft with this serial, on the next frame. */
        void pick(int serial, UpgradeKind kind) {
            pickSerial = serial;
            pickKind = kind;
        }

        Machine(String name, PeerSession session, int phase) {
            this.name = name;
            this.session = session;
            this.phase = phase;
        }

        void script(int frame) {
            int step = frame + phase;
            double angle = step * 0.05;
            local.clear();
            local.set(
                    0,
                    new CommanderInput(
                                    (float) StrictMath.cos(angle),
                                    (float) StrictMath.sin(angle),
                                    80f,
                                    45f,
                                    step % 7 != 0,
                                    step % 11 == 0)
                            .acting(step % 13 == 0));
            if (step % 90 == 0) {
                Order[] orders = Order.values();
                local.order(0, orders[(step / 90) % orders.length]);
            }
            UpgradeKind taking = pickKind;
            if (taking != null) {
                local.pick(0, pickSerial, taking);
                pickKind = null;
            }
        }
    }
}
