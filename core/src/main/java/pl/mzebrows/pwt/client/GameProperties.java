package pl.mzebrows.pwt.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Reads a file off the classpath (the {@code assets/} folder is on it), as it is written.
 *
 * <p>What every config file is read through ({@link ConfigLoader}), and the words the player reads ({@link
 * UiStrings}). A file that is missing or cannot be read is logged and read as empty, which every reader takes
 * as what ships - so a broken install degrades the game rather than crashing it.
 */
public final class GameProperties {

    private GameProperties() {}

    /**
     * The named classpath resource as it is written, or empty if it cannot be read. Read as ISO-8859-1, the
     * encoding a {@code .properties} file is written in.
     */
    public static String text(String resourceName) {
        return text(resourceName, StandardCharsets.ISO_8859_1);
    }

    /** The named classpath resource in the given encoding - UTF-8 for text a player reads. */
    public static String text(String resourceName, Charset charset) {
        try (InputStream stream = GameProperties.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (stream == null) {
                Log.error("Config " + resourceName + " not found on classpath; using defaults", null);
                return "";
            }
            Log.debug("Loaded config " + resourceName);
            return new String(stream.readAllBytes(), charset);
        } catch (IOException e) {
            Log.error("Could not read config " + resourceName + "; using defaults", e);
            return "";
        }
    }
}
