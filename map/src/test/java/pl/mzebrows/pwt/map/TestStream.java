package pl.mzebrows.pwt.map;

/**
 * A {@link Randomness} for this module's tests, which cannot see {@code sim}'s {@code Rng}.
 *
 * <p>Two flavours. {@link #weyl()} is the bare Weyl sequence the older tests were written against:
 * deterministic, well spread in its high bits, and deliberately not the simulation's generator - those
 * tests are about a partition or a scatter, not about SplitMix64. {@link #seeded(long)} runs each step
 * through SplitMix64's finalizer, for tests that measure a distribution over many seeds and need draws
 * that do not move in step with each other.
 */
final class TestStream implements Randomness {

    private static final long GAMMA = 0x9E3779B97F4A7C15L;

    private long state;
    private final boolean mixed;

    private TestStream(long state, boolean mixed) {
        this.state = state;
        this.mixed = mixed;
    }

    /** The bare Weyl sequence from zero. */
    static TestStream weyl() {
        return new TestStream(0L, false);
    }

    /** SplitMix64 from a seed. */
    static TestStream seeded(long seed) {
        return new TestStream(seed, true);
    }

    @Override
    public long nextLong() {
        state += GAMMA;
        return mixed ? mix(state) : state;
    }

    @Override
    public Randomness derive(long salt) {
        return new TestStream(mix(state ^ (salt * GAMMA)), mixed);
    }

    private static long mix(long value) {
        long z = value;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
