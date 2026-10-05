package pl.mzebrows.pwt.map;

/**
 * A biome's houses: how many {@link House}s it is laid with - the first coat, laid before patches because it is
 * the big shape. A count rather than a density, the one coat that is: a house is a place, not texture, and a
 * density of pieces this big lands a whole house off (missions ticket 12). How big a house is and how it is
 * entered is the strip's ({@link HouseRules}).
 *
 * @param min the fewest houses laid; a biome that cannot fit them is a fault, never silently short
 * @param max the most; zero is no houses
 */
public record Houses(int min, int max) {

    public Houses {
        if (min < 0 || max < min) {
            throw new IllegalArgumentException("a biome's houses run from at least 0 up, were " + min + ".." + max);
        }
    }

    /** No houses: what a biome whose block leaves them out has. */
    public static Houses off() {
        return new Houses(0, 0);
    }
}
