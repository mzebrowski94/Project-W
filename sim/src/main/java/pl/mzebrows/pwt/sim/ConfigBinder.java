package pl.mzebrows.pwt.sim;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.def.AiChance;
import pl.mzebrows.pwt.sim.def.Chances;
import pl.mzebrows.pwt.sim.def.Defs;

/**
 * Fills a config record from {@code key=value} text by the names of its components, so a tunable is a
 * record field and a line in {@code game.properties} and nothing else - no literal default, no loader
 * line to keep in step.
 *
 * <p>A component is read from {@code <prefix>.<name>}, or from its {@link ConfigKey}. What each type
 * reads as:
 *
 * <ul>
 *   <li>numbers and {@code true}/{@code false} as written;
 *   <li>an enum as its constant's name in lower case, and a {@code @Nullable} one as {@code none} for
 *       nothing;
 *   <li>a nested record as a block of keys under its own name - and a block the file says nothing
 *       about at all, of a record that has a static {@code off()}, {@code none()} or {@code clear()},
 *       as that: "zero means off" for whole abilities, so a strain that does not leap needs no leap
 *       block;
 *   <li>a {@link Defs} table as one block per kind, named by the kind in lower case;
 *   <li>a sealed interface of records as the record's name and its one argument, if it has one: {@code
 *       mutate brute}, {@code feed 1}, {@code nothing};
 *   <li>{@link Chances} as one key per {@link AiChance} plus {@code longestGap}, each taken from the
 *       shared block {@value #SHARED_CHANCES} when a strain's own block does not name it.
 * </ul>
 *
 * <p>Every key is accounted for both ways: a key no record has is reported as unknown, and a value
 * the text does not give is reported as missing. With a fallback text - the shipped file behind a
 * player's own - a missing or unreadable value is taken from the fallback and still reported.
 *
 * <p>Reflection, once, while a world is being set up; never during a tick (ADR 0014).
 */
final class ConfigBinder {

    /** The block a strain's chances fall back to, key by key. */
    static final String SHARED_CHANCES = "sim.ai.chance";

    private static final String NOTHING = "none";
    private static final String LONGEST_GAP = "longestGap";
    private static final List<String> OFF_FACTORIES = List.of("off", "none", "clear");

    /** What a text bound to, and everything wrong with it. The value is null only if something is. */
    record Bound<T>(@Nullable T value, List<String> problems) {}

    private final SortedMap<String, String> values;
    private final SortedMap<String, String> fallback;
    private final List<String> problems = new ArrayList<>();
    private final Set<String> used = new TreeSet<>();

    private ConfigBinder(SortedMap<String, String> values, SortedMap<String, String> fallback) {
        this.values = values;
        this.fallback = fallback;
    }

