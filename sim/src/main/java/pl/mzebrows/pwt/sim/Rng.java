package pl.mzebrows.pwt.sim;

import pl.mzebrows.pwt.map.Randomness;

/**
 * The only source of randomness the simulation may use.
 *
 * <p>It implements {@link Randomness} so map generation draws numbers exactly the way the simulation
 * does. Everything derived from the raw bits - bounded ints, floats - is defined once, there.
 *
 * <p>SplitMix64: fixed algorithm, fixed output for a given seed on every JVM and platform, no
 * allocation. Its whole state is one long, so a running simulation can be snapshotted and replayed
 * exactly - which is what makes deterministic replay and future lockstep networking possible.
 */
public final class Rng implements Randomness {

    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private long state;

    /** Creates a generator seeded with the given value. */
    public Rng(long seed) {
        this.state = seed;
    }

    /** Returns the next 64 random bits. */
    @Override
    public long nextLong() {
        long z = (state += GOLDEN_GAMMA);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
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
