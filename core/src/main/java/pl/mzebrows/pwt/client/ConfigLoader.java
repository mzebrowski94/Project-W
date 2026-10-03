package pl.mzebrows.pwt.client;

import pl.mzebrows.pwt.sim.ConfigFile;

/**
 * The client's one way to read a config file: find it on the classpath, bind it through its {@link
 * ConfigFile}, and say out loud what was wrong with it - one line for how many problems, then one for each,
 * every one naming the file. The shipped value stands for each, so a broken file degrades the game rather
 * than stopping it.
 */
public final class ConfigLoader {

    private ConfigLoader() {}

    /** The file {@link ConfigFile#name()} names, read from the classpath and bound. No file at all is what ships. */
    public static <T extends Record> T load(ConfigFile<T> file) {
        return bind(file, GameProperties.text(file.name()));
    }

    /** A file's text, bound, with everything wrong with it logged. */
    public static <T extends Record> T bind(ConfigFile<T> file, String text) {
        ConfigFile.Parsed<T> parsed = file.read(text);
        if (!parsed.problems().isEmpty()) {
            Log.error(
                    file.name() + ": " + parsed.problems().size() + " problem(s); the shipped value stands for each",
                    null);
        }
        for (String problem : parsed.problems()) {
            Log.error(file.name() + ": " + problem, null);
        }
        return parsed.config();
    }
}
