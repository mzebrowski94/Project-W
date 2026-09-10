package pl.mzebrows.pwt.map;

/**
 * A rectangle of tiles, named in tile coordinates: a patch of the grid that means something to
 * whoever laid the level out.
 *
 * <p>What a level uses one for today is a <strong>spawn area</strong> - the ground a side's units are
 * put on at the start. It is a rectangle of tiles rather than a single tile because a squad does not
 * fit on one, and it is tiles rather than world units because the grid is the unit a level is
 * authored and generated in (G0009).
 *
 * <p><strong>Nothing here quantises anything.</strong> The rectangle chooses which tiles may be used;
 * where inside them a thing actually stands is a jittered offset, exactly as {@link Scatter} already
 * places everything else. A squad lined up on tile centres would read as a grid to the player
 * instantly, which is the tell that separates an authored level from a generated one.
 *
 * <p>Lives in {@code map} because it is pure geometry over the grid and knows nothing about what will
 * be put in it (ADR 0009). Who spawns where is {@code sim}'s business.
 *
 * @param col leftmost tile column, inclusive
 * @param row bottom tile row, inclusive
 * @param width how many columns across, at least one
 * @param height how many rows up, at least one
 */
public record TileRect(int col, int row, int width, int height) {

    public TileRect {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("tile rect must be at least one tile, was " + width + "x" + height);
        }
    }

    /** One tile, for a spawn area that genuinely is a single tile. */
    public static TileRect ofTile(int col, int row) {
        return new TileRect(col, row, 1, 1);
    }

    /** The column just past the right-hand edge. */
    public int endCol() {
        return col + width;
    }

    /** The row just past the top edge. */
    public int endRow() {
        return row + height;
    }

    /** How many tiles it covers. */
    public int tileCount() {
        return width * height;
    }

    /** Whether this tile is inside the rectangle. */
    public boolean contains(int atCol, int atRow) {
        return atCol >= col && atCol < endCol() && atRow >= row && atRow < endRow();
    }

    /** The world point at the middle of the rectangle - for a camera or a route, never for a spawn. */
    public float centreX(TileMap map) {
        return (col + width * 0.5f) * map.tileSize();
    }

    /** The world point at the middle of the rectangle - for a camera or a route, never for a spawn. */
    public float centreY(TileMap map) {
        return (row + height * 0.5f) * map.tileSize();
    }
}
