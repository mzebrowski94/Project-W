package pl.mzebrows.pwt.map;

import java.util.List;

/**
 * The ground of one kind of biome: its width, its regions, and the coats laid over its base - patches
 * in order, then crags, then outcrops. Rock always wins over a patch, and a pass's apron keeps base
 * ground only. How much of each coat is a density, never a count.
 *
 * <p>Not {@code Ground}: {@code GroundDef} already names what standing on a terrain costs.
 *
 * @param widthMin the narrowest the biome is dealt, in columns
 * @param widthMax the widest
 * @param regions how many regions it is cut into, at least one: every region lies inside one biome (G0062)
 * @param base the ground everything else is laid over
 * @param fallback what neighbour ground is when there is no other biome's base to take
 */
public record BiomeGround(
        int widthMin,
        int widthMax,
        int regions,
        Terrain base,
        Terrain fallback,
        List<Patch> patches,
        Crags crags,
        Outcrops outcrops) {

    public BiomeGround {
        if (widthMin < 1 || widthMax < widthMin) {
            throw new IllegalArgumentException(
                    "biome widths run from at least 1 up, were " + widthMin + ".." + widthMax);
        }
        if (regions < 1) {
            throw new IllegalArgumentException("a biome needs at least one region, was " + regions);
        }
        if (!base.passable() || !fallback.passable()) {
            throw new IllegalArgumentException(
                    "a biome's base and fallback are ground, were " + base + " and " + fallback);
        }
        patches = List.copyOf(patches);
    }
}
