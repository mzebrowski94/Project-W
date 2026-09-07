package pl.mzebrows.pwt.sim;

/**
 * The only source of randomness the simulation may use.
 *
 * <p>SplitMix64: fixed algorithm, fixed output for a given seed on every JVM and platform, no
 * allocation. Its whole state is one long, so a running simulation can be snapshotted and replayed
 * exactly - which is what makes deterministic replay and future lockstep networking possible.
 */
public final class Rng {

    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private long state;

    /** Creates a generator seeded with the given value. */
    public Rng(long seed) {
        this.state = seed;
    }

    /** Returns the next 64 random bits. */
    public long nextLong() {
        long z = (state += GOLDEN_GAMMA);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Returns a value in {@code [0, boundExclusive)}; uses rejection sampling, so it is unbiased. */
    public int nextInt(int boundExclusive) {
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

    /** Returns a value in {@code [0.0, 1.0)}. */
    public float nextFloat() {
        return (nextLong() >>> 40) * 0x1.0p-24f;
    }

    /** Returns a value in {@code [min, max)}. */
    public float nextFloat(float min, float max) {
        return min + nextFloat() * (max - min);
    }

    /** Returns the full internal state, for snapshotting. */
    public long state() {
        return state;
    }

    /** Restores state captured by {@link #state()}. */
    public void restore(long snapshot) {
        this.state = snapshot;
    }
}