    /**
     * {@code key=value} lines, sorted by key. Blank lines and lines starting {@code #} or {@code !} are
     * comments; keys and values are trimmed.
     */
    static SortedMap<String, String> parse(String text) {
        SortedMap<String, String> parsed = new TreeMap<>();
        for (String line : text.lines().toList()) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                continue;
            }
            int equals = trimmed.indexOf('=');
            if (equals < 0) {
                parsed.put(trimmed, "");
            } else {
                parsed.put(
                        trimmed.substring(0, equals).strip(),
                        trimmed.substring(equals + 1).strip());
            }
        }
        return parsed;
    }

    /** Binds with no fallback, and refuses the whole text if anything at all is wrong with it. */
    static <T extends Record> T bindStrictly(Class<T> type, String root, SortedMap<String, String> values) {
        Bound<T> bound = bind(type, root, values, new TreeMap<>());
        T value = bound.value();
        if (value == null || !bound.problems().isEmpty()) {
            throw new IllegalArgumentException("config: " + String.join("; ", bound.problems()));
        }
        return value;
    }

    /** Binds, taking whatever {@code values} lacks or garbles from {@code fallback}, and says what that was. */
    static <T extends Record> Bound<T> bind(
            Class<T> type, String root, SortedMap<String, String> values, SortedMap<String, String> fallback) {
        ConfigBinder binder = new ConfigBinder(values, fallback);
        Object value = binder.record(root, type);
        for (String key : values.keySet()) {
            if (!binder.used.contains(key)) {
                binder.problems.add("unknown key " + key);
            }
        }
        return new Bound<>(type.cast(value), List.copyOf(binder.problems));
    }

    private @Nullable Object value(String key, Type type, boolean nullable) {
        Class<?> raw = rawClass(type);
        if (raw.isRecord()) {
            return record(key, raw);
        }
        if (raw == Defs.class) {
            return table(key, (ParameterizedType) type);
        }
        if (raw == Chances.class) {
            return chances(key);
        }
        return leaf(key, raw, nullable);
    }

    private @Nullable Object record(String key, Class<?> type) {
        @Nullable Method off = offFactory(type);
        if (off != null && !mentions(values, key) && !mentions(fallback, key)) {
            return invoke(off);
        }
        RecordComponent[] components = type.getRecordComponents();
        @Nullable Object[] arguments = new Object[components.length];
        Class<?>[] types = new Class<?>[components.length];
        int before = problems.size();
        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            types[i] = component.getType();
            arguments[i] = value(
                    key + "." + keyOf(component),
                    component.getGenericType(),
                    component.getAnnotatedType().isAnnotationPresent(Nullable.class));
        }
        try {
            Constructor<?> constructor = type.getDeclaredConstructor(types);
            constructor.setAccessible(true);
            return constructor.newInstance(arguments);
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            // Only worth saying if nothing below it already has: a block built from a placeholder
            // fails its own checks, and that is the missing value's fault, not the block's.
            if (problems.size() == before) {
                problems.add(key + ": " + reason(e));
            }
            return null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private @Nullable Object table(String key, ParameterizedType type) {
        Class<? extends Enum> kinds = (Class<? extends Enum>) type.getActualTypeArguments()[0];
        Type definition = type.getActualTypeArguments()[1];
        Defs.Builder builder = Defs.builder(kinds);
        boolean complete = true;
        for (Enum<?> kind : kinds.getEnumConstants()) {
            Object def = value(key + "." + lower(kind), definition, false);
            if (def == null) {
                complete = false;
            } else {
                builder.put(kind, def);
            }
        }
        return complete ? builder.build() : null;
    }

    private Chances chances(String key) {
        Chances chances = Chances.standard();
        for (AiChance kind : AiChance.values()) {
            chances = chances.with(kind, (Float) own(key, lower(kind), float.class));
        }
        return chances.withLongestGap((Float) own(key, LONGEST_GAP, float.class));
    }

    /** A strain's own value if its block names one, and the shared block's if it does not. */
    private Object own(String key, String name, Class<?> type) {
        String own = key + "." + name;
        String shared = SHARED_CHANCES + "." + name;
        boolean named = values.containsKey(own) || fallback.containsKey(own);
        Object value = leaf(named ? own : shared, type, false);
        return value == null ? 0f : value;
    }

    private @Nullable Object leaf(String key, Class<?> type, boolean nullable) {
        used.add(key);
        String written = values.get(key);
        if (written != null) {
            try {
                return convert(written, type, nullable);
            } catch (IllegalArgumentException e) {
                problems.add("cannot read " + key + "=" + written + " as " + type.getSimpleName());
            }
        } else {
            problems.add("missing " + key);
        }
        String shipped = fallback.get(key);
        if (shipped != null) {
            return convert(shipped, type, nullable);
        }
        return placeholder(type);
    }

    private static @Nullable Object convert(String text, Class<?> type, boolean nullable) {
        if (type == float.class) {
            return Float.parseFloat(text);
        }
        if (type == int.class) {
            return Integer.parseInt(text);
        }
        if (type == long.class) {
            return Long.parseLong(text);
        }
        if (type == boolean.class) {
            if (!text.equals("true") && !text.equals("false")) {
                throw new IllegalArgumentException("not true or false: " + text);
            }
            return Boolean.parseBoolean(text);
        }
        if (type.isEnum()) {
            if (nullable && text.equals(NOTHING)) {
                return null;
            }
            return enumConstant(type, text);
        }
        if (type.isInterface() && type.isSealed()) {
            return sealed(type, text);
        }
        throw new IllegalArgumentException("no reading for " + type.getSimpleName());
    }

    /** The permitted record whose name is the first word, built from the rest if it takes an argument. */
    private static Object sealed(Class<?> type, String text) {
        int space = text.indexOf(' ');
        String name = space < 0 ? text : text.substring(0, space);
        String argument = space < 0 ? "" : text.substring(space + 1).strip();
        for (Class<?> permitted : type.getPermittedSubclasses()) {
            if (!permitted.getSimpleName().toLowerCase(Locale.ROOT).equals(name)) {
                continue;
            }
            RecordComponent[] components = permitted.getRecordComponents();
            if (components.length > 1 || (components.length == 1) == argument.isEmpty()) {
                throw new IllegalArgumentException(name + " takes " + components.length + " argument(s)");
            }
            try {
                if (components.length == 0) {
                    return permitted.getDeclaredConstructor().newInstance();
                }
                Class<?> parameter = components[0].getType();
                return permitted.getDeclaredConstructor(parameter).newInstance(convert(argument, parameter, false));
            } catch (ReflectiveOperationException e) {
                throw new IllegalArgumentException(reason(e), e);
            }
        }
        throw new IllegalArgumentException("no " + type.getSimpleName() + " called " + name);
    }

    /** What a constructor objected to, rather than the reflection that carried the objection. */
    private static String reason(Exception e) {
        Throwable cause = e instanceof InvocationTargetException && e.getCause() != null ? e.getCause() : e;
        return String.valueOf(cause.getMessage());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumConstant(Class<?> type, String text) {
        return Enum.valueOf((Class<? extends Enum>) type, text.toUpperCase(Locale.ROOT));
    }

    private static @Nullable Object placeholder(Class<?> type) {
        if (type == float.class) {
            return 0f;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type.isEnum()) {
            return type.getEnumConstants()[0];
        }
        return null;
    }

    private static @Nullable Method offFactory(Class<?> type) {
        for (String name : OFF_FACTORIES) {
            try {
                Method method = type.getDeclaredMethod(name);
                if (Modifier.isStatic(method.getModifiers()) && method.getReturnType() == type) {
                    return method;
                }
            } catch (NoSuchMethodException e) {
                // Not this one; try the next name.
            }
        }
        return null;
    }

    private static Object invoke(Method factory) {
        try {
            factory.setAccessible(true);
            return factory.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot call " + factory, e);
        }
    }

    /** Whether any key lies under this block. */
    private static boolean mentions(SortedMap<String, String> map, String key) {
        String prefix = key + ".";
        SortedMap<String, String> tail = map.tailMap(prefix);
        return !tail.isEmpty() && tail.firstKey().startsWith(prefix);
    }

    private static String keyOf(RecordComponent component) {
        ConfigKey renamed = component.getAnnotation(ConfigKey.class);
        return renamed == null ? component.getName() : renamed.value();
    }

    private static String lower(Enum<?> constant) {
        return constant.name().toLowerCase(Locale.ROOT);
    }

    private static Class<?> rawClass(Type type) {
        if (type instanceof ParameterizedType parameterized) {
            return (Class<?>) parameterized.getRawType();
        }
        return (Class<?>) type;
    }
}
