package pl.mzebrows.pwt.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The client's half of the relay's keys (G0101, G0103): where it is, empty as shipped. */
class NetConfigRelayTest {

    @Test
    void shipsWithNoRelaySetAndThePortTheRelayListensOn() {
        NetConfig.Relay shipped = NetConfig.defaults().relay();

        assertThat(shipped.host()).isEmpty();
        assertThat(shipped.set()).isFalse();
        assertThat(shipped.port()).isEqualTo(24_741);
    }

    @Test
    void readsTheRelaysAddressFromALocalOverride() {
        // A local override that names only these keys leaves the shipped value standing for every other.
        List<String> problems = new ArrayList<>();

        NetConfig.Relay read = NetConfig.read("net.relay.host = relay.example.org \nnet.relay.port=5000", problems::add)
                .relay();

        assertThat(read.host()).isEqualTo("relay.example.org");
        assertThat(read.set()).isTrue();
        assertThat(read.port()).isEqualTo(5_000);
    }
}
