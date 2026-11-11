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
 *
 * <p>{@link #stream(long, RngStream)} derives one generator per concern from a single level seed.
 * See {@link RngStream} for why sharing one sequence between concerns is a trap.
 */
public final class Rng implements Randomness {

    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private long state;

    /** Creates a generator seeded with the given value. */
    public Rng(long seed) {
        this.state = seed;
    }

    /**
     * A generator for one concern, derived from the level seed.
     *
     * <p>The salt is multiplied by the gamma and the result is run through SplitMix64's mixing
     * function before it becomes a seed, rather than merely exclusive-ored in. Two seeds that differ
     * by exactly the gamma produce sequences one draw apart from each other - the same numbers, one
     * step out of step - so seeding streams from lightly perturbed values is precisely the way to get
     * two "independent" streams that are anything but. Mixing removes that whole class of accident.
     */
    public static Rng stream(long seed, RngStream concern) {
        return new Rng(seed).derive(concern.salt());
    }

    /**
     * A child from this generator's current state and a salt, with the mixing {@link #stream} uses, and
     * without advancing this one (G0065). {@link #stream} is this applied to the level seed.
     */
    @Override
    public Rng derive(long salt) {
        return new Rng(mix(state ^ (salt * GOLDEN_GAMMA)));
    }

    /** Returns the next 64 random bits. */
    @Override
    public long nextLong() {
        return mix(state += GOLDEN_GAMMA);
    }

    /** Returns the full internal state, for snapshotting. */
    public long state() {
        return state;
    }

    /** Restores state captured by {@link #state()}. */
    public void restore(long snapshot) {
        this.state = snapshot;
    }

    /**
     * A number in {@code [0, 1)} that is a function of its three keys alone - the same answer every time it
     * is asked, on every machine, without advancing any stream (G0076). For a chance taken once per pair
     * of things rather than per second of opportunity: whether this man gives that spatter room. It
     * allocates nothing, so a per-entity loop may ask it.
     */
    public static float keyed(long seed, long first, long second) {
        long mixed = mix(mix(seed ^ (first * GOLDEN_GAMMA)) ^ (second * GOLDEN_GAMMA));
        // The top 24 bits, as Randomness.nextFloat takes them, so the answer is exact in a float.
        return (mixed >>> 40) * 0x1.0p-24f;
    }

    /** SplitMix64's finalizer: avalanches a counter into a value with no visible structure. */
    private static long mix(long value) {
        long z = value;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
