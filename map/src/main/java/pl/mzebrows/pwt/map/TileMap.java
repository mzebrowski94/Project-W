package pl.mzebrows.pwt.map;

/**
 * The ground a level is played on: a rectangular grid of tiles, each carrying a {@link Terrain} and
 * the id of the region it belongs to.
 *
 * <p><strong>A biome is a grouping of regions</strong> (G0062): every region lies inside one, and a tile
 * is in its region's biome - one partition, so the shading and the biome cannot disagree and a region
 * cannot straddle two. A map that names none is one biome, biome zero.
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

    /**
     * What {@link #biomeAtTile} reports for a tile the map does not have - and only then: every tile on
     * the map is in a biome (G0062).
     */
    public static final int NO_BIOME = -1;

    /** What lies outside the map: solid, so nothing can be pushed out through the edge. */
    private static final Terrain OUTSIDE = Terrain.ROCK;

    private final int cols;
    private final int rows;
    private final float tileSize;
    private final Terrain[] terrain;
    private final int[] region;
    private final int regionCount;

    /** The biome each region is in, indexed by region id: {@link #regionCount} long. */
    private final int[] biomeOfRegion;

    private final int biomeCount;

    private TileMap(
            int cols,
            int rows,
            float tileSize,
            Terrain[] terrain,
            int[] region,
            int regionCount,
            int[] biomeOfRegion,
            int biomeCount) {
        this.cols = cols;
        this.rows = rows;
        this.tileSize = tileSize;
        this.terrain = terrain;
        this.region = region;
        this.regionCount = regionCount;
        this.biomeOfRegion = biomeOfRegion;
        this.biomeCount = biomeCount;
    }

    /** Starts a map of this many tiles, every one of them {@link Terrain#PLAIN} in region zero, biome zero. */
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

    /** How many biomes this map's regions were grouped into: one if it named none. Ids run from zero. */
    public int biomeCount() {
        return biomeCount;
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

    /** The biome a tile is in - its region's - or {@link #NO_BIOME} for anything outside the map. */
    public int biomeAtTile(int col, int row) {
        return inBounds(col, row) ? biomeOfRegion[region[row * cols + col]] : NO_BIOME;
    }

    /** The biome at a world position, or {@link #NO_BIOME} for anything outside the map. */
    public int biomeAt(float worldX, float worldY) {
        return biomeAtTile(colAt(worldX), rowAt(worldY));
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
     * Whether a circle of this radius could travel between two world points without touching rock.
     *
     * <p>Marched in half-tile steps rather than solved analytically: a tile grid has no closed form
     * for "does this capsule hit any solid tile", and a step of half a tile cannot skip past one. It
     * inherits {@link #isClear}'s conservatism, so it errs towards reporting a route blocked, which is
     * the right way round for a caller deciding whether it may take a shortcut.
     */
    public boolean isClearPath(float fromX, float fromY, float toX, float toY, float radius) {
        float spanX = toX - fromX;
        float spanY = toY - fromY;
        float span = (float) Math.sqrt(spanX * spanX + spanY * spanY);
        int steps = (int) Math.ceil(span / (tileSize * 0.5f));
        for (int step = 0; step <= steps; step++) {
            float along = steps == 0 ? 0f : (float) step / steps;
            if (!isClear(fromX + spanX * along, fromY + spanY * along, radius)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether an unobstructed straight line runs between two world points - a sight line, with no
     * body to it.
     *
     * <p>Walks the grid tile by tile (Amanatides and Woo) rather than sampling along the ray, so it
     * is <em>exact</em>: every tile the line passes through is tested, and none can be stepped over.
     * {@link #isClearPath} deliberately does something different - it marches in half-tile steps and
     * carries a radius, because it answers "could a body of this size travel here", where erring
     * towards blocked is right. A sight line has no width and must not err at all, or a unit
     * occasionally notices through the corner of a wall and nothing reproduces it.
     *
     * <p>Anything outside the map reads as impassable, so a line that leaves the grid is blocked
     * rather than clear - the same rule every other lookup here follows, and it fails safe.
     *
     * <p>Only terrain blocks a line. Obstacles, corpses and other units are entities and this module
     * cannot see one (ADR 0009); that asymmetry is deliberate and is the same one routing already
     * accepts.
     */
    public boolean clearLine(float fromX, float fromY, float toX, float toY) {
        return passableFraction(fromX, fromY, toX, toY) >= 1f;
    }

    /**
     * How much of the straight line between two world points runs over ground before it first meets
     * something impassable, as a fraction of the line: one if none of it does, zero if it starts there.
     *
     * <p>The same exact tile walk as {@link #clearLine}, which is this asked whether all of it is clear -
     * so where a shot that flew into rock meets the face is answered by the rule a sight line uses.
     */
    public float passableFraction(float fromX, float fromY, float toX, float toY) {
        int col = colAt(fromX);
        int row = rowAt(fromY);
        if (!passableAtTile(col, row)) {
            return 0f;
        }
        int endCol = colAt(toX);
        int endRow = rowAt(toY);
        if (col == endCol && row == endRow) {
            return 1f;
        }

        float spanX = toX - fromX;
        float spanY = toY - fromY;
        int stepX = spanX > 0f ? 1 : (spanX < 0f ? -1 : 0);
        int stepY = spanY > 0f ? 1 : (spanY < 0f ? -1 : 0);
        float nextX = boundary(col, stepX);
        float nextY = boundary(row, stepY);
        float toBoundaryX = stepX == 0 ? Float.POSITIVE_INFINITY : (nextX - fromX) / spanX;
        float toBoundaryY = stepY == 0 ? Float.POSITIVE_INFINITY : (nextY - fromY) / spanY;
        float perTileX = stepX == 0 ? Float.POSITIVE_INFINITY : tileSize / Math.abs(spanX);
        float perTileY = stepY == 0 ? Float.POSITIVE_INFINITY : tileSize / Math.abs(spanY);

        // Every tile crossed is one step in exactly one axis, so the walk cannot be longer than the
        // grid's own span. The bound is belt and braces against a denormal ray stalling the loop.
        int limit = cols + rows + 2;
        for (int taken = 0; taken < limit; taken++) {
            float crossed;
            if (toBoundaryX < toBoundaryY) {
                crossed = toBoundaryX;
                col += stepX;
                toBoundaryX += perTileX;
            } else {
                crossed = toBoundaryY;
                row += stepY;
                toBoundaryY += perTileY;
            }
            if (!passableAtTile(col, row)) {
                // Strictly short of one, so a line blocked on its last tile is never read as clear.
                return Math.max(0f, Math.min(Math.nextDown(1f), crossed));
            }
            if (col == endCol && row == endRow) {
                return 1f;
            }
        }
        return 1f;
    }

    /** The world coordinate of the edge of this tile the ray is heading towards. */
    private float boundary(int index, int step) {
        return step > 0 ? (index + 1) * tileSize : index * tileSize;
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

        /** The biome each region has been put in so far, by region id; grown as regions are named. */
        private int[] biomeOfRegion = new int[0];

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

        /**
         * Puts a whole region in a biome. Every region is in biome zero until told otherwise; biome ids
         * are dense from zero, and the count is taken from the highest.
         */
        public Builder regionInBiome(int id, int biome) {
            if (id < 0) {
                throw new IllegalArgumentException("region id must not be negative, was " + id);
            }
            if (biome < 0) {
                throw new IllegalArgumentException("biome id must not be negative, was " + biome);
            }
            if (id >= biomeOfRegion.length) {
                biomeOfRegion = java.util.Arrays.copyOf(biomeOfRegion, id + 1);
            }
            biomeOfRegion[id] = biome;
            return this;
        }

        public TileMap build() {
            int highest = 0;
            for (int id : region) {
                if (id > highest) {
                    highest = id;
                }
            }
            int regionCount = highest + 1;
            if (biomeOfRegion.length > regionCount) {
                throw new IllegalArgumentException("region " + (biomeOfRegion.length - 1)
                        + " was put in a biome, but the map has only " + regionCount + " regions");
            }
            int[] biomes = java.util.Arrays.copyOf(biomeOfRegion, regionCount);
            return new TileMap(
                    cols, rows, tileSize, terrain.clone(), region.clone(), regionCount, biomes, biomeCount(biomes));
        }

        /** One more than the highest biome any region is in, refusing a gap: a biome nothing is in. */
        private static int biomeCount(int[] biomes) {
            int highest = 0;
            for (int biome : biomes) {
                if (biome > highest) {
                    highest = biome;
                }
            }
            boolean[] named = new boolean[highest + 1];
            for (int biome : biomes) {
                named[biome] = true;
            }
            for (int biome = 0; biome <= highest; biome++) {
                if (!named[biome]) {
                    throw new IllegalArgumentException("no region is in biome " + biome + " of " + (highest + 1)
                            + ": biome ids must be dense from zero");
                }
            }
            return highest + 1;
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
