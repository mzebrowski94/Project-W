package pl.mzebrows.pwt.map;

/**
 * A biome's clearings (G0115, provisional, for review): round stretches of its base no crag or outcrop is laid in, each
 * an end of the biome the coats after must keep reached - laid after the fixed pieces and before every other coat. Clearing
 * {@code k} of {@code count} is centred near {@code k / (count + 1)} of the biome's width, dealt within a twelfth of it
 * on the side away from the middle, and on a row a radius in from either edge ({@link Coats}).
 *
 * @param count how many; zero is none
 * @param radius how far a clearing reaches from its centre tile, in tiles, judged at each tile's centre
 */
public record Clearings(int count, int radius) {

    public Clearings {
        if (count < 0 || radius < 0 || (count > 0 && radius < 1)) {
            throw new IllegalArgumentException(
                    "a biome's clearings are a count and a radius of at least a tile, were " + count + " of " + radius);
        }
    }

    /** No clearings: what a biome whose block leaves them out has. */
    public static Clearings off() {
        return new Clearings(0, 0);
    }
}
