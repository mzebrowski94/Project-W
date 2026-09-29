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
import pl.mzebrows.pwt.sim.upgrade.UpgradeKind;

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
    private int snapshotsAnswered;

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
                            .putByte(Msg.EVENT_ORDER)
                            .putInt(Bundle.orderCode(Order.FORWARD))
                            .putByte(0));
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
                            .putByte(Msg.EVENT_ORDER)
                            .putInt(Bundle.orderCode(Order.DEFENCE))
                            .putByte(0));
            frame(host);
        }
        assertThat(ticksWithOrder(Order.DEFENCE)).isEmpty();
        host.send(
                Delivery.UNRELIABLE,
                input(0, CommanderInput.IDLE)
                        .putByte(2)
                        .putInt(1)
                        .putByte(0)
                        .putByte(Msg.EVENT_ORDER)
                        .putInt(Bundle.orderCode(Order.FORWARD))
                        .putByte(0)
                        .putInt(2)
                        .putByte(0)
                        .putByte(Msg.EVENT_ORDER)
                        .putInt(Bundle.orderCode(Order.DEFENCE))
                        .putByte(0));
        for (int frame = 0; frame < 5; frame++) {
            frame(host);
        }

        List<Integer> forward = ticksWithOrder(Order.FORWARD);
        List<Integer> defence = ticksWithOrder(Order.DEFENCE);
        assertThat(forward).hasSize(1);
        assertThat(defence).containsExactly(forward.get(0) + 1);
    }

    @Test
    void sealsAPickOnceHoweverOftenItIsSentAgain() {
        RawPeer host = startedHost();

        for (int frame = 0; frame < 30; frame++) {
            host.send(Delivery.UNRELIABLE, pick(input(0, CommanderInput.IDLE).putByte(1), 1, 5, UpgradeKind.MARKSMEN));
            frame(host);
        }

        assertThat(ticksWithPick(UpgradeKind.MARKSMEN)).hasSize(1);
        assertThat(sealed.get(ticksWithPick(UpgradeKind.MARKSMEN).get(0)).pickOf(0))
                .isEqualTo(UpgradeKind.MARKSMEN);
    }

    /** One of each type per slot per tick: an order and a pick given together both land on the same tick. */
    @Test
    void sealsAnOrderAndAPickFromOneSlotOnTheSameTick() {
        RawPeer host = startedHost();

        host.send(
                Delivery.UNRELIABLE,
                pick(order(input(0, CommanderInput.IDLE).putByte(2), 1, Order.FORWARD), 2, 5, UpgradeKind.MARKSMEN));
        for (int frame = 0; frame < 5; frame++) {
            frame(host);
        }

        assertThat(ticksWithPick(UpgradeKind.MARKSMEN)).hasSize(1).isEqualTo(ticksWithOrder(Order.FORWARD));
    }

    /** Two picks from one slot: the second waits a tick, as a second order does. */
    @Test
    void sealsOnePickPerSlotPerTick() {
        RawPeer host = startedHost();

        host.send(
                Delivery.UNRELIABLE,
                pick(
                        pick(input(0, CommanderInput.IDLE).putByte(2), 1, 5, UpgradeKind.MARKSMEN),
                        2,
                        6,
                        UpgradeKind.FIRST_AID));
        for (int frame = 0; frame < 5; frame++) {
            frame(host);
        }

        List<Integer> first = ticksWithPick(UpgradeKind.MARKSMEN);
        assertThat(first).hasSize(1);
        assertThat(ticksWithPick(UpgradeKind.FIRST_AID)).containsExactly(first.get(0) + 1);
    }

    @Test
    void refusesAnInputEventOfNoType() {
        RawPeer host = startedHost();

        host.send(
                Delivery.UNRELIABLE,
                input(0, CommanderInput.IDLE)
                        .putByte(1)
                        .putInt(1)
                        .putByte(0)
                        .putByte(9)
                        .putInt(0)
                        .putByte(0));
        for (int frame = 0; frame < 5; frame++) {
            frame(host);
        }

        assertThat(sealed.values())
                .allSatisfy(bundle -> assertThat(bundle.pickOf(0)).isNull());
        assertThat(ticksWithOrder(Order.FORWARD)).isEmpty();
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

    /**
     * A peer that has nothing new to tag - starved of ticks by a gap in the network, or paused - says its
     * newest tag again with every input. How early a tag was is known when it is first heard; heard again
     * after its tick is sealed it is not late input, and was once taken for it: a 180 ms gap in the third
     * playtest raised a lead from 8 to 18.
     */
    @Test
    void judgesHowEarlyATagCameOnlyWhenItFirstHearsIt() {
        RawPeer host = startedHost();
        int tag = sequencer.sealedTick() + 3;

        for (int frame = 0; frame < 30; frame++) {
            host.send(Delivery.UNRELIABLE, input(tag, CommanderInput.IDLE).putByte(0));
            frame(host);
        }

        assertThat(marginsSentTo(host)).isNotEmpty().allMatch(margin -> margin >= 0);
    }

    @Test
    void keepsSendingNewTicksToAPeerWhoseAcknowledgementsAreLate() {
        RawPeer host = startedHost();

        for (int frame = 0; frame < 200; frame++) {
            frame(host);
        }

        assertThat(sealed.keySet())
                .as("every tick sealed, although the peer has acknowledged none")
                .containsAll(ticksUpTo(sequencer.sealedTick() - 1));
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
    void tellsEveryPeerWhyWhenTheSessionEnds() {
        RawPeer host = startedHost();
        RawPeer friend = new RawPeer(network.endpoint("friend"), port);
        frame(host, friend);

        sequencer.close();
        host.poll();
        friend.poll();

        assertThat(host.closedBecause).contains("the host left the game");
        assertThat(friend.closedBecause).contains("the host left the game");
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

    @Test
    void keepsSlotZeroForTheHostsKeyboardWhoeverConnectsFirst() {
        RawPeer early = new RawPeer(network.endpoint("early"), port);
        frame(early);
        early.send(
                Delivery.RELIABLE,
                new WireWriter(64)
                        .putByte(Msg.HELLO)
                        .putInt(Msg.PROTOCOL)
                        .putString(BuildId.current())
                        .putLong(0L));
        early.send(Delivery.RELIABLE, new WireWriter(8).putByte(Msg.SEAT).putByte(0));
        frame(early);
        frame(early);

        assertThat(slotsGiven(early)).containsExactly(1);
        startedHost();
    }

    @Test
    void sendsAJoinerNoTicksUntilItHasBuiltTheWorldItWasSent() {
        RawPeer host = startedHost();
        RawPeer joiner = joined("joiner", host);
        int snapshotTick = snapshotTickSentTo(joiner);

        for (int frame = 0; frame < 30; frame++) {
            frame(host, joiner);
        }
        List<Integer> whileLoading = ticksSentTo(joiner);
        joiner.send(Delivery.UNRELIABLE, loadedInput(1, snapshotTick));
        for (int frame = 0; frame < 3; frame++) {
            frame(host, joiner);
        }

        assertThat(whileLoading).isEmpty();
        assertThat(ticksSentTo(joiner)).first().isEqualTo(snapshotTick + 1);
    }

    @Test
    void letsGoAJoinerThatNeverBuildsItsWorldRatherThanSendingItAgainAndAgain() {
        RawPeer host = startedHost();
        RawPeer joiner = joined("joiner", host);

        for (int frame = 0; frame < config.session().historyTicks() + 60; frame++) {
            frame(host, joiner);
            answerSnapshots(host);
        }

        assertThat(joiner.closedBecause).contains("could not load the world");
        assertThat(messagesOfType(joiner, Msg.SNAPSHOT)).hasSize(1);
        assertThat(host.closedBecause).isNull();
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

    /**
     * A second machine that has said hello and asked for a seat, and been sent the world - the raw host
     * answering the sequencer's request with a snapshot of its own. It has not built that world yet.
     */
    private RawPeer joined(String name, RawPeer host) {
        RawPeer joiner = new RawPeer(network.endpoint(name), port);
        frame(host, joiner);
        joiner.send(
                Delivery.RELIABLE,
                new WireWriter(64)
                        .putByte(Msg.HELLO)
                        .putInt(Msg.PROTOCOL)
                        .putString(BuildId.current())
                        .putLong(0L));
        joiner.send(Delivery.RELIABLE, new WireWriter(8).putByte(Msg.SEAT).putByte(0));
        for (int frame = 0; frame < 3; frame++) {
            frame(host, joiner);
            answerSnapshots(host);
        }
        assertThat(messagesOfType(joiner, Msg.SNAPSHOT)).as("sent the world").hasSize(1);
        return joiner;
    }

    /** Answers every snapshot the sequencer has asked the raw host for since the last call. */
    private void answerSnapshots(RawPeer host) {
        List<byte[]> asked = messagesOfType(host, Msg.SNAPSHOT_WANTED);
        for (int i = snapshotsAnswered; i < asked.size(); i++) {
            WireReader in = new WireReader().wrap(asked.get(i), 1, asked.get(i).length - 1);
            host.send(
                    Delivery.RELIABLE,
                    new WireWriter(64)
                            .putByte(Msg.SNAPSHOT_TAKEN)
                            .putInt(in.getInt())
                            .putInt(sequencer.sealedTick())
                            .putBytes(new byte[] {1, 2, 3}));
        }
        snapshotsAnswered = asked.size();
    }

    /** What a peer says once it has built the world of {@code generation}: nothing held, nothing ordered. */
    private static WireWriter loadedInput(int generation, int acked) {
        return new WireWriter(64)
                .putByte(Msg.INPUT)
                .putInt(generation)
                .putInt(acked)
                .putInt(-1)
                .putByte(0)
                .putInt(acked + 1)
                .putByte(0)
                .putByte(0);
    }

    private static int snapshotTickSentTo(RawPeer peer) {
        byte[] snapshot = messagesOfType(peer, Msg.SNAPSHOT).getFirst();
        WireReader in = new WireReader().wrap(snapshot, 1, snapshot.length - 1);
        in.getInt();
        return in.getInt();
    }

    /** Every tick a peer has been sent, in the order it was sent them, repeats and all. */
    private static List<Integer> ticksSentTo(RawPeer peer) {
        List<Integer> ticks = new ArrayList<>();
        for (byte[] message : messagesOfType(peer, Msg.BUNDLES)) {
            WireReader in = new WireReader().wrap(message, 1, message.length - 1);
            in.getInt();
            in.getInt();
            int count = in.getByte();
            for (int i = 0; i < count; i++) {
                ticks.add(new Bundle(Players.MAX).read(in).tick());
            }
        }
        return ticks;
    }

    /** Every margin the sequencer has reported to a peer, leaving out the reports that had none. */
    private static List<Integer> marginsSentTo(RawPeer peer) {
        List<Integer> margins = new ArrayList<>();
        for (byte[] message : messagesOfType(peer, Msg.BUNDLES)) {
            WireReader in = new WireReader().wrap(message, 1, message.length - 1);
            in.getInt();
            int margin = in.getInt();
            if (margin != Msg.NO_MARGIN) {
                margins.add(margin);
            }
        }
        return margins;
    }

    private static List<byte[]> messagesOfType(RawPeer peer, int type) {
        List<byte[]> found = new ArrayList<>();
        for (byte[] message : peer.seen) {
            if ((message[0] & 0xFF) == type) {
                found.add(message);
            }
        }
        return found;
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

    private static List<Integer> ticksUpTo(int last) {
        List<Integer> ticks = new ArrayList<>();
        for (int tick = 0; tick <= last; tick++) {
            ticks.add(tick);
        }
        return ticks;
    }

    /** Every slot the sequencer has told this peer it plays, in the order it said so. */
    private List<Integer> slotsGiven(RawPeer peer) {
        List<Integer> slots = new ArrayList<>();
        for (byte[] message : peer.seen) {
            if ((message[0] & 0xFF) == Msg.SLOT) {
                slots.add(message[2] & 0xFF);
            }
        }
        return slots;
    }

    private static WireWriter order(WireWriter message, int seq, Order order) {
        return message.putInt(seq)
                .putByte(0)
                .putByte(Msg.EVENT_ORDER)
                .putInt(Bundle.orderCode(order))
                .putByte(0);
    }

    private static WireWriter pick(WireWriter message, int seq, int serial, UpgradeKind kind) {
        return message.putInt(seq)
                .putByte(0)
                .putByte(Msg.EVENT_PICK)
                .putInt(serial)
                .putByte(Bundle.pickCode(kind));
    }

    private List<Integer> ticksWithPick(UpgradeKind kind) {
        List<Integer> ticks = new ArrayList<>();
        for (Map.Entry<Integer, Bundle> entry : sealed.entrySet()) {
            if (entry.getValue().pickOf(0) == kind) {
                ticks.add(entry.getKey());
            }
        }
        return ticks;
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
        final List<byte[]> seen = new ArrayList<>();

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
            seen.add(Arrays.copyOfRange(data, offset, offset + length));
        }

        @Override
        public void closed(int closed, String reason) {
            closedBecause = reason;
        }
    }
}
