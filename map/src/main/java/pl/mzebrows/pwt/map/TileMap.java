package pl.mzebrows.pwt.map;

/**
 * The ground a level is played on: a rectangular grid of tiles, each carrying a {@link Terrain} and
 * the id of the region it belongs to.
 *
 * <p>Immutable once built. It is world state of the same kind as the simulation's config - a value
 * that systems read freely and nothing writes during a tick - which is why reading it from a system
 * is not a boundary crossing worth worrying about.
 *
 * <p><strong>The grid builds the map and does nothing else</strong> (G0009). Nothing is snapped to
 * it: units move in continuous world units, objects are placed at arbitrary points, and the only
 * question anything asks a tile is what terrain is at a position. The arena rectangle is this map's
 * own extent, so there is one source of truth for how big a level is.
 *
 * <p><strong>This class owns the only conversion between world units and tile coordinates.</strong>
 * Nothing else may open-code {@code (int) (x / tileSize)} - negative coordinates truncate toward zero
 * rather than flooring, and that bug reproduces rarely and gets fixed independently in four places.
 *
 * <p>Anything outside the grid reads as {@link Terrain#ROCK} and therefore as impassable, rather than
 * throwing or reporting open ground. Callers then need no bounds guard of their own, and a mistake at
 * the border fails safe.
 */
public final class TileMap {

    /** What {@link #regionAtTile} reports for a tile the map does not have. */
    public static final int NO_REGION = -1;

    /** What lies outside the map: solid, so nothing can be pushed out through the edge. */
    private static final Terrain OUTSIDE = Terrain.ROCK;

    private final int cols;
    private final int rows;
    private final float tileSize;
    private final Terrain[] terrain;
    private final int[] region;
    private final int regionCount;

    private TileMap(int cols, int rows, float tileSize, Terrain[] terrain, int[] region, int regionCount) {
        this.cols = cols;
        this.rows = rows;
        this.tileSize = tileSize;
        this.terrain = terrain;
        this.region = region;
        this.regionCount = regionCount;
    }

    /** Starts a map of this many tiles, every one of them {@link Terrain#PLAIN} in region zero. */
    public static Builder builder(int cols, int rows, float tileSize) {
        return new Builder(cols, rows, tileSize);
    }

    public int cols() {
        return cols;
    }

    public int rows() {
        return rows;
    }

    /** The width of one tile, in world units. */
    public float tileSize() {
        return tileSize;
    }

    /** How many regions this map was partitioned into. Ids run from zero to one less than this. */
    public int regionCount() {
        return regionCount;
    }

    /** The playable width, in world units. This is the arena. */
    public float worldWidth() {
        return cols * tileSize;
    }

    /** The playable height, in world units. This is the arena. */
    public float worldHeight() {
        return rows * tileSize;
    }

    /** The column holding this world x, which may be outside the map. */
    public int colAt(float worldX) {
        return (int) Math.floor(worldX / tileSize);
    }

    /** The row holding this world y, which may be outside the map. */
    public int rowAt(float worldY) {
        return (int) Math.floor(worldY / tileSize);
    }

    /** The world x of the middle of a column. */
    public float tileCentreX(int col) {
        return (col + 0.5f) * tileSize;
    }

    /** The world y of the middle of a row. */
    public float tileCentreY(int row) {
        return (row + 0.5f) * tileSize;
    }

    /** Whether the map actually has this tile. */
    public boolean inBounds(int col, int row) {
        return col >= 0 && col < cols && row >= 0 && row < rows;
    }

    /** The terrain of a tile; {@link Terrain#ROCK} for anything outside the map. */
    public Terrain terrainAtTile(int col, int row) {
        return inBounds(col, row) ? terrain[row * cols + col] : OUTSIDE;
    }

    /** The terrain at a world position; {@link Terrain#ROCK} for anything outside the map. */
    public Terrain terrainAt(float worldX, float worldY) {
        return terrainAtTile(colAt(worldX), rowAt(worldY));
    }

    /** The region a tile belongs to, or {@link #NO_REGION} for anything outside the map. */
    public int regionAtTile(int col, int row) {
        return inBounds(col, row) ? region[row * cols + col] : NO_REGION;
    }

    /** Whether a unit may stand on this tile. False for anything outside the map. */
    public boolean passableAtTile(int col, int row) {
        return terrainAtTile(col, row).passable();
    }

