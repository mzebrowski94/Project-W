package pl.mzebrows.pwt.sim;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectIntMap;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.component.Components;

/**
 * The whole world written down, and read back (ADR 0014): what a player who drops in is sent, what a
 * peer that has desynced is healed with, and - hashed rather than written - the checksum peers compare.
 *
 * <p><strong>Derived from the components, not written for them.</strong> When this class loads it builds
 * a plan for every class in {@link Components#ALL}: its fields, every one including the private ones,
 * sorted by name, each with a way to write and read it. After that a snapshot is a walk - every stream,
 * then every entity in spawn order (ADR 0013), then each entity's components in registry order - and a
 * new component or a new field needs nothing here at all. The field types a plan can handle are the
 * ones {@code ArchitectureTest.componentFieldsAreWhatASnapshotCanWrite} allows: primitives, enums, an
 * {@code Entity} (written as its place in spawn order), records made of those, and the named lists of
 * records {@code ArchitectureTest.NAMED_COLLECTIONS} admits - the level's schedule and lair, and the
 * upgrade ledger, drafts and presence.
 *
 * <p><strong>One walk, two uses.</strong> The same walk writes bytes for a snapshot or feeds a hash for a
 * checksum, so the two can never disagree about what the world is.
 *
 * <p>A snapshot is restored into a {@link pl.mzebrows.pwt.sim.scenario.Scenarios#blank blank} world -
 * the map rebuilt from the seed, and nothing standing on it but the level entity, whose components the
 * snapshot overwrites. Everything else is spawned from the snapshot in the order it was written, so every
 * family comes out in the order the original held it.
 */
public final class WorldCodec {

    /** "PWS1": what a snapshot starts with, so something that is not one is refused rather than misread. */
    private static final int MAGIC = 0x5057_5331;

    /** The root stream and one per {@link RngStream} - how many longs a snapshot opens with. */
    private static final int STREAMS = 1 + RngStream.values().length;

    private static final ClassPlan[] PLANS;
    private static final ObjectIntMap<Class<?>> IDS = new ObjectIntMap<>();

    static {
        List<Class<? extends Component>> all = Components.ALL;
        PLANS = new ClassPlan[all.size()];
        for (int id = 0; id < all.size(); id++) {
            PLANS[id] = new ClassPlan(all.get(id));
            IDS.put(all.get(id), id);
        }
    }

    private WorldCodec() {}

    /** The world as bytes. */
    public static byte[] snapshot(Simulation simulation) {
        ByteSink sink = new ByteSink();
        walk(simulation, sink);
        return sink.bytes();
    }

    /** The world as one number: what two peers compare to know they are still in step. */
    public static long hash(Simulation simulation) {
        HashSink sink = new HashSink();
        walk(simulation, sink);
        return sink.value();
    }

    /**
     * The hash of a snapshot already written - equal to {@link #hash(Simulation)} of the world it was
     * written from, so a peer that keeps a snapshot for a checksum does not walk the world twice.
     */
    public static long hash(byte[] snapshot) {
        if (snapshot.length % 4 != 0) {
            throw new IllegalStateException("a snapshot is whole ints, and this is " + snapshot.length + " bytes");
        }
        HashSink sink = new HashSink();
        Source in = new Source(snapshot);
        while (in.remaining() > 0) {
            sink.putInt(in.getInt());
        }
        return sink.value();
    }

    /**
     * Where two snapshots differ, as lines a person can read - {@code entity 41 Position.x: 3.25 vs
     * 3.2500002} - at most {@code most} of them. What a desync report is made of: the field that went wrong
     * first usually names the system that wrote it.
     *
     * <p>Entities are named by their place in spawn order and components by class. If the two worlds do
     * not even hold the same things - an entity more, a component missing - the list says where they stop
     * lining up and ends there.
     */
    public static List<String> differences(byte[] first, byte[] second, int most) {
        List<String[]> left = describe(first);
        List<String[]> right = describe(second);
        List<String> found = new ArrayList<>();
        int common = Math.min(left.size(), right.size());
        for (int i = 0; i < common && found.size() < most; i++) {
            String[] a = left.get(i);
            String[] b = right.get(i);
            if (!a[0].equals(b[0])) {
                found.add("the worlds stop lining up at " + a[0] + " vs " + b[0]);
                return found;
            }
            if (!a[1].equals(b[1])) {
                found.add(a[0] + ": " + a[1] + " vs " + b[1]);
            }
        }
        if (left.size() != right.size() && found.size() < most) {
            found.add("one world holds " + left.size() + " values, the other " + right.size());
        }
        return found;
    }

