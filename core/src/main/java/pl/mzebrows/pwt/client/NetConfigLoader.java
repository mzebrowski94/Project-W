package pl.mzebrows.pwt.client;

import pl.mzebrows.pwt.net.NetConfig;

/**
 * Builds a {@link NetConfig} from {@code network.properties}.
 *
 * <p>Read as every config file is ({@link ConfigLoader}, {@code NetConfig.FILE}); what is left here is one
 * override.
 *
 * <p>One override: the {@code pwt.net.logDir} system property, which the Gradle run task sets so that a
 * game started from the repo logs into {@code ai/logs/net} rather than beside itself.
 */
public final class NetConfigLoader {

    /** The system property that overrides where the connection log goes. */
    public static final String LOG_DIR_PROPERTY = "pwt.net.logDir";

    private NetConfigLoader() {}

    /** Loads network config, falling back to {@link NetConfig#defaults()} per missing value. */
    public static NetConfig load() {
        NetConfig config = ConfigLoader.load(NetConfig.FILE);
        String logDir = System.getProperty(LOG_DIR_PROPERTY);
        if (logDir == null) {
            return config;
        }
        NetConfig.LogConfig log = config.log();
        return config.with(new NetConfig.LogConfig(logDir, log.level(), log.traceSize(), log.statsSeconds()));
    }
}
