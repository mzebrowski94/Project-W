package pl.mzebrows.pwt.sim;

import java.util.List;
import java.util.SortedMap;
import org.jspecify.annotations.Nullable;

/**
 * One config file and the record it binds to - the one way every tunable file is read: {@code
 * sim/*.properties}, {@code network.properties}, {@code graphic.properties} and {@code controls.properties}.
 *
 * <p>What ships is compiled in and bound strictly, once, on first use, so a broken shipped file names the key
 * rather than a class. A player's text is bound against it: no text at all is what ships, and every value
 * the text lacks or garbles, every key no record has and every key given twice is named, with the shipped
 * value standing for it. Saying so out loud is the caller's - this module has no log.
 *
 * @param <T> the record the file binds to
 */
public final class ConfigFile<T extends Record> {

    private final String name;
    private final Class<T> type;
    private final String root;
    private final String shippedText;
    private final ConfigBinder.Conversions conversions;

    private @Nullable SortedMap<String, String> shippedValues;
    private @Nullable T defaults;

    /**
     * @param name what a complaint names: the file, or the files, as a player finds them
     * @param root the prefix every key of the file starts with
     * @param shippedText the shipped file, compiled in
     * @param conversions how to read what the binder cannot by itself
     */
    public ConfigFile(
            String name, Class<T> type, String root, String shippedText, ConfigBinder.Conversions conversions) {
        this.name = name;
        this.type = type;
        this.root = root;
        this.shippedText = shippedText;
        this.conversions = conversions;
    }

    /** A config read from text, and everything that had to be taken from what ships instead. */
    public record Parsed<T>(T config, List<String> problems) {}

    public String name() {
        return name;
    }

    /** What ships, bound strictly: what every test runs, and what a player's file falls back to. */
    public T defaults() {
        T bound = defaults;
        if (bound == null) {
            bound = ConfigBinder.bindStrictly(type, root, shippedText, conversions);
            defaults = bound;
        }
        return bound;
    }

    /** What ships, key by key. */
    public SortedMap<String, String> shippedValues() {
        SortedMap<String, String> values = shippedValues;
        if (values == null) {
            values = ConfigBinder.parse(shippedText);
            shippedValues = values;
        }
        return values;
    }

    /**
     * A config from a file's text - a player's own, or the one a host shipped (ADR 0012). Blank text, no file
     * at all, is what ships.
     *
     * @throws IllegalArgumentException if the values are readable but break a rule a record checks
     */
    public Parsed<T> read(String text) {
        if (text.isBlank()) {
            return new Parsed<>(defaults(), List.of());
        }
        ConfigBinder.Bound<T> bound = ConfigBinder.bind(type, root, text, shippedValues(), conversions);
        T config = bound.value();
        if (config == null) {
            throw new IllegalArgumentException(name + ": " + String.join("; ", bound.problems()));
        }
        return new Parsed<>(config, bound.problems());
    }
}
