package pl.mzebrows.pwt.map;

/**
 * What every {@link House} on a strip shares, whichever biome it is laid in, and the gatehouse - a house built
 * into a ridge (missions ticket 12). Sizes are outside the walls for a house inside a biome and inside them for a
 * gatehouse, as the two are tuned: a house by how much ground it takes, a gatehouse by the room in it.
 *
 * @param widthMin the narrowest house, walls included, in columns
 * @param widthMax the widest
 * @param heightMin the lowest house, walls included, in rows
 * @param heightMax the highest
 * @param doorways how many doorways a house inside a biome has, each in a wall of its own; a gatehouse always has
 *     two, the way in and the way out
 * @param doorwayWidth how wide every doorway is, in tiles - a gatehouse's too, so one door fits every doorway
 * @param gatehouseShare the chance a ridge between two biomes is a gatehouse; never the ridge before the finale
 * @param gatehouseWidth a gatehouse inside its walls, in columns; its ridge is this, the two walls and a column of
 *     rock either side
 * @param gatehouseHeight a gatehouse inside its walls, in rows
 */
public record HouseRules(
        int widthMin,
        int widthMax,
        int heightMin,
        int heightMax,
        int doorways,
        int doorwayWidth,
        float gatehouseShare,
        int gatehouseWidth,
        int gatehouseHeight) {

    public HouseRules {
        if (doorwayWidth < 1 || doorways < 1 || doorways > 4) {
            throw new IllegalArgumentException(
                    "a house has 1 to 4 doorways at least a tile wide, was " + doorways + " of " + doorwayWidth);
        }
        if (widthMax < widthMin || heightMax < heightMin || Math.min(widthMin, heightMin) - 2 < doorwayWidth) {
            throw new IllegalArgumentException("a house runs " + widthMin + ".." + widthMax + " x " + heightMin + ".."
                    + heightMax + " and must hold a doorway " + doorwayWidth + " wide off its corners in every wall");
        }
        if (gatehouseShare < 0f || gatehouseShare > 1f) {
            throw new IllegalArgumentException("the gatehouse share is a chance, was " + gatehouseShare);
        }
        if (gatehouseWidth < 1 || gatehouseHeight <= doorwayWidth) {
            throw new IllegalArgumentException("a gatehouse " + gatehouseWidth + " x " + gatehouseHeight
                    + " cannot be entered and left on two rows by doorways " + doorwayWidth + " wide");
        }
    }

    /** How wide a ridge with a gatehouse in it is: the house, its walls and a column of rock either side. */
    public int gatehouseRidge() {
        return gatehouseWidth + 4;
    }

    /** How many rows a gatehouse's way out may be from its way in: off by one at least, inside its walls. */
    int gatehouseJog(int passJog) {
        return Math.min(passJog, gatehouseHeight - doorwayWidth);
    }
}
