package pl.mzebrows.pwt.net.loopback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.net.transport.Delivery;
import pl.mzebrows.pwt.net.transport.RecordingListener;
import pl.mzebrows.pwt.net.transport.Transport;

class LoopbackNetworkTest {

    private static final int PORT = 47_100;

    @Test
    void opensALinkAtBothEnds() {
        LoopbackNetwork network = new LoopbackNetwork(NetConfig.defaults());
        Transport host = network.endpoint("host");
        Transport joiner = network.endpoint("joiner");
        host.listen(PORT, 0);
        RecordingListener atHost = new RecordingListener();
        RecordingListener atJoiner = new RecordingListener();

        int link = joiner.connect("host", PORT);
        host.poll(atHost);
        joiner.poll(atJoiner);

        assertThat(atJoiner.of("opened"))
                .singleElement()
                .extracting(RecordingListener.Heard::link)
                .isEqualTo(link);
        assertThat(atHost.of("opened")).hasSize(1);
    }

    @Test
    void refusesALinkNobodyIsListeningFor() {
        LoopbackNetwork network = new LoopbackNetwork(NetConfig.defaults());
        Transport joiner = network.endpoint("joiner");
        RecordingListener heard = new RecordingListener();

        int link = joiner.connect("host", PORT);
        joiner.poll(heard);

        assertThat(heard.heard()).singleElement().satisfies(h -> {
            assertThat(h.kind()).isEqualTo("closed");
            assertThat(h.link()).isEqualTo(link);
            assertThat(h.text()).contains("refused");
        });
    }

    @Test
    void carriesBothKindsOfMessageAcross() {
        Pair pair = Pair.open();

        pair.joiner.send(pair.joinerLink, Delivery.RELIABLE, new byte[] {7}, 0, 1);
        pair.joiner.send(pair.joinerLink, Delivery.UNRELIABLE, new byte[] {9}, 0, 1);
        RecordingListener heard = new RecordingListener();
        pair.host.poll(heard);

        assertThat(heard.of("received"))
                .extracting(RecordingListener.Heard::delivery, RecordingListener.Heard::firstByte)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(Delivery.RELIABLE, 7),
                        org.assertj.core.groups.Tuple.tuple(Delivery.UNRELIABLE, 9));
    }

    @Test
    void copiesWhatItIsSentSoTheCallerMayReuseItsArray() {
        Pair pair = Pair.open();
        byte[] reused = {1};

        pair.joiner.send(pair.joinerLink, Delivery.RELIABLE, reused, 0, 1);
        reused[0] = 2;
        RecordingListener heard = new RecordingListener();
        pair.host.poll(heard);

        assertThat(heard.of("received"))
                .singleElement()
                .extracting(RecordingListener.Heard::firstByte)
                .isEqualTo(1);
    }

    @Test
    void closesTheOtherEndWhenOneEndDisconnectsAndSaysNothingMoreAboutIt() {
        Pair pair = Pair.open();

        pair.joiner.disconnect(pair.joinerLink, "leaving");
        pair.joiner.send(pair.joinerLink, Delivery.RELIABLE, new byte[] {1}, 0, 1);
        RecordingListener atHost = new RecordingListener();
        RecordingListener atJoiner = new RecordingListener();
        pair.host.poll(atHost);
        pair.joiner.poll(atJoiner);

        assertThat(atHost.heard())
                .singleElement()
                .satisfies(h -> assertThat(h.text()).contains("leaving"));
        assertThat(atJoiner.heard())
                .singleElement()
                .extracting(RecordingListener.Heard::kind)
                .isEqualTo("closed");
    }

    /** Two instances on one machine: the second host finds the next port rather than failing. */
    @Test
    void givesASecondHostOnTheSameMachineTheNextPort() {
        LoopbackNetwork network = new LoopbackNetwork(NetConfig.defaults());
        int first = network.endpoint("pc").listen(PORT, 8);

        int second = network.endpoint("pc").listen(PORT, 8);

        assertThat(first).isEqualTo(PORT);
        assertThat(second).isEqualTo(PORT + 1);
    }

    @Test
    void failsLoudlyWhenEveryPortInTheRangeIsTaken() {
        LoopbackNetwork network = new LoopbackNetwork(NetConfig.defaults());
        network.endpoint("pc").listen(PORT, 0);

        assertThatThrownBy(() -> network.endpoint("pc").listen(PORT, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(String.valueOf(PORT));
    }

    @Test
    void refusesAnUnreliableMessageLongerThanADatagramMayBe() {
        Pair pair = Pair.open();
        int tooLong = NetConfig.defaults().maxDatagramBytes() + 1;

        assertThatThrownBy(() -> pair.joiner.send(pair.joinerLink, Delivery.UNRELIABLE, new byte[tooLong], 0, tooLong))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** A host and a joiner with one open link between them, every opening event already polled away. */
    private record Pair(Transport host, Transport joiner, int joinerLink) {

        static Pair open() {
            LoopbackNetwork network = new LoopbackNetwork(NetConfig.defaults());
            Transport host = network.endpoint("host");
            Transport joiner = network.endpoint("joiner");
            host.listen(PORT, 0);
            int link = joiner.connect("host", PORT);
            host.poll(new RecordingListener());
            joiner.poll(new RecordingListener());
            return new Pair(host, joiner, link);
        }
    }
}
