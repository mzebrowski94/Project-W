package pl.mzebrows.pwt.client;

import pl.mzebrows.pwt.sim.SimConfig;

/**
 * Builds a {@link SimConfig} from {@code game.properties}.
 *
 * <p>This lives in the client module because the simulation does no file I/O - it is handed its
 * configuration already parsed.
 */
public final class SimConfigLoader {

    private static final String FILE = "game.properties";

    private SimConfigLoader() {}

    /** Loads simulation config, falling back to {@link SimConfig#defaults()} per missing value. */
    public static SimConfig load() {
        SimConfig defaults = SimConfig.defaults();
        GameProperties properties = GameProperties.load(FILE);
        return new SimConfig(
                properties.getLong("sim.seed", defaults.seed()),
                properties.getFloat("sim.tickSeconds", defaults.tickSeconds()),
                properties.getInt("sim.maxTicksPerFrame", defaults.maxTicksPerFrame()),
                properties.getFloat("sim.worldWidth", defaults.worldWidth()),
                properties.getFloat("sim.worldHeight", defaults.worldHeight()));
    }
}