    /** Every value in a snapshot as a label and its text, in the order it was written. */
    private static List<String[]> describe(byte[] snapshot) {
        Source in = new Source(snapshot);
        if (in.getInt() != MAGIC) {
            throw new IllegalStateException("not a world snapshot");
        }
        List<String[]> lines = new ArrayList<>();
        for (int s = 0; s < STREAMS; s++) {
            lines.add(new String[] {"stream " + s, Long.toHexString(in.getLong())});
        }
        int count = in.getInt();
        lines.add(new String[] {"entities", String.valueOf(count)});
        for (int e = 0; e < count; e++) {
            int components = in.getInt();
            for (int c = 0; c < components; c++) {
                int id = in.getInt();
                if (id < 0 || id >= PLANS.length) {
                    throw new IllegalStateException("no component numbered " + id);
                }
                ClassPlan plan = PLANS[id];
                lines.add(new String[] {"entity " + e + " component " + c, plan.type.getSimpleName()});
                plan.describe(in, "entity " + e + " " + plan.type.getSimpleName(), lines);
            }
        }
        return lines;
    }

    /**
     * Makes a blank world into the one a snapshot describes.
     *
     * @throws IllegalStateException if the world is not blank, or the bytes are not a snapshot this build
     *     wrote
     */
    public static void restore(Simulation blank, byte[] snapshot) {
        Source in = new Source(snapshot);
        if (in.getInt() != MAGIC) {
            throw new IllegalStateException("not a world snapshot");
        }
        for (Rng stream : blank.streams().inOrder()) {
            stream.restore(in.getLong());
        }
        PooledEngine engine = blank.engine();
        ImmutableArray<Entity> existing = engine.getEntities();
        if (existing.size() != 1) {
            throw new IllegalStateException(
                    "a snapshot is restored into a blank world, which has one entity - this has " + existing.size());
        }
        int count = in.getInt();
        Entity[] made = new Entity[count];
        List<Deferred> references = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            boolean level = i == 0;
            Entity entity = level ? existing.get(0) : engine.createEntity();
            int components = in.getInt();
            for (int c = 0; c < components; c++) {
                int id = in.getInt();
                ClassPlan plan = PLANS[id];
                Component component = level ? entity.getComponent(plan.type) : engine.createComponent(plan.type);
                if (component == null) {
                    throw new IllegalStateException("the level entity has no " + plan.type.getSimpleName());
                }
                plan.read(component, in, references);
                if (!level) {
                    entity.add(component);
                }
            }
            made[i] = entity;
        }
        for (Deferred reference : references) {
            reference.resolve(made);
        }
        for (int i = 1; i < count; i++) {
            engine.addEntity(made[i]);
        }
        if (in.remaining() != 0) {
            throw new IllegalStateException(in.remaining() + " bytes left over after the snapshot");
        }
    }

    private static void walk(Simulation simulation, Sink sink) {
        sink.putInt(MAGIC);
        for (Rng stream : simulation.streams().inOrder()) {
            sink.putLong(stream.state());
        }
        ImmutableArray<Entity> entities = simulation.engine().getEntities();
        sink.putInt(entities.size());
        int[] ids = new int[PLANS.length];
        for (int i = 0; i < entities.size(); i++) {
            ImmutableArray<Component> components = entities.get(i).getComponents();
            int present = components.size();
            for (int c = 0; c < present; c++) {
                ids[c] = IDS.get(components.get(c).getClass(), -1);
                if (ids[c] < 0) {
                    throw new IllegalStateException(
                            components.get(c).getClass().getSimpleName() + " is missing from Components.ALL");
                }
            }
            Arrays.sort(ids, 0, present);
            sink.putInt(present);
            for (int c = 0; c < present; c++) {
                ClassPlan plan = PLANS[ids[c]];
                sink.putInt(ids[c]);
                plan.write(entities.get(i).getComponent(plan.type), sink, entities);
            }
        }
    }

    /** How one field is written. */
    private enum Kind {
        FLOAT,
        DOUBLE,
        INT,
        LONG,
        BOOLEAN,
        ENUM,
        ENTITY,
        RECORD,
        RECORD_ARRAY
    }

    /** How to write and read one component class. */
    private static final class ClassPlan {
        final Class<? extends Component> type;
        final Field[] fields;
        final Kind[] kinds;
        final @Nullable RecordPlan[] records;

        ClassPlan(Class<? extends Component> type) {
            this.type = type;
            List<Field> found = new ArrayList<>();
            for (Field field : type.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    found.add(field);
                }
            }
            found.sort(Comparator.comparing(Field::getName));
            fields = found.toArray(new Field[0]);
            kinds = new Kind[fields.length];
            records = new RecordPlan[fields.length];
            for (int f = 0; f < fields.length; f++) {
                Class<?> fieldType = fields[f].getType();
                kinds[f] = kindOf(fieldType, fields[f]);
                if (kinds[f] == Kind.RECORD) {
                    records[f] = RecordPlan.of(fieldType);
                } else if (kinds[f] == Kind.RECORD_ARRAY) {
                    records[f] = RecordPlan.of(elementOf(fields[f]));
                }
            }
        }

        void write(Component component, Sink sink, ImmutableArray<Entity> entities) {
            try {
                for (int f = 0; f < fields.length; f++) {
                    Field field = fields[f];
                    switch (kinds[f]) {
                        case FLOAT -> sink.putInt(Float.floatToRawIntBits(field.getFloat(component)));
                        case DOUBLE -> sink.putLong(Double.doubleToRawLongBits(field.getDouble(component)));
                        case INT -> sink.putInt(field.getInt(component));
                        case LONG -> sink.putLong(field.getLong(component));
                        case BOOLEAN -> sink.putInt(field.getBoolean(component) ? 1 : 0);
                        case ENUM -> sink.putInt(ordinalOf((Enum<?>) field.get(component)));
                        case ENTITY -> {
                            Entity target = (Entity) field.get(component);
                            sink.putInt(target == null ? -1 : entities.indexOf(target, true));
                        }
                        case RECORD -> recordPlan(f).write(field.get(component), sink);
                        case RECORD_ARRAY -> {
                            Array<?> array = (Array<?>) field.get(component);
                            int size = array == null ? 0 : array.size;
                            sink.putInt(size);
                            for (int i = 0; i < size; i++) {
                                recordPlan(f).write(array.get(i), sink);
                            }
                        }
                    }
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("cannot read " + type.getSimpleName(), e);
            }
        }

        @SuppressWarnings("unchecked")
        void read(Component component, Source in, List<Deferred> references) {
            try {
                for (int f = 0; f < fields.length; f++) {
                    Field field = fields[f];
                    switch (kinds[f]) {
                        case FLOAT -> field.setFloat(component, Float.intBitsToFloat(in.getInt()));
                        case DOUBLE -> field.setDouble(component, Double.longBitsToDouble(in.getLong()));
                        case INT -> field.setInt(component, in.getInt());
                        case LONG -> field.setLong(component, in.getLong());
                        case BOOLEAN -> field.setBoolean(component, in.getInt() != 0);
                        case ENUM -> field.set(component, enumOf(field.getType(), in.getInt()));
                        case ENTITY -> references.add(new Deferred(field, component, in.getInt()));
                        case RECORD -> field.set(component, recordPlan(f).read(in));
                        case RECORD_ARRAY -> {
                            Array<Object> array = (Array<Object>) field.get(component);
                            if (array == null) {
                                throw new IllegalStateException(
                                        type.getSimpleName() + "." + field.getName() + " is null");
                            }
                            array.clear();
                            int size = in.getInt();
                            for (int i = 0; i < size; i++) {
                                array.add(recordPlan(f).read(in));
                            }
                        }
                    }
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("cannot write " + type.getSimpleName(), e);
            }
        }

        void describe(Source in, String prefix, List<String[]> lines) {
            for (int f = 0; f < fields.length; f++) {
                String label = prefix + "." + fields[f].getName();
                switch (kinds[f]) {
                    case RECORD -> recordPlan(f).describe(in, label, lines);
                    case RECORD_ARRAY -> {
                        int size = in.getInt();
                        lines.add(new String[] {label + ".size", String.valueOf(size)});
                        for (int i = 0; i < size; i++) {
                            recordPlan(f).describe(in, label + "[" + i + "]", lines);
                        }
                    }
                    default -> lines.add(new String[] {label, text(kinds[f], fields[f].getType(), in)});
                }
            }
        }

        private RecordPlan recordPlan(int f) {
            RecordPlan plan = records[f];
            if (plan == null) {
                throw new IllegalStateException("no record plan for " + fields[f].getName());
            }
            return plan;
        }

        private static Class<?> elementOf(Field field) {
            if (field.getGenericType() instanceof ParameterizedType generic
                    && generic.getActualTypeArguments()[0] instanceof Class<?> element
                    && element.isRecord()) {
                return element;
            }
            throw new IllegalStateException(field + " is a collection of something a snapshot cannot write");
        }
    }

    /** How to write and read one record type, recursively. */
    private static final class RecordPlan {
        final RecordComponent[] parts;
        /** The record's own fields, in component order: read without boxing, so a checksum allocates nothing. */
        final Field[] fields;

        final Kind[] kinds;
        final @Nullable RecordPlan[] nested;
        final Constructor<?> canonical;

        private RecordPlan(Class<?> type) {
            parts = type.getRecordComponents();
            fields = new Field[parts.length];
            kinds = new Kind[parts.length];
            nested = new RecordPlan[parts.length];
            Class<?>[] types = new Class<?>[parts.length];
            for (int p = 0; p < parts.length; p++) {
                types[p] = parts[p].getType();
                kinds[p] = kindOf(types[p], null);
                if (kinds[p] == Kind.RECORD) {
                    nested[p] = new RecordPlan(types[p]);
                }
                try {
                    fields[p] = type.getDeclaredField(parts[p].getName());
                } catch (NoSuchFieldException e) {
                    throw new IllegalStateException("no field behind " + parts[p], e);
                }
                fields[p].setAccessible(true);
            }
            try {
                canonical = type.getDeclaredConstructor(types);
                canonical.setAccessible(true);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("no canonical constructor on " + type, e);
            }
        }

        static RecordPlan of(Class<?> type) {
            return new RecordPlan(type);
        }

        void write(@Nullable Object value, Sink sink) {
            sink.putInt(value == null ? 0 : 1);
            if (value == null) {
                return;
            }
            try {
                for (int p = 0; p < parts.length; p++) {
                    Field field = fields[p];
                    switch (kinds[p]) {
                        case FLOAT -> sink.putInt(Float.floatToRawIntBits(field.getFloat(value)));
                        case DOUBLE -> sink.putLong(Double.doubleToRawLongBits(field.getDouble(value)));
                        case INT -> sink.putInt(field.getInt(value));
                        case LONG -> sink.putLong(field.getLong(value));
                        case BOOLEAN -> sink.putInt(field.getBoolean(value) ? 1 : 0);
                        case ENUM -> sink.putInt(ordinalOf((Enum<?>) field.get(value)));
                        case RECORD -> nestedPlan(p).write(field.get(value), sink);
                        case ENTITY, RECORD_ARRAY -> throw new IllegalStateException("not in a record");
                    }
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(
                        "cannot read a " + value.getClass().getSimpleName(), e);
            }
        }

        @Nullable
        Object read(Source in) {
            if (in.getInt() == 0) {
                return null;
            }
            @Nullable Object[] values = new Object[parts.length];
            for (int p = 0; p < parts.length; p++) {
                values[p] = switch (kinds[p]) {
                    case FLOAT -> Float.intBitsToFloat(in.getInt());
                    case DOUBLE -> Double.longBitsToDouble(in.getLong());
                    case INT -> in.getInt();
                    case LONG -> in.getLong();
                    case BOOLEAN -> in.getInt() != 0;
                    case ENUM -> enumOf(parts[p].getType(), in.getInt());
                    case RECORD -> nestedPlan(p).read(in);
                    case ENTITY, RECORD_ARRAY -> throw new IllegalStateException("not in a record");
                };
            }
            try {
                return canonical.newInstance(values);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(
                        "cannot rebuild a " + canonical.getDeclaringClass().getSimpleName(), e);
            }
        }

        void describe(Source in, String prefix, List<String[]> lines) {
            if (in.getInt() == 0) {
                lines.add(new String[] {prefix, "null"});
                return;
            }
            for (int p = 0; p < parts.length; p++) {
                String label = prefix + "." + parts[p].getName();
                if (kinds[p] == Kind.RECORD) {
                    nestedPlan(p).describe(in, label, lines);
                } else {
                    lines.add(new String[] {label, text(kinds[p], parts[p].getType(), in)});
                }
            }
        }

        private RecordPlan nestedPlan(int p) {
            RecordPlan plan = nested[p];
            if (plan == null) {
                throw new IllegalStateException("no plan for " + parts[p].getName());
            }
            return plan;
        }
    }

    private static Kind kindOf(Class<?> type, @Nullable Field field) {
        if (type == float.class) {
            return Kind.FLOAT;
        } else if (type == double.class) {
            return Kind.DOUBLE;
        } else if (type == int.class || type == short.class || type == byte.class || type == char.class) {
            if (type != int.class) {
                throw new IllegalStateException("only int of the narrow integer types is written: " + field);
            }
            return Kind.INT;
        } else if (type == long.class) {
            return Kind.LONG;
        } else if (type == boolean.class) {
            return Kind.BOOLEAN;
        } else if (type.isEnum()) {
            return Kind.ENUM;
        } else if (type == Entity.class) {
            return Kind.ENTITY;
        } else if (type.isRecord()) {
            return Kind.RECORD;
        } else if (type == Array.class && field != null) {
            return Kind.RECORD_ARRAY;
        }
        throw new IllegalStateException(
                "a snapshot cannot write " + type.getName() + (field == null ? "" : " in " + field));
    }

    /** One value as text, read from where it was written. */
    private static String text(Kind kind, Class<?> type, Source in) {
        return switch (kind) {
            case FLOAT -> String.valueOf(Float.intBitsToFloat(in.getInt()));
            case DOUBLE -> String.valueOf(Double.longBitsToDouble(in.getLong()));
            case INT -> String.valueOf(in.getInt());
            case LONG -> String.valueOf(in.getLong());
            case BOOLEAN -> String.valueOf(in.getInt() != 0);
            case ENUM -> String.valueOf(enumOf(type, in.getInt()));
            case ENTITY -> {
                int index = in.getInt();
                yield index < 0 ? "none" : "entity " + index;
            }
            case RECORD, RECORD_ARRAY -> throw new IllegalStateException("not a single value: " + type);
        };
    }

    @SuppressWarnings("EnumOrdinal") // read back through the same enum in the same build - the handshake says so
    private static int ordinalOf(@Nullable Enum<?> value) {
        return value == null ? 0 : value.ordinal() + 1;
    }

    private static @Nullable Object enumOf(Class<?> type, int code) {
        if (code == 0) {
            return null;
        }
        Object[] constants = type.getEnumConstants();
        if (constants == null || code - 1 >= constants.length) {
            throw new IllegalStateException(type.getSimpleName() + " has no constant " + (code - 1));
        }
        return constants[code - 1];
    }

    /** An entity reference, filled in once every entity it could point at exists. */
    private record Deferred(Field field, Object owner, int index) {
        void resolve(Entity[] made) {
            try {
                field.set(owner, index < 0 ? null : made[index]);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("cannot set " + field, e);
            }
        }
    }

    /** Where a walk puts what it finds. */
    private interface Sink {
        void putInt(int value);

        void putLong(long value);
    }

    private static final class ByteSink implements Sink {
        private byte[] bytes = new byte[64 * 1024];
        private int size;

        @Override
        public void putInt(int value) {
            ensure(4);
            for (int shift = 24; shift >= 0; shift -= 8) {
                bytes[size++] = (byte) (value >>> shift);
            }
        }

        @Override
        public void putLong(long value) {
            putInt((int) (value >>> 32));
            putInt((int) value);
        }

        byte[] bytes() {
            return Arrays.copyOf(bytes, size);
        }

        private void ensure(int more) {
            if (size + more > bytes.length) {
                bytes = Arrays.copyOf(bytes, Math.max(bytes.length * 2, size + more));
            }
        }
    }

    /** A 64-bit hash: every value mixed in with SplitMix64's finaliser, so a one-bit change moves everything. */
    private static final class HashSink implements Sink {
        private long value = 0x9E37_79B9_7F4A_7C15L;

        @Override
        public void putLong(long word) {
            putInt((int) (word >>> 32));
            putInt((int) word);
        }

        /** Every value goes in as the ints a snapshot writes it as, so hashing the bytes gives the same number. */
        @Override
        public void putInt(int word) {
            long z = value ^ (word & 0xFFFF_FFFFL);
            z = (z ^ (z >>> 30)) * 0xBF58_476D_1CE4_E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D0_49BB_1331_11EBL;
            value = Long.rotateLeft(z ^ (z >>> 31), 17) + 0x632B_E59B_D9B4_E019L;
        }

        long value() {
            return value;
        }
    }

    private static final class Source {
        private final byte[] bytes;
        private int at;

        Source(byte[] bytes) {
            this.bytes = bytes;
        }

        int getInt() {
            if (at + 4 > bytes.length) {
                throw new IllegalStateException("the snapshot ends early");
            }
            int value = 0;
            for (int i = 0; i < 4; i++) {
                value = (value << 8) | (bytes[at++] & 0xFF);
            }
            return value;
        }

        long getLong() {
            return ((long) getInt() << 32) | (getInt() & 0xFFFF_FFFFL);
        }

        int remaining() {
            return bytes.length - at;
        }
    }
}
