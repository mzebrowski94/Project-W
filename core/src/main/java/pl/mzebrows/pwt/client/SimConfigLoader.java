package pl.mzebrows.pwt.client;

import java.util.Map;
import pl.mzebrows.pwt.sim.ConfigBinder;
import pl.mzebrows.pwt.sim.SimConfig;

/**
 * Builds a {@link SimConfig} from {@code game.properties}.
 *
 * <p>This lives in the client module because the simulation does no file I/O - it is handed its
 * configuration as text. Binding the text is the simulation's own ({@link SimConfig#parse}); what is
 * left here is finding the file and saying out loud what was wrong with it. A key the records do not
 * have, or a value that is missing or unreadable, is logged and the shipped value is used, so an edited
 * or outdated file degrades the game rather than stopping it. The shipped file itself is held to
 * stricter terms by {@code ShippedConfigTest}.
 */
public final class SimConfigLoader {

    private static final String FILE = "game.properties";

    private SimConfigLoader() {}

    /** Loads simulation config, falling back to {@link SimConfig#defaults()} per missing value. */
    public static SimConfig load() {
        return parse(text());
    }

    /** {@code game.properties} as written - what a host ships to its peers (ADR 0012). */
    public static String text() {
        return GameProperties.text(FILE);
    }

    /**
     * What a host ships to its peers (ADR 0012): {@link #text()} as the parser reads it - every key with
     * its value, one a line - and none of the comments, which are seven tenths of the file, mean nothing
     * to a peer, and are what a join over a slow link waits for.
     */
    public static String shipped() {
        return withoutComments(text());
    }

    static String withoutComments(String text) {
        StringBuilder kept = new StringBuilder(text.length() / 3);
        for (Map.Entry<String, String> entry : ConfigBinder.parse(text).entrySet()) {
            kept.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
        return kept.toString();
    }

    /**
     * Simulation config from the text of a {@code game.properties}: this machine's, or the one a host
     * shipped. Empty text - no file at all, already logged where it was looked for - is what ships.
     */
    public static SimConfig parse(String text) {
        if (text.isBlank()) {
            return SimConfig.defaults();
        }
        SimConfig.Parsed parsed = SimConfig.parse(text);
        if (!parsed.problems().isEmpty()) {
            Log.error(FILE + ": " + parsed.problems().size() + " problem(s); the shipped value stands for each", null);
        }
        for (String problem : parsed.problems()) {
            Log.error(FILE + ": " + problem, null);
        }
        return parsed.config();
    }
}
