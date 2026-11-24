package pl.mzebrows.pwt.map;

/**
 * A biome's clearings (G0115, provisional, for review): round stretches of its base no crag or outcrop is laid in, each
 * an end of the biome the coats after must keep reached - laid after the fixed pieces and before every other coat. Clearing
 * {@code k} of {@code count} is centred near {@code k / (count + 1)} of the biome's width, dealt within a twelfth of it
 * on the side away from the middle, and on a row a radius in from either edge ({@link Coats}).
 *
 * <p>Its <em>nooks</em> are smaller clearings that hold no site: room for a nest in a maze that otherwise leaves none
 * (G0115, amended 2026-11-24). Nook {@code k} of {@code nooks} is centred in the {@code k}-th of as many equal bands of
 * the biome's columns less a twelfth at either end, in its top third or its bottom third, whichever is farther from the nearest clearing.
 *
 * @param count how many; zero is none
 * @param radius how far a clearing reaches from its centre tile, in tiles, judged at each tile's centre
 * @param nooks how many nooks; zero is none
 * @param nookRadius how far a nook reaches from its centre tile, in tiles
 */
public record Clearings(int count, int radius, int nooks, int nookRadius) {

    public Clearings {
        if (count < 0 || radius < 0 || (count > 0 && radius < 1)) {
            throw new IllegalArgumentException(
                    "a biome's clearings are a count and a radius of at least a tile, were " + count + " of " + radius);
        }
        if (nooks < 0 || nookRadius < 0 || (nooks > 0 && nookRadius < 1)) {
            throw new IllegalArgumentException(
                    "a biome's nooks are a count and a radius of at least a tile, were " + nooks + " of " + nookRadius);
        }
    }

    /** No clearings: what a biome whose block leaves them out has. */
    public static Clearings off() {
        return new Clearings(0, 0, 0, 0);
    }
}
