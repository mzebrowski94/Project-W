package pl.mzebrows.pwt.client;

import java.util.ArrayList;
import java.util.List;
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

    /** The simulated one-way latencies a joining copy steps through with F3, when the key is missing. */
    public static final List<Integer> LATENCY_STEPS = List.of(0, 25, 50, 100);

    /**
     * {@code net.conditions.latencySteps}: the simulated one-way latencies, in milliseconds, a joining
     * copy of the game steps through with F3 for the latency playtest. The rest of the conditions stay as
     * configured. A bad entry drops the key back to {@link #LATENCY_STEPS}.
     */
    public static List<Integer> latencySteps() {
        String text = GameProperties.load(FILE).getString("net.conditions.latencySteps", "");
        if (text.isBlank()) {
            return LATENCY_STEPS;
        }
        List<Integer> steps = new ArrayList<>();
        for (String part : text.split(",", -1)) {
            try {
                int millis = Integer.parseInt(part.trim());
                if (millis < 0) {
                    throw new NumberFormatException("negative");
                }
                steps.add(millis);
            } catch (NumberFormatException e) {
                Log.error(
                        FILE + ": net.conditions.latencySteps='" + text + "' is not a list of milliseconds; using "
                                + LATENCY_STEPS,
                        null);
                return LATENCY_STEPS;
            }
        }
        return List.copyOf(steps);
    }

    /** Loads network config, falling back to {@link NetConfig#defaults()} per missing value. */
    public static NetConfig load() {
        NetConfig defaults = NetConfig.defaults();
        GameProperties properties = GameProperties.load(FILE);
        NetConfig.Conditions noConditions = defaults.conditions();
        NetConfig.LogConfig log = defaults.log();
        NetConfig.Session session = defaults.session();
        NetConfig.Lan lan = defaults.lan();
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
                        properties.getInt("net.log.statsSeconds", log.statsSeconds())),
                new NetConfig.Session(
                        properties.getInt("net.session.leadMinTicks", session.leadMinTicks()),
                        properties.getInt("net.session.leadMaxTicks", session.leadMaxTicks()),
                        properties.getInt("net.session.marginTicks", session.marginTicks()),
                        properties.getInt("net.session.bufferTicks", session.bufferTicks()),
                        properties.getInt("net.session.catchUpTicks", session.catchUpTicks()),
                        properties.getInt("net.session.checksumTicks", session.checksumTicks()),
                        properties.getInt("net.session.historyTicks", session.historyTicks())),
                new NetConfig.Lan(
                        properties.getInt("net.lan.port", lan.port()),
                        properties.getInt("net.lan.announceMillis", lan.announceMillis()),
                        properties.getInt("net.lan.forgetMillis", lan.forgetMillis())));
    }
}
