package pl.mzebrows.pwt.client;

import java.io.IOException;
import java.io.InputStream;
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
        Properties loaded = new Properties();
        try (InputStream stream = GameProperties.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (stream == null) {
                Log.error("Config " + resourceName + " not found on classpath; using defaults", null);
            } else {
                loaded.load(stream);
                Log.debug("Loaded config " + resourceName);
            }
        } catch (IOException e) {
            Log.error("Could not read config " + resourceName + "; using defaults", e);
        }
        return new GameProperties(resourceName, loaded);
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
