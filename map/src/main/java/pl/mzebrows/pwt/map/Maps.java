package pl.mzebrows.pwt.map;

import java.util.function.IntFunction;

/**
 * Ready-made maps: the small number of layouts a scenario or a test wants without describing a grid
 * tile by tile.
 *
 * <p>Everything here produces an ordinary {@link TileMap}, so nothing downstream can tell a
 * generated layout from a hand-authored one - which is what keeps the authored path open once real
 * levels exist.
 */
public final class Maps {

    private Maps() {}

    /** One terrain everywhere, one region. The plainest possible level, and what tests want. */
    public static TileMap uniform(int cols, int rows, float tileSize, Terrain kind) {
        return TileMap.builder(cols, rows, tileSize).fill(kind).build();
    }

    /**
     * Open ground partitioned into regions by nearest-seed (a Voronoi partition): pick that many
     * tiles at random, then give every tile to the seed nearest it.
     *
     * <p>Regions before tiles, deliberately. Deciding tile by tile produces noise; deciding region by
     * region produces places, and it is regions the player recognises and the renderer tints. The
     * terrain stays open ground here - this decides the <em>shape</em> of a level's areas, and what
     * grows or blocks in each of them is a later decision made per region.
     *
     * <p>Asking for more regions than there are tiles gets one region per tile rather than an error;
     * anything less than one region is a caller bug and is refused.
     */
    public static TileMap regions(int cols, int rows, float tileSize, int regionCount, Randomness rng) {
        return partitionRegions(TileMap.builder(cols, rows, tileSize), regionCount, rng)
                .build();
    }

    /**
     * Partitions an existing builder's tiles into regions, leaving its terrain alone.
     *
     * <p>Separate from {@link #regions} so a caller can decide the areas first and then lay terrain
     * over them - which is the order that produces places rather than noise, and the order a scenario
     * wants when it is hand-authoring walls on top of a generated partition.
     */
    public static TileMap.Builder partitionRegions(TileMap.Builder builder, int regionCount, Randomness rng) {
        if (regionCount < 1) {
            throw new IllegalArgumentException("a map needs at least one region, was " + regionCount);
        }
        int cols = builder.cols();
        int rows = builder.rows();
        int tiles = cols * rows;
        int wanted = Math.min(regionCount, tiles);

        int[] seedCol = new int[wanted];
        int[] seedRow = new int[wanted];
        boolean[] taken = new boolean[tiles];
        for (int seed = 0; seed < wanted; seed++) {
            int at = rng.nextInt(tiles);
            while (taken[at]) {
                at = (at + 1) % tiles;
            }
            taken[at] = true;
            seedCol[seed] = at % cols;
            seedRow[seed] = at / cols;
        }

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                builder.region(col, row, nearest(col, row, seedCol, seedRow));
            }
        }
        return builder;
    }

    /**
     * Gives every region its own ground, by asking for one terrain per region id.
     *
     * <p>Regions first, then terrain over them, is the order that produces places rather than noise -
     * a patch of organic ground the player can recognise and come back to, instead of speckle. Call it
     * after {@link #partitionRegions}, which is what decides the shapes.
     */
    public static TileMap.Builder paintRegions(TileMap.Builder builder, IntFunction<Terrain> groundForRegion) {
        for (int row = 0; row < builder.rows(); row++) {
            for (int col = 0; col < builder.cols(); col++) {
                builder.set(col, row, groundForRegion.apply(builder.regionAt(col, row)));
            }
        }
        return builder;
    }

    /**
     * Every tile that can be walked to from a starting tile, as a flat grid of flags indexed the way
     * the map is - {@code row * cols + col}.
     *
     * <p>A breadth-first flood fill, four-connected. Four rather than eight on purpose: a unit has
     * width, so a diagonal pinch between two rock corners is not a way through, and counting it as one
     * would report a level as connected that a player cannot actually cross.
     *
     * <p>This is the cheap half of <em>generate, then validate</em>. A generated level has to be
     * checked for the properties it promised - every spawn reachable, no sealed pockets - and either
     * repaired or regenerated; trusting the first result is how a player ends up walled into a corner.
     */
    public static boolean[] reachableFrom(TileMap map, int col, int row) {
        boolean[] reached = new boolean[map.cols() * map.rows()];
        if (!map.passableAtTile(col, row)) {
            return reached;
        }
        int[] queue = new int[reached.length];
        int head = 0;
        int tail = 0;
        queue[tail++] = row * map.cols() + col;
        reached[row * map.cols() + col] = true;
        while (head < tail) {
            int at = queue[head++];
            int atCol = at % map.cols();
            int atRow = at / map.cols();
            tail = visit(map, reached, queue, tail, atCol - 1, atRow);
            tail = visit(map, reached, queue, tail, atCol + 1, atRow);
            tail = visit(map, reached, queue, tail, atCol, atRow - 1);
            tail = visit(map, reached, queue, tail, atCol, atRow + 1);
        }
        return reached;
    }

    /**
     * Fills in every patch of ground that cannot be walked to from a starting tile.
     *
     * <p>The repair half of <em>generate, then validate, then repair</em>. A layout that scatters
     * walls will sooner or later fence off a pocket, and a pocket is worse than it looks: things get
     * placed in it, and the player can never reach them. Turning it into solid ground is the cheap
     * fix - the level loses a little space and gains the guarantee that everywhere you can see, you
     * can get to.
     */
    public static TileMap.Builder sealUnreachable(TileMap.Builder builder, int fromCol, int fromRow, Terrain fill) {
        TileMap probe = builder.build();
        boolean[] reachable = reachableFrom(probe, fromCol, fromRow);
        for (int row = 0; row < probe.rows(); row++) {
            for (int col = 0; col < probe.cols(); col++) {
                if (probe.passableAtTile(col, row) && !reachable[row * probe.cols() + col]) {
                    builder.set(col, row, fill);
                }
            }
        }
        return builder;
    }

    /** Whether one tile can be walked to from another. */
    public static boolean connects(TileMap map, int fromCol, int fromRow, int toCol, int toRow) {
        if (!map.inBounds(toCol, toRow)) {
            return false;
        }
        return reachableFrom(map, fromCol, fromRow)[toRow * map.cols() + toCol];
    }

    private static int visit(TileMap map, boolean[] reached, int[] queue, int tail, int col, int row) {
        if (!map.inBounds(col, row) || !map.passableAtTile(col, row)) {
            return tail;
        }
        int at = row * map.cols() + col;
        if (reached[at]) {
            return tail;
        }
        reached[at] = true;
        queue[tail++] = at;
        return tail;
    }

    /** Ties go to the lower seed index, so the partition is the same on every run. */
    private static int nearest(int col, int row, int[] seedCol, int[] seedRow) {
        int best = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int seed = 0; seed < seedCol.length; seed++) {
            long dx = (long) col - seedCol[seed];
            long dy = (long) row - seedRow[seed];
            long distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = seed;
            }
        }
        return best;
    }
}
