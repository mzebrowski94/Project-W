package pl.mzebrows.pwt.sim.def;

import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;

/**
 * An immutable table of definitions, one per constant of an enum: strains by {@code UnitKind},
 * pickups by {@code PickupKind}, guns by {@code WeaponKind}.
 *
 * <p>Backed by an array indexed by the enum's ordinal rather than a map. Lookup is one array read,
 * there is no hashing, and therefore no iteration order that could vary between runs - which is what
 * a {@code HashMap} here would have cost, and why {@code ArchitectureTest} bans one outright.
 *
 * <p><strong>Completeness is checked at construction.</strong> A table missing a constant cannot be
 * built, so no system anywhere has to consider what to do when a lookup returns nothing. That check
 * is the whole reason this is a class rather than a bare array passed around, and it is what turns
 * "somebody must remember to define the new kind" into a build failure.
 *
 * <p><strong>Ordinal indexing is deliberate, and suppressed rather than avoided.</strong> Error Prone
 * is right in general - depending on an enum's declaration order is usually a latent bug, because a
 * constant inserted in the middle silently renumbers everything. It is safe here for a reason that
 * does not generalise: nothing is <em>persisted</em> against these numbers. The array is built from
 * the enum's own constants in the same process that reads it, so reordering the enum reorders the
 * array with it.
 *
 * <p>Equality is by value, because {@code SimConfig} is a record and a test compares a loaded config
 * with the defaults it fell back to. An array field with the default identity equality would make
 * that comparison quietly always false.
 *
 * @param <K> the enum naming the kinds
 * @param <V> the definition record describing one kind
 */
@SuppressWarnings("EnumOrdinal")
public final class Defs<K extends Enum<K>, V> {

    private final Class<K> keyType;
    private final V[] byKey;

    private Defs(Class<K> keyType, V[] byKey) {
        this.keyType = keyType;
        this.byKey = byKey;
    }

    public static <K extends Enum<K>, V> Builder<K, V> builder(Class<K> keyType) {
        return new Builder<>(keyType);
    }

    /** The definition of one kind. Never null - the table could not have been built without it. */
    public V of(K key) {
        return byKey[key.ordinal()];
    }

    /** Every definition, in the enum's declaration order. Allocates; for setup and tooling only. */
    public List<V> values() {
        return List.of(byKey);
    }

    /** This table with one kind replaced, leaving the rest alone. */
    public Defs<K, V> with(K key, V def) {
        V[] copy = byKey.clone();
        copy[key.ordinal()] = def;
        return new Defs<>(keyType, copy);
    }

    /**
     * This table with one kind changed in place, so a scenario or a test can vary a single number
     * without restating the kind. Allocates, and is meant to: it runs at setup, never in a tick.
     */
    public Defs<K, V> map(K key, UnaryOperator<V> change) {
        return with(key, change.apply(of(key)));
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof Defs<?, ?> defs && keyType.equals(defs.keyType) && Arrays.equals(byKey, defs.byKey);
    }

    @Override
    public int hashCode() {
        return keyType.hashCode() * 31 + Arrays.hashCode(byKey);
    }

    @Override
    public String toString() {
        return keyType.getSimpleName() + Arrays.toString(byKey);
    }

    /** Collects one definition per kind and refuses to produce a table with a hole in it. */
    public static final class Builder<K extends Enum<K>, V> {

        private final Class<K> keyType;
        private final @Nullable Object[] byKey;

        private Builder(Class<K> keyType) {
            this.keyType = keyType;
            this.byKey = new Object[keyType.getEnumConstants().length];
        }

        public Builder<K, V> put(K key, V def) {
            byKey[key.ordinal()] = def;
            return this;
        }

        /** Fails loudly if any kind was left out, rather than handing back a table with a gap. */
        @SuppressWarnings("unchecked")
        public Defs<K, V> build() {
            for (K key : keyType.getEnumConstants()) {
                if (byKey[key.ordinal()] == null) {
                    throw new IllegalStateException("no definition for " + keyType.getSimpleName() + " " + key);
                }
            }
            return new Defs<>(keyType, (V[]) byKey.clone());
        }
    }
}
