package pl.mzebrows.pwt.map;

import java.util.List;

/**
 * The ground of one kind of biome: its width, its regions, and the coats laid over its base - clearings, houses, then
 * patches in order, then corridors, then crags, then outcrops - after any fixed {@code pieces} of rock. Rock always wins over a
 * patch, and a pass's apron and a house's inside keep base ground only. How much of each coat is a density, never
 * a count - but for houses, which are places rather than texture.
 *
 * <p>Not {@code Ground}: {@code GroundDef} already names what standing on a terrain costs.
 *
 * @param widthMin the narrowest the biome is dealt, in columns
 * @param widthMax the widest
 * @param regions how many regions it is cut into, at least one: every region lies inside one biome (G0062)
 * @param base the ground everything else is laid over
 * @param fallback what neighbour ground is when there is no other biome's base to take
 * @param corridors a maze carved out of the filled biome (G0115, amended 2026-11-23); off in every biome but the rift
 * @param clearings round stretches of its base kept clear of crags and outcrops (G0115); off in every biome but the
 *     rift
 * @param ice how much of its chokepoints are shut by ice, laid last (G0115, ADR 0025); off in every biome but the rift
 * @param pieces fixed rock, rects of tiles offset from the biome's {@link Strip#anchor}, laid before every coat - a
 *     finale's set piece (G0064); empty for every dealt biome, whose rock is its coats
 */
public record BiomeGround(
        int widthMin,
        int widthMax,
        int regions,
        Terrain base,
        Terrain fallback,
        Houses houses,
        List<Patch> patches,
        Crags crags,
        Corridors corridors,
        Outcrops outcrops,
        Clearings clearings,
        Ice ice,
        List<TileRect> pieces) {

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
        pieces = List.copyOf(pieces);
    }
}
