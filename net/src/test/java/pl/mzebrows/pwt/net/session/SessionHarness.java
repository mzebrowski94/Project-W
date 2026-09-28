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
import pl.mzebrows.pwt.net.transport.Transport;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.WorldCodec;
import pl.mzebrows.pwt.sim.def.Order;

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
    private int frame;

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
        Transport hostEnd = conditioned(network.endpoint("host"), atTheHost);
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

    Machine join(String name, Conditions conditions, long key, @Nullable Path dumpDir, String build, int phase) {
        Transport end = conditioned(network.endpoint(name), conditions);
        PeerSession session = new PeerSession(end, config, NetLog.silent(64), new TestWorlds(), dumpDir, build);
        session.connect("host", port, key);
        session.seat(0);
        Machine machine = new Machine(name, session, phase);
        machines.add(machine);
        return machine;
    }

    /** Runs this many frames. */
    void run(int frames) {
        for (int i = 0; i < frames; i++) {
            clock.advanceNanos(FRAME_NANOS);
            long now = clock.nanos();
            sequencer.pump(now);
            for (Machine machine : machines) {
                if (machine.frozenFrames > 0) {
                    machine.frozenFrames--;
                    continue;
                }
                machine.script(frame);
                machine.session.pump(now, machine.local);
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

    /** One computer in the session, with one player on its keyboard walking a circle and giving orders. */
    static final class Machine {
        final String name;
        final PeerSession session;
        final PlayerInputs local = new PlayerInputs(Players.MAX);
        final int phase;

        /** Frames this machine's game will not run at all - a window being dragged, a pause for GC. */
        int frozenFrames;

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
        }
    }
}
