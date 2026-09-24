package pl.mzebrows.pwt.client;

import pl.mzebrows.pwt.net.NetConfig;

/**
 * Builds a {@link NetConfig} from {@code network.properties}.
 *
 * <p>Here rather than in {@code net} for the reason {@link SimConfigLoader} is here: the modules below
 * the client are handed their configuration already parsed.
 *
 * <p>One override: the {@code pwt.net.logDir} system property, which the Gradle run task sets so that a
 * game started from the repo logs into {@code ai/logs/net} rather than beside itself.
 */
public final class NetConfigLoader {

    private static final String FILE = "network.properties";

    /** The system property that overrides where the connection log goes. */
    public static final String LOG_DIR_PROPERTY = "pwt.net.logDir";

    private NetConfigLoader() {}

    /** Loads network config, falling back to {@link NetConfig#defaults()} per missing value. */
    public static NetConfig load() {
        NetConfig defaults = NetConfig.defaults();
        GameProperties properties = GameProperties.load(FILE);
        NetConfig.Conditions noConditions = defaults.conditions();
        NetConfig.LogConfig log = defaults.log();
        return new NetConfig(
                properties.getInt("net.port", defaults.port()),
                properties.getInt("net.portSearch", defaults.portSearch()),
                properties.getInt("net.connectTimeoutMillis", defaults.connectTimeoutMillis()),
                properties.getInt("net.linkTimeoutMillis", defaults.linkTimeoutMillis()),
                properties.getInt("net.heartbeatMillis", defaults.heartbeatMillis()),
                properties.getInt("net.maxDatagramBytes", defaults.maxDatagramBytes()),
                properties.getInt("net.maxMessageBytes", defaults.maxMessageBytes()),
                new NetConfig.Conditions(
                        properties.getInt("net.conditions.latencyMillis", noConditions.latencyMillis()),
                        properties.getInt("net.conditions.jitterMillis", noConditions.jitterMillis()),
                        properties.getFloat("net.conditions.loss", noConditions.loss()),
                        properties.getFloat("net.conditions.duplicate", noConditions.duplicate()),
                        properties.getLong("net.conditions.seed", noConditions.seed())),
                new NetConfig.LogConfig(
                        System.getProperty(LOG_DIR_PROPERTY, properties.getString("net.log.dir", log.dir())),
                        properties.getString("net.log.level", log.level()),
                        properties.getInt("net.log.traceSize", log.traceSize()),
                        properties.getInt("net.log.statsSeconds", log.statsSeconds())));
    }
}
