package pl.mzebrows.pwt.net.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.net.log.NetLog;
import pl.mzebrows.pwt.net.loopback.LoopbackNetwork;
import pl.mzebrows.pwt.net.transport.Delivery;
import pl.mzebrows.pwt.net.transport.Transport;
import pl.mzebrows.pwt.net.transport.TransportListener;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.def.Order;

/**
 * The sequencer against a peer written byte by byte: what it seals, not merely that every machine
 * agrees on it. Every machine applying an order twice would still agree.
 */
class SequencerTest {

    private static final long FRAME = 16_666_667L;

    private final NetConfig config = NetConfig.defaults();
    private final LoopbackNetwork network = new LoopbackNetwork(config);
    private final Sequencer sequencer = new Sequencer(
            network.endpoint("host"),
            config,
            NetLog.silent(64),
            new SessionSetup("", "Skirmish", 1L),
            Players.MAX,
            1f / 60f,
            SessionHarness.HOST_KEY,
            BuildId.current());
    private final int port = sequencer.listen(config.port(), 0);
    private final Map<Integer, Bundle> sealed = new TreeMap<>();
    private long now;

    @AfterEach
    void close() {
        sequencer.close();
    }

    @Test
    void sealsAnOrderOnceHoweverOftenItIsSentAgain() {
        RawPeer host = startedHost();

        for (int frame = 0; frame < 30; frame++) {
            host.send(
                    Delivery.UNRELIABLE,
                    input(0, CommanderInput.IDLE)
                            .putByte(1)
                            .putInt(1)
                            .putByte(0)
                            .putByte(Bundle.orderCode(Order.FORWARD)));
            frame(host);
        }

        assertThat(ticksWithOrder(Order.FORWARD)).hasSize(1);
    }

    @Test
    void sealsOrdersInTheOrderTheyWereGivenOnePerTick() {
        RawPeer host = startedHost();

        for (int frame = 0; frame < 10; frame++) {
            // Given second, heard first: it waits for the first.
            host.send(
                    Delivery.UNRELIABLE,
                    input(0, CommanderInput.IDLE)
                            .putByte(1)
                            .putInt(2)
                            .putByte(0)
                            .putByte(Bundle.orderCode(Order.DEFENCE)));
            frame(host);
        }
        assertThat(ticksWithOrder(Order.DEFENCE)).isEmpty();
        host.send(
                Delivery.UNRELIABLE,
                input(0, CommanderInput.IDLE)
                        .putByte(2)
                        .putInt(1)
                        .putByte(0)
                        .putByte(Bundle.orderCode(Order.FORWARD))
                        .putInt(2)
                        .putByte(0)
                        .putByte(Bundle.orderCode(Order.DEFENCE)));
        for (int frame = 0; frame < 5; frame++) {
            frame(host);
        }

        List<Integer> forward = ticksWithOrder(Order.FORWARD);
        List<Integer> defence = ticksWithOrder(Order.DEFENCE);
        assertThat(forward).hasSize(1);
        assertThat(defence).containsExactly(forward.get(0) + 1);
    }

    @Test
    void repeatsTheLastInputOfAPlayerWhoseInputIsLate() {
        RawPeer host = startedHost();
        CommanderInput running = new CommanderInput(1f, 0f, 3f, 4f, true, false);
        int lastTagged = sequencer.sealedTick() + 3;
        host.send(Delivery.UNRELIABLE, input(lastTagged, running).putByte(0));

        for (int frame = 0; frame < 20; frame++) {
            frame(host);
        }

        assertThat(held(lastTagged + 10, 0)).isEqualTo(running);
    }

    @Test
    void dropsALinkThatSendsSomethingThatIsNotAMessage() {
        RawPeer host = startedHost();
        RawPeer stranger = new RawPeer(network.endpoint("stranger"), port);
        frame(host, stranger);

        stranger.send(Delivery.RELIABLE, new WireWriter(8).putByte(99));
        frame(host, stranger);
        frame(host, stranger);

        assertThat(stranger.closedBecause).contains("malformed");
        assertThat(host.closedBecause).isNull();
    }

