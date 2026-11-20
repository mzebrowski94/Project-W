package pl.mzebrows.pwt.relay;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.net.NetConfig;

final class RelayBannerTest {

    @Test
    void theBannerNamesWhatTheRelayRunsWith() {
        NetConfig config = NetConfig.defaults();

        String banner =
                String.join("\n", RelayBanner.lines(config, 24741, "abc123", "25.0.1", "/var/log/relay.log", ""));

        assertThat(banner)
                .contains("abc123")
                .contains("port 24741, TCP and UDP")
                .contains("/var/log/relay.log")
                .contains("network.properties as shipped")
                .contains("at most " + config.relay().roomsMax());
    }

    @Test
    void anOverridesFileIsNamed() {
        List<String> lines =
                RelayBanner.lines(NetConfig.defaults(), 24741, "abc123", "25.0.1", "", "/etc/relay.properties");

        assertThat(String.join("\n", lines))
                .contains("overrides from /etc/relay.properties")
                .contains("could not be opened");
    }
}
