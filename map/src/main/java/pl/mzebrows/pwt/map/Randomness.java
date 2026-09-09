package pl.mzebrows.pwt.map;

/**
 * A stream of deterministic random bits, supplied by whoever is building a map.
 *
 * <p>This module holds no generator of its own on purpose. Reproducibility is pillar 6, and two
 * implementations of "random" are two behaviours to keep in step - so the caller passes its one
 * seeded generator in and this module only says what it needs from it. {@code sim}'s {@code Rng}
 * implements this and remains the single source of randomness in the game.
 *
 * <p>Only {@link #nextLong()} is left to the implementation; everything derived from it is here, so
 * a number drawn during map generation is drawn exactly the way the simulation would draw it.
 *
 * <p><strong>Give map generation its own stream.</strong> Deriving one seed per concern - terrain,
 * scatter, gameplay - is what stops re-tuning object placement from reshuffling the terrain layout
 * you had just settled on. See the {@code game-patterns} skill.
 */
@FunctionalInterface
public interface Randomness {

    /** The next 64 random bits. */
    long nextLong();

    /** A value in {@code [0, boundExclusive)}. Rejection sampled, so it is unbiased. */
    default int nextInt(int boundExclusive) {
        if (boundExclusive <= 0) {
            throw new IllegalArgumentException("bound must be positive, was " + boundExclusive);
        }
        int bits;
        int value;
        do {
            bits = (int) (nextLong() >>> 33);
            value = bits % boundExclusive;
        } while (bits - value + (boundExclusive - 1) < 0);
        return value;
    }

    /** A value in {@code [0.0, 1.0)}. */
    default float nextFloat() {
        return (nextLong() >>> 40) * 0x1.0p-24f;
    }

    /** A value in {@code [min, max)}. */
    default float nextFloat(float min, float max) {
        return min + nextFloat() * (max - min);
    }
}
