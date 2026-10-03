package pl.mzebrows.pwt.client.config;

import java.util.Map;
import pl.mzebrows.pwt.client.Log;
import pl.mzebrows.pwt.sim.ConfigBinder;
import pl.mzebrows.pwt.sim.ConfigFile;
import pl.mzebrows.pwt.sim.SimConfig;

/**
 * Builds a {@link SimConfig} from {@code assets/sim/*.properties}.
 *
 * <p>This lives in the client module because the simulation does no file I/O - it is handed its
 * configuration as text. It is read as every config file is ({@link ConfigLoader}, {@code SimConfig.FILE});
 * what is left here is that it is several files read as one text, the text a host ships without its
 * comments, and how a joiner reports the host's.
 */
public final class SimConfigLoader {

    /** Where the files are on the classpath; which files, and in what order, the simulation says. */
    private static final String DIRECTORY = "sim/";

    private SimConfigLoader() {}

    /** Loads simulation config, falling back to {@link SimConfig#defaults()} per missing value. */
    public static SimConfig load() {
        return loaded().config();
    }

    /**
     * Loads simulation config and keeps the text it was bound from, from one read of the files - so a host
     * ships the world config it plays, never a later read of a file edited since (ADR 0012).
     */
    public static Loaded loaded() {
        String text = text();
        return new Loaded(parse(text), withoutComments(text));
    }

    /**
     * A config and the text it was bound from.
     *
     * @param shipped the text as the parser reads it, comments left out - what a host ships
     */
    public record Loaded(SimConfig config, String shipped) {}

    /**
     * Every file in {@link SimConfig#shippedFiles()}, as written and in that order, as one text - what a
     * host ships to its peers (ADR 0012).
     */
    public static String text() {
        StringBuilder text = new StringBuilder();
        for (String file : SimConfig.shippedFiles()) {
            text.append(GameProperties.text(DIRECTORY + file)).append('\n');
        }
        return text.toString();
    }

    /**
     * {@link #text()} as the parser reads it - every key with its value, one a line - and none of the
     * comments, which are seven tenths of the file, mean nothing to a peer, and are what a join over a slow
     * link waits for.
     */
    static String withoutComments(String text) {
        StringBuilder kept = new StringBuilder(text.length() / 3);
        for (Map.Entry<String, String> entry : ConfigBinder.parse(text).entrySet()) {
            kept.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
        return kept.toString();
    }

    /**
     * Simulation config from the text of the {@code sim/*.properties} files: this machine's, or the one a
     * host shipped. Empty text - no file at all, already logged where it was looked for - is what ships.
     */
    public static SimConfig parse(String text) {
        return ConfigLoader.bind(SimConfig.FILE, text);
    }

    /**
     * Simulation config from the world config a host shipped. Its problems get one line, naming whose they
     * are: the host logged each against its own files, and the build id says this parse finds the same.
     */
    public static SimConfig parseFromHost(String text) {
        ConfigFile.Parsed<SimConfig> parsed = SimConfig.FILE.read(text);
        if (!parsed.problems().isEmpty()) {
            Log.info("The host's world config: " + parsed.problems().size()
                    + " problem(s), the shipped value stands for each");
        }
        return parsed.config();
    }
}
