package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.assertj.core.api.SoftAssertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import pl.mzebrows.pwt.sim.component.Components;

/**
 * Every pooled component, after {@code reset()}, is indistinguishable from a new one.
 *
 * <p>{@link ComponentPoolingTest} guards two components by hand; this guards all of them by
 * construction, so a field added tomorrow is covered without anybody remembering to. Offline a field
 * {@code reset()} forgot is a stale value in the next entity to reuse the instance. Online it is a
 * desync: the host recycled the component and a peer that has just dropped in built a fresh one
 * (ADR 0014).
 *
 * <p>Every field is dirtied through reflection - private ones included, because a snapshot is the
 * whole component - then {@code reset()} is called and the result compared with a freshly built
 * instance, field by field.
 */
class ComponentResetTest {

    @Test
    void findsTheComponents() {
        assertThat(components()).hasSizeGreaterThan(40);
    }

    /** A component missing from the registry is one no snapshot can carry (ADR 0014). */
    @Test
    void numbersEveryComponentForTheSnapshot() {
        assertThat(Components.ALL.stream().map(Class::getName))
                .containsExactlyInAnyOrderElementsOf(
                        components().map(Class::getName).toList());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("components")
    void resetsEveryFieldToWhatANewOneHolds(Class<?> type) throws ReflectiveOperationException {
        Object fresh = type.getDeclaredConstructor().newInstance();
        Object used = type.getDeclaredConstructor().newInstance();
        List<Field> fields = fieldsOf(type);
        for (Field field : fields) {
            dirty(field, used, field.get(fresh));
        }

        ((Pool.Poolable) used).reset();

        SoftAssertions softly = new SoftAssertions();
        for (Field field : fields) {
            softly.assertThat(comparable(field.get(used)))
                    .as("%s.%s after reset()", type.getSimpleName(), field.getName())
                    .isEqualTo(comparable(field.get(fresh)));
        }
        softly.assertAll();
    }

    static Stream<Class<?>> components() {
        List<Class<?>> found = new ArrayList<>();
        for (JavaClass javaClass : new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("pl.mzebrows.pwt.sim.component")) {
            Class<?> type = javaClass.reflect();
            if (Component.class.isAssignableFrom(type)
                    && Pool.Poolable.class.isAssignableFrom(type)
                    && !type.isInterface()
                    && !Modifier.isAbstract(type.getModifiers())
                    && type.getEnclosingClass() == null) {
                found.add(type);
            }
        }
        found.sort(Comparator.comparing(Class::getSimpleName));
        return found.stream();
    }

    private static List<Field> fieldsOf(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);
                fields.add(field);
            }
        }
        return fields;
    }

    /** Sets a field to something a new instance does not hold, whatever its type. */
    private static void dirty(Field field, Object target, @Nullable Object freshValue)
            throws ReflectiveOperationException {
        Class<?> type = field.getType();
        if (type == float.class) {
            field.setFloat(target, (Float) freshValue + 1.5f);
        } else if (type == double.class) {
            field.setDouble(target, (Double) freshValue + 1.5);
        } else if (type == int.class) {
            field.setInt(target, (Integer) freshValue + 7);
        } else if (type == long.class) {
            field.setLong(target, (Long) freshValue + 7L);
        } else if (type == boolean.class) {
            field.setBoolean(target, !(Boolean) freshValue);
        } else if (type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            Object other =
                    Objects.equals(constants[0], freshValue) && constants.length > 1 ? constants[1] : constants[0];
            field.set(target, Objects.equals(other, freshValue) ? null : other);
        } else if (type == Entity.class) {
            field.set(target, new Entity());
        } else if (type == Array.class) {
            addOneTo((Array<?>) field.get(target));
        } else if (type.isRecord()) {
            field.set(target, differentRecord(type, freshValue));
        } else {
            throw new AssertionError(field + " has a type this test does not know how to dirty");
        }
    }

    /** A record whose every number is off by a little from the fresh one, or null if it will not build. */
    private static @Nullable Object differentRecord(Class<?> type, @Nullable Object fresh) {
        RecordComponent[] parts = type.getRecordComponents();
        Object[] values = new Object[parts.length];
        Class<?>[] types = new Class<?>[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                types[i] = parts[i].getType();
                Object was = fresh == null ? null : parts[i].getAccessor().invoke(fresh);
                values[i] = nudged(types[i], was);
            }
            return type.getDeclaredConstructor(types).newInstance(values);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static @Nullable Object nudged(Class<?> type, @Nullable Object value) {
        if (type == float.class) {
            return (value == null ? 0f : (Float) value) + 0.5f;
        }
        if (type == int.class) {
            return (value == null ? 0 : (Integer) value) + 1;
        }
        if (type == boolean.class) {
            return value == null || !(Boolean) value;
        }
        return value;
    }

    private static <T> void addOneTo(Array<T> array) {
        array.add(null);
    }

    /** A collection compares by size; everything else by value. */
    private static @Nullable Object comparable(@Nullable Object value) {
        return value instanceof Array<?> array ? "Array of " + array.size : value;
    }
}
