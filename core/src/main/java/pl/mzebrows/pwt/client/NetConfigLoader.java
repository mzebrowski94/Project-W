package pl.mzebrows.pwt.client;

import java.util.List;
import pl.mzebrows.pwt.net.NetConfig;

/**
 * Builds a {@link NetConfig} from {@code network.properties}.
 *
 * <p>Here for the reason {@link SimConfigLoader} is: binding the text is {@code net}'s own ({@link
 * NetConfig#parse}); what is left here is finding the file and saying out loud what was wrong with it. A
 * key the records do not have, or a value that is missing or unreadable, is logged and the shipped value
 * is used. The shipped file itself is held to stricter terms by {@code ShippedNetConfigTest}.
 *
 * <p>One override: the {@code pwt.net.logDir} system property, which the Gradle run task sets so that a
 * game started from the repo logs into {@code ai/logs/net} rather than beside itself.
 */
public final class NetConfigLoader {

    private static final String FILE = "network.properties";

    /** The system property that overrides where the connection log goes. */
    public static final String LOG_DIR_PROPERTY = "pwt.net.logDir";

    private NetConfigLoader() {}

    /** The one-way latencies a joining copy steps through with F3 for the latency playtest. */
    public static List<Integer> latencySteps() {
        return load().latencySteps();
    }

    /** Loads network config, falling back to {@link NetConfig#defaults()} per missing value. */
    public static NetConfig load() {
        NetConfig config = parse(GameProperties.text(FILE));
        String logDir = System.getProperty(LOG_DIR_PROPERTY);
        if (logDir == null) {
            return config;
        }
        NetConfig.LogConfig log = config.log();
        return config.with(new NetConfig.LogConfig(logDir, log.level(), log.traceSize(), log.statsSeconds()));
    }

    /** Network config from the text of a {@code network.properties}. Empty text - no file at all - is what ships. */
    public static NetConfig parse(String text) {
        if (text.isBlank()) {
            return NetConfig.defaults();
        }
        NetConfig.Parsed parsed = NetConfig.parse(text);
        if (!parsed.problems().isEmpty()) {
            Log.error(FILE + ": " + parsed.problems().size() + " problem(s); the shipped value stands for each", null);
        }
        for (String problem : parsed.problems()) {
            Log.error(FILE + ": " + problem, null);
        }
        return parsed.config();
    }
}
