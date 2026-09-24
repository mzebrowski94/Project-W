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

    @Test
    void passesEverythingStraightThroughUnderNoConditions() {
        Rig rig = Rig.with(Conditions.NONE);

        rig.send(Delivery.UNRELIABLE, 1);

        assertThat(rig.pollReceived()).hasSize(1);
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
        final Transport joiner;
        final int link;

        private Rig(Conditions conditions) {
            LoopbackNetwork network = new LoopbackNetwork(NetConfig.defaults());
            host = network.endpoint("host");
            joiner = new ConditionedTransport(network.endpoint("joiner"), conditions, clock);
            host.listen(PORT, 0);
            link = joiner.connect("host", PORT);
            host.poll(new RecordingListener());
            joiner.poll(new RecordingListener());
        }

        static Rig with(Conditions conditions) {
            return new Rig(conditions);
        }

        void send(Delivery delivery, int value) {
            joiner.send(link, delivery, new byte[] {(byte) value}, 0, 1);
        }

        List<RecordingListener.Heard> pollReceived() {
            joiner.poll(new RecordingListener());
            RecordingListener heard = new RecordingListener();
            host.poll(heard);
            return heard.of("received");
        }
    }
}