    /** Whether a unit may stand at this world position. False for anything outside the map. */
    public boolean passableAt(float worldX, float worldY) {
        return passableAtTile(colAt(worldX), rowAt(worldY));
    }

    /**
     * Whether a circle of this radius sits entirely on ground that can be stood on.
     *
     * <p><strong>Conservative.</strong> It tests every tile the circle's bounding box touches rather
     * than the circle itself, so a body that only just clips the corner of a rock is reported as not
     * clear. That is the right way round for the thing this is for - deciding where to <em>put</em>
     * something - because the cost of being wrong is a spot rejected rather than an object embedded
     * in a wall. It is deliberately not what the constraint solver uses, which needs the exact answer.
     */
    public boolean isClear(float worldX, float worldY, float radius) {
        int minCol = colAt(worldX - radius);
        int maxCol = colAt(worldX + radius);
        int minRow = rowAt(worldY - radius);
        int maxRow = rowAt(worldY + radius);
        for (int row = minRow; row <= maxRow; row++) {
            for (int col = minCol; col <= maxCol; col++) {
                if (!passableAtTile(col, row)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Assembles a map tile by tile. Generators and hand-authored levels both come through here, so a
     * fixed level and a generated one are the same type downstream and nothing can tell them apart.
     */
    public static final class Builder {

        private final int cols;
        private final int rows;
        private final float tileSize;
        private final Terrain[] terrain;
        private final int[] region;

        private Builder(int cols, int rows, float tileSize) {
            if (cols < 1 || rows < 1) {
                throw new IllegalArgumentException("a map needs at least one tile, was " + cols + "x" + rows);
            }
            if (tileSize <= 0f) {
                throw new IllegalArgumentException("tile size must be positive, was " + tileSize);
            }
            this.cols = cols;
            this.rows = rows;
            this.tileSize = tileSize;
            this.terrain = new Terrain[cols * rows];
            this.region = new int[cols * rows];
            java.util.Arrays.fill(this.terrain, Terrain.PLAIN);
        }

        /** Sets every tile to one terrain, discarding whatever was there. */
        public Builder fill(Terrain kind) {
            java.util.Arrays.fill(terrain, kind);
            return this;
        }

        public int cols() {
            return cols;
        }

        public int rows() {
            return rows;
        }

        /** The region a tile has been put in so far, for a step that lays terrain over the partition. */
        public int regionAt(int col, int row) {
            return region[index(col, row)];
        }

        /** Sets one tile's terrain. */
        public Builder set(int col, int row, Terrain kind) {
            terrain[index(col, row)] = kind;
            return this;
        }

        /**
         * Sets a rectangle of tiles, clipped to the map rather than refused - a layout written
         * against one map size is then still buildable on another, which is what stops a
         * hand-authored scenario throwing the first time somebody changes the grid.
         */
        public Builder rect(int col, int row, int width, int height, Terrain kind) {
            int lastCol = Math.min(cols - 1, col + width - 1);
            int lastRow = Math.min(rows - 1, row + height - 1);
            for (int r = Math.max(0, row); r <= lastRow; r++) {
                for (int c = Math.max(0, col); c <= lastCol; c++) {
                    terrain[r * cols + c] = kind;
                }
            }
            return this;
        }

        /** Lines the outside of the map with a wall this many tiles thick. */
        public Builder border(int thickness, Terrain kind) {
            for (int layer = 0; layer < thickness; layer++) {
                rect(layer, layer, cols - layer * 2, 1, kind);
                rect(layer, rows - 1 - layer, cols - layer * 2, 1, kind);
                rect(layer, layer, 1, rows - layer * 2, kind);
                rect(cols - 1 - layer, layer, 1, rows - layer * 2, kind);
            }
            return this;
        }

        /** Puts one tile in a region. Ids are dense from zero; the count is taken from the highest. */
        public Builder region(int col, int row, int id) {
            if (id < 0) {
                throw new IllegalArgumentException("region id must not be negative, was " + id);
            }
            region[index(col, row)] = id;
            return this;
        }

        public TileMap build() {
            int highest = 0;
            for (int id : region) {
                if (id > highest) {
                    highest = id;
                }
            }
            return new TileMap(cols, rows, tileSize, terrain.clone(), region.clone(), highest + 1);
        }

        private int index(int col, int row) {
            if (col < 0 || col >= cols || row < 0 || row >= rows) {
                throw new IllegalArgumentException(
                        "no tile at " + col + "," + row + " in a " + cols + "x" + rows + " map");
            }
            return row * cols + col;
        }
    }
}
