package pl.mzebrows.pwt.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.jspecify.annotations.Nullable;

/**
 * Reads a {@code .properties} file from the classpath (the {@code assets/} folder is on it).
 *
 * <p>Every accessor falls back to a supplied default and logs, so a missing or malformed config file
 * degrades the game rather than crashing it.
 */
public final class GameProperties {

    private final String source;
    private final Properties properties;

    private GameProperties(String source, Properties properties) {
        this.source = source;
        this.properties = properties;
    }

    /** Loads the named classpath resource; returns an empty instance if it cannot be read. */
    public static GameProperties load(String resourceName) {
        return parse(resourceName, text(resourceName));
    }

    /**
     * The named classpath resource as it is written, or empty if it cannot be read - what a host ships
     * to the peers of an online session (ADR 0012), so everybody plays the host's tuning. Read as
     * ISO-8859-1, which is what {@link Properties} reads a file as.
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

    /** Reads properties from text rather than a file; {@code source} names it in any complaint. */
    public static GameProperties parse(String source, String text) {
        Properties parsed = new Properties();
        try {
            parsed.load(new StringReader(text));
        } catch (IOException | IllegalArgumentException e) {
            Log.error("Could not parse config " + source + "; using defaults", e);
        }
        return new GameProperties(source, parsed);
    }

    public int getInt(String key, int fallback) {
        String raw = value(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return reportBadValue(key, raw, fallback);
        }
    }

    public long getLong(String key, long fallback) {
        String raw = value(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return reportBadValue(key, raw, fallback);
        }
    }

    public float getFloat(String key, float fallback) {
        String raw = value(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException e) {
            return reportBadValue(key, raw, fallback);
        }
    }

    public boolean getBoolean(String key, boolean fallback) {
        String raw = value(key);
        return raw == null ? fallback : Boolean.parseBoolean(raw);
    }

    public String getString(String key, String fallback) {
        String raw = value(key);
        return raw == null ? fallback : raw;
    }

    private @Nullable String value(String key) {
        String raw = properties.getProperty(key);
        return raw == null || raw.isBlank() ? null : raw.trim();
    }

    private <T> T reportBadValue(String key, String raw, T fallback) {
        Log.error(source + ": " + key + "='" + raw + "' is not a number; using " + fallback, null);
        return fallback;
    }
}
