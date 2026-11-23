package pl.mzebrows.pwt.map;

/**
 * A biome's corridors (G0115, amended 2026-11-23): the whole biome filled with solid ground and long, narrow, mostly
 * level ways carved back out of it - a braided maze. The biome's rows are cut into <em>corridors</em>, each
 * {@link #widthMin()}-{@link #widthMax()} rows wide, its edges wandering {@link #wander()} rows, and {@link #wall()}
 * rows of solid apart at the least; each corridor into
 * cells {@link #cell()} columns long. A share of the corridors' joins between cells is shut ({@link #breaks()}), a
 * randomised spanning tree joins every cell through short links down the walls (Kruskal's, the shut joins last), and
 * {@link #loops()} of the links left over are opened besides, so ways branch and rejoin. A share of the links is
 * opened across its whole cell ({@link #chambers()}): a room two corridors deep. The
 * clearings, the aprons and the biome's ends are carved round as every coat is, and the crag repairs net the rest.
 *
 * @param widthMin the narrowest a corridor or a link is carved, in tiles; never under the clearance
 * @param widthMax the widest
 * @param wander how many rows a corridor's edges wander along it, column by column, so its walls read as rock; zero is
 *     straight
 * @param wall the least solid between two corridors, in rows, and how long a shut join is, in columns
 * @param cell how many columns a corridor runs between two places a link may leave it; zero is no corridors
 * @param breaks the fraction of the joins along a corridor shut before the cells are joined, 0 to 1: more, shorter ways
 * @param loops the fraction of the links the spanning tree left over opened besides, 0 to 1: more, more ways round
 * @param chambers the fraction of the open links opened across their whole cell, 0 to 1
 * @param terrain what the solid is: {@link Terrain#FISSURE} for the rift - never ground something stands on
 */
public record Corridors(
        int widthMin,
        int widthMax,
        int wander,
        int wall,
        int cell,
        float breaks,
        float loops,
        float chambers,
        Terrain terrain) {

    public Corridors {
        if (cell < 0) {
            throw new IllegalArgumentException("a corridors' cell is a count of columns, was " + cell);
        }
        if (cell > 0) {
            if (widthMin < 1 || widthMax < widthMin) {
                throw new IllegalArgumentException(
                        "corridors are at least a tile wide, were " + widthMin + ".." + widthMax);
            }
            if (wander < 0) {
                throw new IllegalArgumentException("a corridors' wander is a count of rows, was " + wander);
            }
            if (wall < 1) {
                throw new IllegalArgumentException("a corridors' wall is at least a row, was " + wall);
            }
            if (cell < widthMax + wall) {
                throw new IllegalArgumentException("a corridors' cell must hold a link and a shut join, was " + cell
                        + " for " + widthMax + " and " + wall);
            }
            if (terrain.passable()) {
                throw new IllegalArgumentException("corridors are carved in solid ground, not " + terrain);
            }
        }
        for (float fraction : new float[] {breaks, loops, chambers}) {
            if (fraction < 0f || fraction > 1f) {
                throw new IllegalArgumentException("a corridors' share is a fraction, was " + fraction);
            }
        }
    }

    /** No corridors: what a biome whose block leaves them out has. */
    public static Corridors off() {
        return new Corridors(0, 0, 0, 0, 0, 0f, 0f, 0f, Terrain.ROCK);
    }

    /** Whether the biome is carved into corridors at all. */
    public boolean on() {
        return cell > 0;
    }
}
