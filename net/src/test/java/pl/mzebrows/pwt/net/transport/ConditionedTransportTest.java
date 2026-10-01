package pl.mzebrows.pwt.net.transport;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.net.ManualClock;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.net.NetConfig.Conditions;
import pl.mzebrows.pwt.net.loopback.LoopbackNetwork;

class ConditionedTransportTest {

    private static final int PORT = 47_100;
    private static final int MESSAGES = 1_000;

    @Test
    void holdsAMessageBackForTheLatency() {
        Rig rig = Rig.with(new Conditions(50, 0, 0f, 0f, 1L));

        rig.send(Delivery.RELIABLE, 1);
        List<RecordingListener.Heard> early = rig.pollReceived();
        rig.clock.advanceMillis(50);
        List<RecordingListener.Heard> onTime = rig.pollReceived();

        assertThat(early).isEmpty();
        assertThat(onTime).hasSize(1);
    }

    @Test
    void keepsReliableMessagesInTheOrderTheyWereSentHoweverTheyAreDelayed() {
        Rig rig = Rig.with(new Conditions(10, 40, 0f, 0f, 3L));

        for (int i = 0; i < 20; i++) {
            rig.send(Delivery.RELIABLE, i);
            rig.clock.advanceMillis(1);
        }
        rig.clock.advanceMillis(100);

        assertThat(rig.pollReceived())
                .extracting(RecordingListener.Heard::firstByte)
                .containsExactlyElementsOf(IntStream.range(0, 20).boxed().toList());
    }

    @Test
    void losesRoughlyTheFractionOfUnreliableMessagesItIsToldTo() {
        Rig rig = Rig.with(new Conditions(0, 0, 0.3f, 0f, 5L));

        for (int i = 0; i < MESSAGES; i++) {
            rig.send(Delivery.UNRELIABLE, i);
        }

        assertThat(rig.pollReceived()).hasSizeBetween(600, 800);
    }

    @Test
    void neverLosesAReliableMessage() {
        Rig rig = Rig.with(new Conditions(0, 0, 0.9f, 0f, 5L));

        for (int i = 0; i < MESSAGES; i++) {
            rig.send(Delivery.RELIABLE, i);
        }

        assertThat(rig.pollReceived()).hasSize(MESSAGES);
    }

    @Test
    void sendsSomeUnreliableMessagesTwice() {
        Rig rig = Rig.with(new Conditions(0, 0, 0f, 0.2f, 5L));

        for (int i = 0; i < MESSAGES; i++) {
            rig.send(Delivery.UNRELIABLE, i);
        }

        assertThat(rig.pollReceived()).hasSizeBetween(1_100, 1_300);
    }

    @Test
    void misbehavesTheSameWayTwiceUnderTheSameSeed() {
        Conditions bad = new Conditions(5, 30, 0.25f, 0.1f, 42L);

        assertThat(arrivals(bad)).isEqualTo(arrivals(bad));
    }

    /**
     * A relay or a radio that stops for a moment holds everything and then lets it all go at once - the
     * 2026-10-01 playtest's Hamachi link - which independent jitter per message never does.
     */
    @Test
    void holdsEverythingSentDuringASpikeAndLetsItGoAtOnce() {
        Rig rig = Rig.with(new Conditions(0, 0, 0f, 0f, 1000, 200, 9L));
        int longestGap = 0;
        int gap = 0;
        int biggestBurst = 0;
        int bursts = 0;

        for (int ms = 0; ms < 5000; ms++) {
            rig.send(Delivery.UNRELIABLE, ms & 0x7F);
            rig.clock.advanceMillis(1);
            int heard = rig.pollReceived().size();
            gap = heard == 0 ? gap + 1 : 0;
            longestGap = Math.max(longestGap, gap);
            biggestBurst = Math.max(biggestBurst, heard);
            bursts += heard > 100 ? 1 : 0;
        }

        assertThat(longestGap).as("longest silence, ms").isBetween(199, 201);
        assertThat(biggestBurst).as("most heard at once").isBetween(199, 201);
        assertThat(bursts).as("spikes in five seconds at one a second").isBetween(3, 7);
    }