    @Test
    void refusesAPeerSpeakingAnotherProtocol() {
        startedHost();
        RawPeer old = new RawPeer(network.endpoint("old"), port);
        old.send(
                Delivery.RELIABLE,
                new WireWriter(64)
                        .putByte(Msg.HELLO)
                        .putInt(Msg.PROTOCOL - 1)
                        .putString(BuildId.current())
                        .putLong(0L));

        frame(old);
        frame(old);

        assertThat(old.closedBecause).contains("protocol");
    }

    /** A raw host that has said hello, been welcomed and seated in slot 0. */
    private RawPeer startedHost() {
        RawPeer host = new RawPeer(network.endpoint("host"), port);
        frame(host);
        host.send(
                Delivery.RELIABLE,
                new WireWriter(64)
                        .putByte(Msg.HELLO)
                        .putInt(Msg.PROTOCOL)
                        .putString(BuildId.current())
                        .putLong(SessionHarness.HOST_KEY));
        host.send(Delivery.RELIABLE, new WireWriter(8).putByte(Msg.SEAT).putByte(0));
        for (int frame = 0; frame < 3; frame++) {
            frame(host);
        }
        assertThat(sequencer.sealedTick()).as("started").isGreaterThanOrEqualTo(0);
        return host;
    }

    /** An INPUT for slot 0 holding one tick's input at {@code tag}, missing only its event count. */
    private static WireWriter input(int tag, CommanderInput held) {
        WireWriter out = new WireWriter(128)
                .putByte(Msg.INPUT)
                .putInt(0)
                .putInt(-1)
                .putInt(tag)
                .putByte(1)
                .putByte(0);
        out.putInt(tag).putByte(1);
        Bundle.writeHeld(out, held);
        return out;
    }

    private void frame(RawPeer... peers) {
        now += FRAME;
        sequencer.pump(now);
        for (RawPeer peer : peers) {
            peer.poll();
            for (byte[] message : peer.drain()) {
                if ((message[0] & 0xFF) == Msg.BUNDLES) {
                    WireReader in = new WireReader().wrap(message, 0, message.length);
                    in.getByte();
                    in.getInt();
                    in.getInt();
                    int count = in.getByte();
                    for (int i = 0; i < count; i++) {
                        Bundle bundle = new Bundle(Players.MAX).read(in);
                        sealed.putIfAbsent(bundle.tick(), bundle);
                    }
                }
            }
        }
    }

    private List<Integer> ticksWithOrder(Order order) {
        List<Integer> ticks = new ArrayList<>();
        for (Map.Entry<Integer, Bundle> entry : sealed.entrySet()) {
            if (entry.getValue().orderOf(0) == order) {
                ticks.add(entry.getKey());
            }
        }
        return ticks;
    }

    private CommanderInput held(int tick, int slot) {
        Bundle bundle = sealed.get(tick);
        assertThat(bundle).as("tick %d sealed", tick).isNotNull();
        PlayerInputs inputs = new PlayerInputs(Players.MAX);
        bundle.applyTo(inputs);
        return inputs.get(slot);
    }

    /** One end of a link, speaking the protocol by hand. */
    private static final class RawPeer implements TransportListener {
        final Transport transport;
        final int link;
        final List<byte[]> inbox = new ArrayList<>();

        @Nullable
        String closedBecause;

        RawPeer(Transport transport, int port) {
            this.transport = transport;
            this.link = transport.connect("host", port);
        }

        void send(Delivery delivery, WireWriter message) {
            transport.send(link, delivery, message.array(), 0, message.size());
        }

        void poll() {
            transport.poll(this);
        }

        List<byte[]> drain() {
            List<byte[]> all = new ArrayList<>(inbox);
            inbox.clear();
            return all;
        }

        @Override
        public void opened(int opened, String remote) {}

        @Override
        public void received(int from, Delivery delivery, byte[] data, int offset, int length) {
            inbox.add(Arrays.copyOfRange(data, offset, offset + length));
        }

        @Override
        public void closed(int closed, String reason) {
            closedBecause = reason;
        }
    }
}
