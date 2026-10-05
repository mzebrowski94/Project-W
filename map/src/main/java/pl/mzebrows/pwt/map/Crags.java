package pl.mzebrows.pwt.map;

/**
 * A biome's crags: rock laid as irregular masses - white noise smoothed {@link #smoothing()} times, cut at
 * the quantile that makes it {@link #density()} of the biome, then repaired so every pass stays reachable, no
 * passage is under the clearance, and nothing is sealed off.
 *
 * @param density the fraction of the biome laid as crag before repair; zero is no crags
 * @param smoothing how many times the noise is averaged over its neighbours - more, fewer and bigger masses
 */
public record Crags(float density, int smoothing) {

    public Crags {
        if (density < 0f || density > 1f) {
            throw new IllegalArgumentException("a crag density is a fraction of the biome, was " + density);
        }
        if (smoothing < 0) {
            throw new IllegalArgumentException("smoothing must not be negative, was " + smoothing);
        }
    }

    /** No crags: what a biome whose block leaves them out has. */
    public static Crags off() {
        return new Crags(0f, 0);
    }
}