    @Test
    void passesEverythingStraightThroughUnderNoConditions() {
        Rig rig = Rig.with(Conditions.NONE);

        rig.send(Delivery.UNRELIABLE, 1);

        assertThat(rig.pollReceived()).hasSize(1);
    }

    @Test
    void holdsAMessageComingTheOtherWayBackToo() {
        Rig rig = Rig.with(new Conditions(50, 0, 0f, 0f, 1L));

        rig.reply(Delivery.RELIABLE, 1);
        List<RecordingListener.Heard> early = rig.pollReplies();
        rig.clock.advanceMillis(50);
        List<RecordingListener.Heard> onTime = rig.pollReplies();

        assertThat(early).isEmpty();
        assertThat(onTime).hasSize(1);
    }

    @Test
    void losesUnreliableMessagesComingTheOtherWayToo() {
        Rig rig = Rig.with(new Conditions(0, 0, 0.3f, 0f, 5L));

        for (int i = 0; i < MESSAGES; i++) {
            rig.reply(Delivery.UNRELIABLE, i);
        }

        assertThat(rig.pollReplies()).hasSizeBetween(600, 800);
    }

    @Test
    void takesNewConditionsWhileItRuns() {
        Rig rig = Rig.with(Conditions.NONE);
        rig.send(Delivery.RELIABLE, 1);
        assertThat(rig.pollReceived()).hasSize(1);

        rig.joiner.conditions(new Conditions(30, 0, 0f, 0f, 1L));
        rig.send(Delivery.RELIABLE, 2);

        assertThat(rig.pollReceived()).isEmpty();
        rig.clock.advanceMillis(30);
        assertThat(rig.pollReceived())
                .extracting(RecordingListener.Heard::firstByte)
                .containsExactly(2);
    }

    private static List<Integer> arrivals(Conditions conditions) {
        Rig rig = Rig.with(conditions);
        for (int i = 0; i < 100; i++) {
            rig.send(Delivery.UNRELIABLE, i);
            rig.clock.advanceMillis(1);
        }
        rig.clock.advanceMillis(100);
        return rig.pollReceived().stream()
                .map(RecordingListener.Heard::firstByte)
                .toList();
    }

    /** A joiner whose sends go through the conditions, and a host that hears them. */
    private static final class Rig {
        final ManualClock clock = new ManualClock();
        final Transport host;
        final ConditionedTransport joiner;
        final int link;
        final int hostLink;

        private Rig(Conditions conditions) {
            LoopbackNetwork network = new LoopbackNetwork(NetConfig.defaults());
            host = network.endpoint("host");
            joiner = new ConditionedTransport(network.endpoint("joiner"), conditions, clock);
            host.listen(PORT, 0);
            link = joiner.connect("host", PORT);
            RecordingListener opened = new RecordingListener();
            host.poll(opened);
            hostLink = opened.of("opened").get(0).link();
            joiner.poll(new RecordingListener());
        }

        static Rig with(Conditions conditions) {
            return new Rig(conditions);
        }

        void send(Delivery delivery, int value) {
            joiner.send(link, delivery, new byte[] {(byte) value}, 0, 1);
        }

        void reply(Delivery delivery, int value) {
            host.send(hostLink, delivery, new byte[] {(byte) value}, 0, 1);
        }

        List<RecordingListener.Heard> pollReplies() {
            RecordingListener heard = new RecordingListener();
            joiner.poll(heard);
            return heard.of("received");
        }

        List<RecordingListener.Heard> pollReceived() {
            joiner.poll(new RecordingListener());
            RecordingListener heard = new RecordingListener();
            host.poll(heard);
            return heard.of("received");
        }
    }
}
