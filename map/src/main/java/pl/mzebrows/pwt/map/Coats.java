package pl.mzebrows.pwt.map;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Lays one biome's coats over its base: clearings, houses, then patches in order, then corridors, then crags, then outcrops. Rock always wins
 * over a patch; an apron tile keeps base ground, and so do a house's inside, doorways and doorsteps; no passage is
 * left under the clearance; nothing is sealed off. Each coat draws from a stream of its own, derived from the biome's, so
 * retuning one moves no other's draws (G0065).
 *
 * <p>The biome's area arrives with its base already laid and whatever is round it - the border, a ridge
 * face spilling in - already rock. Only tiles inside the area are written. Its <em>ends</em> are the
 * tiles, on or just outside its edge, where it is entered - a pass's ends, a spawn area: every end must reach every other, and every
 * open tile of the area must be reachable from them.
 *
 * <p>The techniques, named so they can be looked up (the {@code game-patterns} skill): patches are
 * rectangles or blobs grown by Eden growth; crags are smoothed white noise cut at a quantile, then
 * repaired; outcrops are bars and Ls placed by dart throwing, the clearance off all other rock - so they
 * cannot enclose anything, and the flood fill after each is only the net; corridors are a braided maze carved out of a
 * filled biome - a randomised spanning tree (Kruskal's) over a lattice of long, level cells, loops added after; houses are walled rects thrown the same
 * way, each doorstep an end so the repairs after keep it reached. Package-private: {@code Strip}
 * is the seam a caller crosses.
 *
 * <p>Allocates freely: a world is built once.
 */
final class Coats {

    private static final long PATCHES = 0x6A09_E667_F3BC_C909L;
    private static final long CRAGS = 0xBB67_AE85_84CA_A73BL;
    private static final long OUTCROPS = 0x3C6E_F372_FE94_F82BL;
    private static final long HOUSES = 0x428A_2F98_D728_AE22L;
    private static final long CLEARINGS = 0x9B05_688C_68D6_A0C2L;
    private static final long ICE = 0x1F83_D9AB_5BE0_CD19L;
    private static final long CORRIDORS = 0x5BE0_CD19_1F83_D9ABL;
    private static final long NOOKS = 0x2B99_2DDF_A232_59D7L;

    /**
     * How far a way's either side must be from each other, walking, with the way shut, for it to be a chokepoint worth
     * icing: further than this many steps round, and shutting it is a detour or a siege; nearer, and it is a gap
     * beside a lone rock (ADR 0025).
     */
    private static final int ICE_DETOUR = 16;

    /** A house's walls, in the order they are drawn from. */
    private static final int WEST = 0;

    private static final int EAST = 1;
    private static final int SOUTH = 2;
    private static final int NORTH = 3;

    /** Places tried for one piece before its coat counts as full. */
    private static final int TRIES = 600;

    /** How much of its box a blob grows to, before smoothing. */
    private static final float BLOB_FILL = 0.85f;

    /** Frontier tiles drawn for each step of a blob's growth; the one nearest the middle is taken. */
    private static final int BLOB_CANDIDATES = 3;

    /** An outcrop's arm: how far it stands off the bar, and how far along it runs - the Adventures' shape. */
    private static final int ARM_OUT_MAX = 2;

    /** How many columns a ragged bar's bumps keep between them, so none leaves a slot under the clearance. */
    private static final int BUMP_APART = 2;

    private static final int ARM_ALONG_MIN = 2;
    private static final int ARM_ALONG_MAX = 4;

    /** How often a corridor's edge wanders a row, column by column: about every three, so it reads as rock. */
    private static final float WANDER_CHANCE = 0.3f;

    /** Bounds on the crag repairs, each of which converges long before. */
    private static final int REPAIR_ROUNDS = 80;

    private final int cols;
    private final int rows;
    private final Terrain[] tiles;
    private final TileRect area;
    private final BiomeGround ground;
    private final @Nullable Terrain west;
    private final @Nullable Terrain east;
    private final boolean[] apron;
    private final int clearance;

    /** Where the biome is entered: the strip's ends, then each house's doorsteps as it is laid. */
    private int[] ends;

    /** Scratch: the tiles of the outcrop being tried. */
    private final boolean[] own;

    /** Per tile: inside a clearing, where no crag and no outcrop is laid (G0115). */
    private final boolean[] clear;

    private final List<Clearing> clearings = new ArrayList<>();

    private final List<Clearing> nooks = new ArrayList<>();

    /**
     * What one biome's coats laid besides its ground: its houses, its clearings and its nooks.
     *
     * @param houses as many as fitted up to the count drawn - fewer than the biome's {@code min} only when there was no
     *     room, which the strip sweep reports
     * @param clearings every clearing its ground asks for, west to east
     * @param nooks every nook its ground asks for, west to east
     * @param ice every run of tiles ice stands across, a chokepoint each
     */
    record Laid(List<House> houses, List<Clearing> clearings, List<Clearing> nooks, List<TileRect> ice) {

        Laid {
            houses = List.copyOf(houses);
            clearings = List.copyOf(clearings);
            nooks = List.copyOf(nooks);
            ice = List.copyOf(ice);
        }
    }

    private Coats(
            TileMap.Builder map,
            TileRect area,
            BiomeGround ground,
            @Nullable Terrain west,
            @Nullable Terrain east,
            boolean[] apron,
            int[] ends,
            int clearance) {
        this.cols = map.cols();
        this.rows = map.rows();
        this.tiles = new Terrain[cols * rows];
        for (int i = 0; i < tiles.length; i++) {
            tiles[i] = map.terrainAt(i % cols, i / cols);
        }
        this.area = area;
        this.ground = ground;
        this.west = west;
        this.east = east;
        this.apron = apron;
        this.ends = ends;
        this.clearance = clearance;
        this.own = new boolean[cols * rows];
        this.clear = new boolean[cols * rows];
    }

    /**
     * Lays the coats into {@code area} of {@code map}.
     *
     * @param west the base of the biome to the west, null when there is none
     * @param east the base of the biome to the east, null when there is none
     * @param apron per tile of the map, {@code row * cols + col}: whether it is kept base ground. A house's inside,
     *     doorways and doorsteps are added to it
     * @param ends tiles of the map, {@code row * cols + col}, where the biome is entered; at least one
     * @param rules the clearance, and every house's size and doorways
     * @param biome this biome's stream; only derived from, never drawn
     * @return the houses and clearings laid
     */
    static Laid lay(
            TileMap.Builder map,
            TileRect area,
            BiomeGround ground,
            @Nullable Terrain west,
            @Nullable Terrain east,
            boolean[] apron,
            int[] ends,
            StripRules rules,
            Randomness biome) {
        if (ends.length == 0) {
            throw new IllegalArgumentException("a biome needs an end to be entered by");
        }
        if (apron.length != map.cols() * map.rows()) {
            throw new IllegalArgumentException("the apron is one flag per tile of the map");
        }
        Coats coats = new Coats(map, area, ground, west, east, apron, ends, rules.clearance());
        coats.pieces();
        coats.clearings(biome.derive(CLEARINGS));
        coats.nooks(biome.derive(NOOKS));
        List<House> houses = coats.houses(rules.houses(), biome.derive(HOUSES));
        coats.patches(biome.derive(PATCHES));
        coats.corridors(biome.derive(CORRIDORS));
        coats.crags(biome.derive(CRAGS));
        coats.outcrops(biome.derive(OUTCROPS));
        List<TileRect> ice = coats.ice(biome.derive(ICE));
        for (int row = area.row(); row < area.endRow(); row++) {
            for (int col = area.col(); col < area.endCol(); col++) {
                map.set(col, row, coats.tiles[row * coats.cols + col]);
            }
        }
        return new Laid(houses, coats.clearings, coats.nooks, ice);
    }

    // ---- pieces ----

    /** The biome's fixed rock, each rect at its offset from the anchor - a finale's set piece (G0064). */
    private void pieces() {
        TileRect anchor = Strip.anchor(area);
        for (TileRect piece : ground.pieces()) {
            for (int row = piece.row(); row < piece.endRow(); row++) {
                for (int col = piece.col(); col < piece.endCol(); col++) {
                    int c = anchor.col() + col;
                    int r = anchor.row() + row;
                    if (!area.contains(c, r)) {
                        throw new IllegalArgumentException("a piece " + piece + " reaches past " + area);
                    }
                    tiles[r * cols + c] = Terrain.ROCK;
                }
            }
        }
    }

    // ---- clearings ----

    /**
     * Each clearing its ground asks for (G0115, provisional, for review): clearing {@code k} of {@code n} centred on a
     * column dealt within a twelfth of the width of {@code k / (n + 1)} of it, on the side away from the middle - so of
     * two, the west one is in the west third and the west half, and the east one in the east third and the east half,
     * whichever way a biome's sites are banded - and on a row a radius in from either edge. Its disc is marked clear,
     * so no later coat lays rock in it, and its centre is an end, so the crag repairs keep it reached.
     */
    private void clearings(Randomness rng) {
        Clearings wanted = ground.clearings();
        int n = wanted.count();
        int radius = wanted.radius();
        int spread = Math.max(1, area.width() / 12);
        int lowRow = area.row() + radius;
        int highRow = area.endRow() - 1 - radius;
        if (n > 0 && highRow < lowRow) {
            throw new IllegalArgumentException("a clearing of radius " + radius + " does not fit in " + area);
        }
        for (int k = 1; k <= n; k++) {
            int edge = area.col() + k * area.width() / (n + 1);
            int from;
            int to;
            if (2 * k < n + 1) {
                from = edge - spread;
                to = edge - 1;
            } else if (2 * k > n + 1) {
                from = edge;
                to = edge + spread - 1;
            } else {
                from = edge - spread / 2;
                to = edge + spread / 2;
            }
            Clearing clearing = new Clearing(between(rng, from, to), between(rng, lowRow, highRow), radius);
            clear(clearing);
            clearings.add(clearing);
        }
    }

    /**
     * Each nook its ground asks for (G0115, amended 2026-11-24): nook {@code k} of {@code n} centred on a column of the
     * {@code k}-th of {@code n} equal bands of the width less a twelfth at either end - where a ridge's face wanders in,
     * laid before any coat - a radius in from its edges, and on a row of the top third or
     * the bottom third, a radius in from the edge - a row dealt in each, and whichever lies farther from the nearest
     * clearing's centre taken, since a hole keeps off the point a clearing holds. Marked clear and kept reached, as a
     * clearing is.
     */
    private void nooks(Randomness rng) {
        Clearings wanted = ground.clearings();
        int n = wanted.nooks();
        int radius = wanted.nookRadius();
        int third = area.height() / 3;
        int inset = Math.max(1, area.width() / 12);
        int west = area.col() + inset;
        int width = area.width() - 2 * inset;
        if (n > 0 && (width / n <= 2 * radius || third - 1 < radius)) {
            throw new IllegalArgumentException(n + " nooks of radius " + radius + " do not fit in " + area);
        }
        for (int k = 0; k < n; k++) {
            int from = west + k * width / n + radius;
            int to = west + (k + 1) * width / n - 1 - radius;
            int col = between(rng, from, to);
            int top = between(rng, area.row() + radius, area.row() + third - 1);
            int bottom = between(rng, area.endRow() - third, area.endRow() - 1 - radius);
            Clearing nook = new Clearing(col, nearest(col, top) >= nearest(col, bottom) ? top : bottom, radius);
            clear(nook);
            nooks.add(nook);
        }
    }

    /** The squared distance in tiles from a tile to the nearest clearing's centre; the most there is with none. */
    private long nearest(int col, int row) {
        long nearest = Long.MAX_VALUE;
        for (Clearing clearing : clearings) {
            long dc = (long) col - clearing.col();
            long dr = (long) row - clearing.row();
            nearest = Math.min(nearest, dc * dc + dr * dr);
        }
        return nearest;
    }

    /** Marks a disc clear, so no later coat lays rock in it, and its centre an end, so the crag repairs keep it reached. */
    private void clear(Clearing disc) {
        int radius = disc.radius();
        for (int row = disc.row() - radius; row <= disc.row() + radius; row++) {
            for (int col = disc.col() - radius; col <= disc.col() + radius; col++) {
                if (area.contains(col, row) && disc.contains(col, row)) {
                    clear[row * cols + col] = true;
                }
            }
        }
        ends = Arrays.copyOf(ends, ends.length + 1);
        ends[ends.length - 1] = disc.row() * cols + disc.col();
    }

    // ---- houses ----

    /** As many houses as drawn between the biome's {@code min} and {@code max}, until one finds nowhere to stand. */
    private List<House> houses(HouseRules rules, Randomness rng) {
        Houses wanted = ground.houses();
        int count = between(rng, wanted.min(), wanted.max());
        List<House> laid = new ArrayList<>();
        for (int k = 0; k < count; k++) {
            House house = house(rules, rng);
            if (house == null) {
                break;
            }
            laid.add(house);
        }
        return laid;
    }

    /**
     * Dart throwing, as an outcrop is placed: a rect at a random place, kept if it and the clearance round it are
     * open ground off every apron - so it stands the clearance off all rock and every other house, and cannot
     * enclose anything. Then its wall, its doorways in walls of their own, and its inside and doorsteps kept as
     * apron, each doorstep an end of the biome.
     */
    private @Nullable House house(HouseRules rules, Randomness rng) {
        for (int attempt = 0; attempt < TRIES; attempt++) {
            int w = between(rng, rules.widthMin(), rules.widthMax());
            int h = between(rng, rules.heightMin(), rules.heightMax());
            if (w + 2 * clearance > area.width() || h + 2 * clearance > area.height()) {
                continue;
            }
            int x = area.col() + clearance + rng.nextInt(area.width() - w - 2 * clearance + 1);
            int y = area.row() + clearance + rng.nextInt(area.height() - h - 2 * clearance + 1);
            if (!openRound(x, y, w, h)) {
                continue;
            }
            TileRect walls = new TileRect(x, y, w, h);
            List<TileRect> doorways = doorways(walls, rules, rng);
            build(walls, doorways);
            return new House(walls, doorways);
        }
        return null;
    }

    /** The rect and the clearance round it all open ground, none of it apron. */
    private boolean openRound(int x, int y, int w, int h) {
        for (int row = y - clearance; row < y + h + clearance; row++) {
            for (int col = x - clearance; col < x + w + clearance; col++) {
                if (rock(col, row) || apron[row * cols + col] || clear[row * cols + col]) {
                    return false;
                }
            }
        }
        return true;
    }

    /** {@code doorways} of the four walls drawn, each a doorway drawn along it off the corners. */
    private static List<TileRect> doorways(TileRect walls, HouseRules rules, Randomness rng) {
        int[] order = {WEST, EAST, SOUTH, NORTH};
        for (int i = 0; i < rules.doorways(); i++) {
            int pick = i + rng.nextInt(order.length - i);
            int swap = order[i];
            order[i] = order[pick];
            order[pick] = swap;
        }
        int width = rules.doorwayWidth();
        List<TileRect> doorways = new ArrayList<>();
        for (int i = 0; i < rules.doorways(); i++) {
            int wall = order[i];
            boolean standing = wall == WEST || wall == EAST;
            int along = 1 + rng.nextInt((standing ? walls.height() : walls.width()) - 2 - width + 1);
            doorways.add(
                    switch (wall) {
                        case WEST -> new TileRect(walls.col(), walls.row() + along, 1, width);
                        case EAST -> new TileRect(walls.endCol() - 1, walls.row() + along, 1, width);
                        case SOUTH -> new TileRect(walls.col() + along, walls.row(), width, 1);
                        default -> new TileRect(walls.col() + along, walls.endRow() - 1, width, 1);
                    });
        }
        return doorways;
    }

    /** The wall round the rect but for the doorways; the inside, doorways and doorsteps kept; doorsteps as ends. */
    private void build(TileRect walls, List<TileRect> doorways) {
        for (int row = walls.row(); row < walls.endRow(); row++) {
            for (int col = walls.col(); col < walls.endCol(); col++) {
                boolean edge = row == walls.row()
                        || row == walls.endRow() - 1
                        || col == walls.col()
                        || col == walls.endCol() - 1;
                if (edge) {
                    tiles[row * cols + col] = Terrain.WALL;
                } else {
                    apron[row * cols + col] = true;
                }
            }
        }
        int[] entered = Arrays.copyOf(ends, ends.length + doorways.size());
        for (int d = 0; d < doorways.size(); d++) {
            TileRect door = doorways.get(d);
            int dc = door.width() == 1 ? (door.col() == walls.col() ? -1 : 1) : 0;
            int dr = door.height() == 1 ? (door.row() == walls.row() ? -1 : 1) : 0;
            for (int row = door.row(); row < door.endRow(); row++) {
                for (int col = door.col(); col < door.endCol(); col++) {
                    for (int step = 0; step <= clearance; step++) {
                        int at = (row + dr * step) * cols + col + dc * step;
                        tiles[at] = ground.base();
                        apron[at] = true;
                    }
                }
            }
            entered[ends.length + d] = (door.row() + dr * clearance) * cols + door.col() + dc * clearance;
        }
        ends = entered;
    }

    // ---- patches ----

    /** Each layer in order, piece by piece, until it has taken its density of the area or has no room. */
    private void patches(Randomness rng) {
        boolean[] used = new boolean[tiles.length];
        for (Patch layer : ground.patches()) {
            float wanted = layer.density() * area.tileCount();
            int laid = 0;
            while (laid < wanted) {
                int added = patch(layer, used, rng);
                if (added < 0) {
                    break;
                }
                laid += added;
            }
        }
    }

    /** One piece, with a tile of room round it from every other piece; how many tiles it took, or -1. */
    private int patch(Patch layer, boolean[] used, Randomness rng) {
        for (int attempt = 0; attempt < TRIES; attempt++) {
            int w = between(rng, layer.sizeMin(), layer.sizeMax());
            int h = between(rng, layer.sizeMin(), layer.sizeMax());
            if (w > area.width() || h > area.height()) {
                continue;
            }
            int x = area.col() + rng.nextInt(area.width() - w + 1);
            int y = area.row() + rng.nextInt(area.height() - h + 1);
            if (!roomFor(used, x, y, w, h)) {
                continue;
            }
            boolean nearerWest = 2 * x + w < 2 * area.col() + area.width();
            Terrain paint = layer.ground().resolve(nearerWest ? west : east, ground.base(), ground.fallback());
            int added =
                    switch (layer) {
                        case Patch.Rect _ -> paintRect(x, y, w, h, paint);
                        case Patch.Blob _ -> paintBlob(x, y, w, h, paint, rng);
                    };
            for (int row = y; row < y + h; row++) {
                for (int col = x; col < x + w; col++) {
                    used[row * cols + col] = true;
                }
            }
            return added;
        }
        return -1;
    }

    /** No piece within a tile of the box, and no apron inside it. */
    private boolean roomFor(boolean[] used, int x, int y, int w, int h) {
        for (int row = y - 1; row <= y + h; row++) {
            for (int col = x - 1; col <= x + w; col++) {
                if (inside(col, row) && used[row * cols + col]) {
                    return false;
                }
            }
        }
        for (int row = y; row < y + h; row++) {
            for (int col = x; col < x + w; col++) {
                if (apron[row * cols + col]) {
                    return false;
                }
            }
        }
        return true;
    }

    private int paintRect(int x, int y, int w, int h, Terrain paint) {
        int added = 0;
        for (int row = y; row < y + h; row++) {
            for (int col = x; col < x + w; col++) {
                added += paint(row * cols + col, paint);
            }
        }
        return added;
    }

    /**
     * Eden growth from the middle of the box out to its rim and a tile past it: of a few frontier tiles
     * drawn, the nearest the middle is taken, so it stays compact with a ragged edge. Then one smoothing
     * pass - a notch with three sides in is filled, a whisker with one or none is dropped.
     */
    private int paintBlob(int x, int y, int w, int h, Terrain paint, Randomness rng) {
        int boxCol = x - 1;
        int boxRow = y - 1;
        int boxW = w + 2;
        int boxH = h + 2;
        boolean[] in = new boolean[boxW * boxH];
        int[] frontier = new int[boxW * boxH * 4 + 4];
        float midCol = x + (w - 1) / 2f;
        float midRow = y + (h - 1) / 2f;
        int wanted = Math.max(1, Math.round(w * h * BLOB_FILL));

        int start = (Math.round(midRow) - boxRow) * boxW + (Math.round(midCol) - boxCol);
        in[start] = true;
        int size = 1;
        int open = grow(start, in, frontier, 0, boxCol, boxRow, boxW, boxH);
        while (size < wanted && open > 0) {
            int pick = rng.nextInt(open);
            for (int k = 1; k < BLOB_CANDIDATES; k++) {
                int other = rng.nextInt(open);
                if (fromMiddle(frontier[other], boxCol, boxRow, boxW, midCol, midRow, w, h)
                        < fromMiddle(frontier[pick], boxCol, boxRow, boxW, midCol, midRow, w, h)) {
                    pick = other;
                }
            }
            int tile = frontier[pick];
            frontier[pick] = frontier[--open];
            if (in[tile]) {
                continue;
            }
            in[tile] = true;
            size++;
            open = grow(tile, in, frontier, open, boxCol, boxRow, boxW, boxH);
        }

        boolean[] smoothed = in.clone();
        for (int at = 0; at < in.length; at++) {
            int col = boxCol + at % boxW;
            int row = boxRow + at / boxW;
            if (!area.contains(col, row)) {
                continue;
            }
            int around = inAt(in, at % boxW - 1, at / boxW, boxW, boxH)
                    + inAt(in, at % boxW + 1, at / boxW, boxW, boxH)
                    + inAt(in, at % boxW, at / boxW - 1, boxW, boxH)
                    + inAt(in, at % boxW, at / boxW + 1, boxW, boxH);
            if (!in[at] && around >= 3) {
                smoothed[at] = true;
            } else if (in[at] && around <= 1) {
                smoothed[at] = false;
            }
        }

        int added = 0;
        for (int at = 0; at < smoothed.length; at++) {
            if (smoothed[at]) {
                added += paint((boxRow + at / boxW) * cols + boxCol + at % boxW, paint);
            }
        }
        return added;
    }

    /** Puts a blob tile's neighbours inside the box and the area on the frontier; the new frontier size. */
    private int grow(int at, boolean[] in, int[] frontier, int open, int boxCol, int boxRow, int boxW, int boxH) {
        int localCol = at % boxW;
        int localRow = at / boxW;
        int count = open;
        count = offer(localCol - 1, localRow, in, frontier, count, boxCol, boxRow, boxW, boxH);
        count = offer(localCol + 1, localRow, in, frontier, count, boxCol, boxRow, boxW, boxH);
        count = offer(localCol, localRow - 1, in, frontier, count, boxCol, boxRow, boxW, boxH);
        return offer(localCol, localRow + 1, in, frontier, count, boxCol, boxRow, boxW, boxH);
    }

    private int offer(
            int localCol,
            int localRow,
            boolean[] in,
            int[] frontier,
            int open,
            int boxCol,
            int boxRow,
            int boxW,
            int boxH) {
        if (localCol < 0 || localRow < 0 || localCol >= boxW || localRow >= boxH) {
            return open;
        }
        if (!area.contains(boxCol + localCol, boxRow + localRow) || in[localRow * boxW + localCol]) {
            return open;
        }
        frontier[open] = localRow * boxW + localCol;
        return open + 1;
    }

    private static float fromMiddle(
            int at, int boxCol, int boxRow, int boxW, float midCol, float midRow, int w, int h) {
        float dc = (boxCol + at % boxW - midCol) / w;
        float dr = (boxRow + at / boxW - midRow) / h;
        return dc * dc + dr * dr;
    }

    private static int inAt(boolean[] in, int localCol, int localRow, int boxW, int boxH) {
        if (localCol < 0 || localRow < 0 || localCol >= boxW || localRow >= boxH) {
            return 0;
        }
        return in[localRow * boxW + localCol] ? 1 : 0;
    }

    /** Lays patch ground on one tile unless it is apron or rock; 1 if it did. */
    private int paint(int at, Terrain paint) {
        if (apron[at] || !tiles[at].passable()) {
            return 0;
        }
        tiles[at] = paint;
        return 1;
    }

    // ---- corridors ----

    /**
     * The rift's maze (G0115, amended 2026-11-23): every open tile of the area that no apron or clearing keeps filled
     * with solid, then corridors, links and chambers carved back out of it ({@link Corridors} has the shape), the ends
     * tunnelled to the maze while one is cut off, and the crag repairs after - no passage under the clearance, nothing
     * sealed off. A braided maze: a randomised spanning tree over the cells (Kruskal's), loops added after.
     */
    private void corridors(Randomness rng) {
        Corridors maze = ground.corridors();
        if (!maze.on()) {
            return;
        }
        if (maze.widthMin() < clearance) {
            throw new IllegalArgumentException(
                    "a corridor " + maze.widthMin() + " wide is under the clearance " + clearance);
        }
        boolean[] mine = new boolean[tiles.length];
        Terrain[] under = new Terrain[tiles.length];
        for (int row = area.row(); row < area.endRow(); row++) {
            for (int col = area.col(); col < area.endCol(); col++) {
                int at = row * cols + col;
                if (!apron[at] && !clear[at] && tiles[at].passable()) {
                    mine[at] = true;
                    under[at] = tiles[at];
                    tiles[at] = maze.terrain();
                }
            }
        }
        int deepest = maze.widthMax() + maze.wander();
        int pitch = deepest + maze.wall();
        int corridors = Math.max(1, (area.height() + maze.wall()) / pitch);
        int spare = Math.max(0, area.height() - corridors * deepest - (corridors - 1) * maze.wall());
        int firstRow = area.row() + rng.nextInt(spare + 1);
        int cells = Math.max(1, area.width() / maze.cell());
        int nodes = corridors * cells;
        int[] width = new int[nodes];
        for (int node = 0; node < nodes; node++) {
            width[node] = between(rng, maze.widthMin(), Math.min(maze.widthMax(), area.height()));
        }
        // Joins: along a corridor, node to node + 1; links: down a wall, node to node + cells.
        boolean[] joined = new boolean[nodes];
        boolean[] linked = new boolean[nodes];
        int[] group = new int[nodes];
        for (int node = 0; node < nodes; node++) {
            group[node] = node;
        }
        List<Integer> shut = new ArrayList<>();
        for (int node = 0; node < nodes; node++) {
            if (node % cells == cells - 1) {
                continue;
            }
            if (rng.nextFloat() < maze.breaks()) {
                shut.add(node);
            } else {
                joined[node] = true;
                union(group, node, node + 1);
            }
        }
        List<Integer> links = new ArrayList<>();
        for (int node = 0; node + cells < nodes; node++) {
            links.add(node);
        }
        shuffle(links, rng);
        shuffle(shut, rng);
        List<Integer> spareLinks = new ArrayList<>();
        for (int node : links) {
            if (union(group, node, node + cells)) {
                linked[node] = true;
            } else {
                spareLinks.add(node);
            }
        }
        for (int node : shut) {
            if (union(group, node, node + 1)) {
                joined[node] = true;
            }
        }
        for (int node : spareLinks) {
            linked[node] = rng.nextFloat() < maze.loops();
        }
        for (int corridor = 0; corridor < corridors; corridor++) {
            corridor(maze, firstRow + corridor * pitch, corridor * cells, cells, width, joined, mine, under, rng);
        }
        for (int node = 0; node < nodes; node++) {
            if (!linked[node]) {
                continue;
            }
            int corridor = node / cells;
            int cell = node % cells;
            int top = firstRow + corridor * pitch;
            int bottom = firstRow + (corridor + 1) * pitch + maze.wander() + maze.widthMin();
            int from = cellStart(cell, cells);
            int to = cellStart(cell + 1, cells) - (cell < cells - 1 ? maze.wall() : 0);
            if (rng.nextFloat() < maze.chambers()) {
                carve(from, top, to - from, bottom - top, mine, under);
            } else {
                int across = Math.min(to - from, between(rng, maze.widthMin(), maze.widthMax()));
                int col = from + rng.nextInt(to - from - across + 1);
                carve(col, top, across, bottom - top, mine, under);
            }
        }
        // A way in opens onto open ground, the clearance round its apron, never straight into a corridor's wall.
        for (int row = area.row(); row < area.endRow(); row++) {
            for (int col = area.col(); col < area.endCol(); col++) {
                if (apron[row * cols + col]) {
                    carve(col - clearance, row - clearance, 2 * clearance + 1, 2 * clearance + 1, mine, under);
                }
            }
        }
        tunnelEnds(mine, under);
        widen(mine, under);
        sealPockets(maze.terrain());
    }

    /**
     * One corridor, column by column, its edges wandering as rock does (G0115, amended 2026-11-23): a walk that, on a column
     * in {@link #WANDER_CHANCE}, widens or narrows it a row or shifts it a row within {@code wander}, starting each
     * cell at the width dealt to it - and only ever so that two columns side by side share the clearance of rows, so the
     * way along it never pinches under it. The last {@code wall} columns of a cell whose join is shut are left solid.
     */
    private void corridor(
            Corridors maze,
            int top,
            int first,
            int cells,
            int[] width,
            boolean[] joined,
            boolean[] mine,
            Terrain[] under,
            Randomness rng) {
        int off = rng.nextInt(maze.wander() + 1);
        int wide = width[first];
        for (int cell = 0; cell < cells; cell++) {
            int node = first + cell;
            int from = cellStart(cell, cells);
            int to = cellStart(cell + 1, cells);
            int shutFrom = cell < cells - 1 && !joined[node] ? to - maze.wall() : to;
            for (int col = from; col < to; col++) {
                int nextOff = off;
                int nextWide = col == from ? width[node] : wide;
                if (col != from && rng.nextFloat() < WANDER_CHANCE) {
                    switch (rng.nextInt(4)) {
                        case 0 -> nextWide++;
                        case 1 -> nextWide--;
                        case 2 -> nextOff++;
                        default -> nextOff--;
                    }
                }
                boolean fits = nextWide >= maze.widthMin()
                        && nextWide <= maze.widthMax()
                        && nextOff >= 0
                        && nextOff <= maze.wander()
                        && Math.min(off + wide, nextOff + nextWide) - Math.max(off, nextOff) >= clearance;
                if (fits || col == from) {
                    off = fits ? nextOff : off;
                    wide = fits ? nextWide : wide;
                }
                if (col < shutFrom) {
                    carve(col, top + off, 1, wide, mine, under);
                }
            }
        }
    }

    /** The first column of cell {@code cell} of {@code cells}, spread evenly over the area; the area's end past the last. */
    private int cellStart(int cell, int cells) {
        return area.col() + cell * area.width() / cells;
    }

    /** Gives back the ground under every tile of the rect the coat filled, inside the area. */
    private void carve(int col, int row, int w, int h, boolean[] mine, Terrain[] under) {
        for (int r = row; r < row + h; r++) {
            for (int c = col; c < col + w; c++) {
                int at = index(c, r);
                if (area.contains(c, r) && mine[at]) {
                    restore(at, mine, under);
                }
            }
        }
    }

    /**
     * While an end is cut off, the shortest way from it through what the coat filled to what the first end reaches,
     * carved the clearance wide: a breadth-first search, never through rock the coat did not lay.
     */
    private void tunnelEnds(boolean[] mine, Terrain[] under) {
        for (int round = 0; round < REPAIR_ROUNDS; round++) {
            boolean[] reached = reach();
            int cut = -1;
            for (int end : ends) {
                if (!reached[end]) {
                    cut = end;
                    break;
                }
            }
            if (cut < 0) {
                return;
            }
            int[] from = new int[tiles.length];
            Arrays.fill(from, -2);
            int[] queue = new int[tiles.length];
            int head = 0;
            int tail = 0;
            queue[tail++] = cut;
            from[cut] = -1;
            int met = -1;
            while (head < tail && met < 0) {
                int at = queue[head++];
                if (reached[at]) {
                    met = at;
                    break;
                }
                int col = at % cols;
                int row = at / cols;
                for (int d = 0; d < 4; d++) {
                    int nc = col + (d == 0 ? 1 : d == 1 ? -1 : 0);
                    int nr = row + (d == 2 ? 1 : d == 3 ? -1 : 0);
                    if (nc < area.col() - 1 || nc > area.endCol() || nr < area.row() - 1 || nr > area.endRow()) {
                        continue;
                    }
                    int next = index(nc, nr);
                    if (next < 0 || from[next] != -2 || (rock(nc, nr) && !mine[next])) {
                        continue;
                    }
                    from[next] = at;
                    queue[tail++] = next;
                }
            }
            if (met < 0) {
                return;
            }
            for (int at = met; at >= 0; at = from[at]) {
                carve(at % cols, at / cols, clearance, clearance, mine, under);
            }
        }
    }

    private static boolean union(int[] group, int a, int b) {
        int ra = root(group, a);
        int rb = root(group, b);
        if (ra == rb) {
            return false;
        }
        group[Math.max(ra, rb)] = Math.min(ra, rb);
        return true;
    }

    private static void shuffle(List<Integer> list, Randomness rng) {
        for (int i = list.size() - 1; i > 0; i--) {
            int pick = rng.nextInt(i + 1);
            Integer swap = list.get(i);
            list.set(i, list.get(pick));
            list.set(pick, swap);
        }
    }

    // ---- crags ----

    /**
     * White noise over the area, kept the clearance off its edges, smoothed, and cut at the quantile that
     * makes the wanted share rock; then repaired - ends reopened, narrow passages widened, pockets sealed.
     */
    private void crags(Randomness rng) {
        Crags crags = ground.crags();
        if (crags.density() <= 0f) {
            return;
        }
        int left = area.col() + clearance;
        int right = area.endCol() - 1 - clearance;
        int bottom = area.row() + clearance;
        int top = area.endRow() - 1 - clearance;
        float[] noise = new float[tiles.length];
        Arrays.fill(noise, -1f);
        int candidates = 0;
        for (int row = bottom; row <= top; row++) {
            for (int col = left; col <= right; col++) {
                int at = row * cols + col;
                if (!apron[at] && !clear[at] && tiles[at].passable()) {
                    noise[at] = rng.nextFloat();
                    candidates++;
                }
            }
        }
        for (int pass = 0; pass < crags.smoothing(); pass++) {
            noise = smoothed(noise, left, right, bottom, top);
        }
        int wanted = Math.min(candidates, Math.round(crags.density() * area.tileCount()));
        if (wanted == 0) {
            return;
        }
        float[] values = new float[candidates];
        int found = 0;
        for (float value : noise) {
            if (value >= 0f) {
                values[found++] = value;
            }
        }
        Arrays.sort(values);
        float threshold = values[candidates - wanted];

        boolean[] mine = new boolean[tiles.length];
        Terrain[] under = new Terrain[tiles.length];
        for (int at = 0; at < tiles.length; at++) {
            if (noise[at] >= threshold) {
                mine[at] = true;
                under[at] = tiles[at];
                tiles[at] = crags.terrain();
            }
        }
        reopenEnds(mine, under);
        widen(mine, under);
        sealPockets(crags.terrain());
    }

    /** Each noise value the mean of itself and its neighbours that carry one. */
    private float[] smoothed(float[] noise, int left, int right, int bottom, int top) {
        float[] next = new float[noise.length];
        Arrays.fill(next, -1f);
        for (int row = bottom; row <= top; row++) {
            for (int col = left; col <= right; col++) {
                int at = row * cols + col;
                if (noise[at] < 0f) {
                    continue;
                }
                float sum = 0f;
                int count = 0;
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        float value = noise[(row + dr) * cols + col + dc];
                        if (value >= 0f) {
                            sum += value;
                            count++;
                        }
                    }
                }
                next[at] = sum / count;
            }
        }
        return next;
    }

    /** While an end is cut off, clears the first crag mass that touches what the first end reaches. */
    private void reopenEnds(boolean[] mine, Terrain[] under) {
        for (int round = 0; round < REPAIR_ROUNDS; round++) {
            boolean[] reached = reach();
            if (endsMeet(reached)) {
                return;
            }
            int touching = firstMassTouching(reached, mine);
            if (touching < 0) {
                return;
            }
            clearMass(touching, mine, under);
        }
    }

    private int firstMassTouching(boolean[] reached, boolean[] mine) {
        for (int row = area.row(); row < area.endRow(); row++) {
            for (int col = area.col(); col < area.endCol(); col++) {
                int at = row * cols + col;
                if (mine[at]
                        && (reachedAt(reached, col - 1, row)
                                || reachedAt(reached, col + 1, row)
                                || reachedAt(reached, col, row - 1)
                                || reachedAt(reached, col, row + 1))) {
                    return at;
                }
            }
        }
        return -1;
    }

    /** Gives back the ground under a whole crag mass, eight-connected. */
    private void clearMass(int start, boolean[] mine, Terrain[] under) {
        int[] stack = new int[tiles.length];
        int depth = 0;
        stack[depth++] = start;
        restore(start, mine, under);
        while (depth > 0) {
            int at = stack[--depth];
            int col = at % cols;
            int row = at / cols;
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    int next = (row + dr) * cols + col + dc;
                    if (inside(col + dc, row + dr) && mine[next]) {
                        restore(next, mine, under);
                        stack[depth++] = next;
                    }
                }
            }
        }
    }

    /**
     * A run of open ground shorter than the clearance between two rocks, along a row or a column, loses one
     * of them - the crag's, never anything that was rock before the crags were laid.
     */
    private void widen(boolean[] mine, Terrain[] under) {
        for (int round = 0; round < REPAIR_ROUNDS; round++) {
            int widened = widenAlong(1, 0, mine, under) + widenAlong(0, 1, mine, under);
            if (widened == 0) {
                return;
            }
        }
    }

    private int widenAlong(int dc, int dr, boolean[] mine, Terrain[] under) {
        int widened = 0;
        for (int row = area.row(); row < area.endRow(); row++) {
            for (int col = area.col(); col < area.endCol(); col++) {
                if (rock(col, row) || !rock(col - dc, row - dr)) {
                    continue;
                }
                int run = 0;
                while (run <= clearance && !rock(col + run * dc, row + run * dr)) {
                    run++;
                }
                if (run >= clearance) {
                    continue;
                }
                int before = index(col - dc, row - dr);
                int after = index(col + run * dc, row + run * dr);
                if (before >= 0 && mine[before]) {
                    restore(before, mine, under);
                    widened++;
                } else if (after >= 0 && mine[after]) {
                    restore(after, mine, under);
                    widened++;
                }
            }
        }
        return widened;
    }

    /** Whatever open ground of the area the ends cannot reach becomes {@code solid} - what the coat is of. */
    private void sealPockets(Terrain solid) {
        boolean[] reached = reach();
        for (int row = area.row(); row < area.endRow(); row++) {
            for (int col = area.col(); col < area.endCol(); col++) {
                int at = row * cols + col;
                if (tiles[at].passable() && !reached[at]) {
                    tiles[at] = solid;
                }
            }
        }
    }

    private void restore(int at, boolean[] mine, Terrain[] under) {
        mine[at] = false;
        tiles[at] = under[at];
    }

    // ---- outcrops ----

    /** Outcrop after outcrop until they take their density of the area, or one finds nowhere to stand. */
    private void outcrops(Randomness rng) {
        Outcrops outcrops = ground.outcrops();
        float wanted = outcrops.density() * area.tileCount();
        int laid = 0;
        while (laid < wanted) {
            int added = outcrop(outcrops, rng);
            if (added < 0) {
                return;
            }
            laid += added;
        }
    }

    /** Dart throwing: a shape at a random place, kept if it fits and every end still reaches every tile. */
    private int outcrop(Outcrops outcrops, Randomness rng) {
        for (int attempt = 0; attempt < TRIES; attempt++) {
            int[] shape = shape(outcrops, rng);
            int count = shape[0];
            int w = shape[1];
            int h = shape[2];
            if (w > area.width() || h > area.height()) {
                continue;
            }
            int x = area.col() + rng.nextInt(area.width() - w + 1);
            int y = area.row() + rng.nextInt(area.height() - h + 1);
            int[] at = new int[count];
            for (int i = 0; i < count; i++) {
                at[i] = (y + shape[4 + i * 2]) * cols + x + shape[3 + i * 2];
            }
            if (!fits(at)) {
                continue;
            }
            Terrain[] under = new Terrain[count];
            for (int i = 0; i < count; i++) {
                under[i] = tiles[at[i]];
                tiles[at[i]] = Terrain.ROCK;
            }
            boolean[] reached = reach();
            if (endsMeet(reached) && pockets(reached) == 0) {
                return count;
            }
            for (int i = 0; i < count; i++) {
                tiles[at[i]] = under[i];
            }
        }
        return -1;
    }

    /**
     * A bar {@code length} x {@code thickness}, for a share of them with an arm bent off one end, standing
     * or lying: {@code [count, width, height, col0, row0, col1, row1, ...]}, offsets from its corner.
     */
    private static int[] shape(Outcrops outcrops, Randomness rng) {
        int length = between(rng, outcrops.lengthMin(), outcrops.lengthMax());
        int thickness = Math.min(length, between(rng, outcrops.thicknessMin(), outcrops.thicknessMax()));
        int[] across = new int[length * (thickness + 1) + ARM_OUT_MAX * ARM_ALONG_MAX];
        int[] along = new int[across.length];
        int count = outcrops.ragged() > 0f && thickness >= 2
                ? ragged(length, thickness, outcrops.ragged(), rng, across, along)
                : bar(length, thickness, across, along);
        if (length >= 2 && rng.nextFloat() < outcrops.arms()) {
            int out = between(rng, 1, ARM_OUT_MAX);
            int run = between(rng, ARM_ALONG_MIN, Math.min(ARM_ALONG_MAX, length));
            int from = rng.nextInt(2) == 0 ? length - run : 0;
            boolean outward = rng.nextInt(2) == 0;
            for (int a = from; a < from + run; a++) {
                for (int o = 1; o <= out; o++) {
                    across[count] = outward ? thickness - 1 + o : -o;
                    along[count++] = a;
                }
            }
        }
        boolean lying = rng.nextInt(2) == 0;
        int minCol = Integer.MAX_VALUE;
        int minRow = Integer.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            minCol = Math.min(minCol, lying ? along[i] : across[i]);
            minRow = Math.min(minRow, lying ? across[i] : along[i]);
        }
        int[] shape = new int[3 + count * 2];
        shape[0] = count;
        for (int i = 0; i < count; i++) {
            int col = (lying ? along[i] : across[i]) - minCol;
            int row = (lying ? across[i] : along[i]) - minRow;
            shape[3 + i * 2] = col;
            shape[4 + i * 2] = row;
            shape[1] = Math.max(shape[1], col + 1);
            shape[2] = Math.max(shape[2], row + 1);
        }
        return shape;
    }

    /** A clean bar's tiles, {@code across} its thickness and {@code along} its length. Returns how many. */
    private static int bar(int length, int thickness, int[] across, int[] along) {
        int count = 0;
        for (int a = 0; a < length; a++) {
            for (int t = 0; t < thickness; t++) {
                across[count] = t;
                along[count++] = a;
            }
        }
        return count;
    }

    /**
     * A bar with a broken outline: each of its four corners knocked off at {@code chance}, and a tile bumped out of
     * one face at half that, column by column along it - never within two columns of the last bump, so no slot a
     * tile wide is ever left between two of them, and never notched, which would leave one in the bar. What is left
     * meets edge to edge, and the clearance round it is the dart's to keep. Returns how many tiles.
     */
    private static int ragged(int length, int thickness, float chance, Randomness rng, int[] across, int[] along) {
        int count = 0;
        int lastBump = -BUMP_APART;
        for (int a = 0; a < length; a++) {
            boolean end = a == 0 || a == length - 1;
            boolean knockLow = end && rng.nextFloat() < chance;
            boolean knockHigh = end && rng.nextFloat() < chance;
            for (int t = 0; t < thickness; t++) {
                boolean knocked = (t == 0 && knockLow) || (t == thickness - 1 && knockHigh);
                if (!knocked) {
                    across[count] = t;
                    along[count++] = a;
                }
            }
            if (!end && a - lastBump > BUMP_APART && rng.nextFloat() < chance * 0.5f) {
                across[count] = rng.nextInt(2) == 0 ? -1 : thickness;
                along[count++] = a;
                lastBump = a;
            }
        }
        return count;
    }

    /** Every tile inside the area, open and off the apron, and no other rock within the clearance. */
    private boolean fits(int[] at) {
        for (int tile : at) {
            int col = tile % cols;
            int row = tile / cols;
            if (!area.contains(col, row) || apron[tile] || clear[tile] || !tiles[tile].passable()) {
                return false;
            }
        }
        for (int tile : at) {
            own[tile] = true;
        }
        boolean fits = true;
        for (int i = 0; i < at.length && fits; i++) {
            int col = at[i] % cols;
            int row = at[i] / cols;
            for (int dr = -clearance; dr <= clearance && fits; dr++) {
                for (int dc = -clearance; dc <= clearance && fits; dc++) {
                    int next = index(col + dc, row + dr);
                    if (next >= 0 && own[next]) {
                        continue;
                    }
                    fits = !rock(col + dc, row + dr);
                }
            }
        }
        for (int tile : at) {
            own[tile] = false;
        }
        return fits;
    }

    // ---- ice ----

    /**
     * The maze's chokepoints, a share of them iced (G0115, ADR 0025, provisional, for review) - laid last, over the
     * finished ground, which it never changes: an iced way counts as open to every connectivity check, for it can always
     * be broken. A chokepoint is a <em>narrow crossing</em> of a way: a run of open tiles across it, along a row or down a
     * column, between two solid tiles, at most the clearance and one long, open ground either side of every tile of it -
     * and shut, its two sides not joined within {@link #ICE_DETOUR} steps, so it is a way and not a gap beside a lone
     * rock (a bounded flood: a local cut test). Never on or within the clearance of a kept tile - a pass's apron, the
     * start, a house - so no way into the biome is iced, and never in a clearing. The runs across one way are one
     * chokepoint: its middle run. Then the chokepoints in a dealt order, each kept while it stands the clearance off
     * every one kept before, until the share is iced.
     */
    private List<TileRect> ice(Randomness rng) {
        float share = ground.ice().share();
        List<TileRect> laid = new ArrayList<>();
        if (share <= 0f) {
            return laid;
        }
        List<TileRect> runs = new ArrayList<>();
        for (int row = area.row(); row < area.endRow(); row++) {
            for (int col = area.col(); col < area.endCol(); col++) {
                addCrossing(col, row, 1, 0, runs);
                addCrossing(col, row, 0, 1, runs);
            }
        }
        List<TileRect> ways = middles(runs);
        for (int i = ways.size() - 1; i > 0; i--) {
            int pick = rng.nextInt(i + 1);
            TileRect swap = ways.get(i);
            ways.set(i, ways.get(pick));
            ways.set(pick, swap);
        }
        int wanted = Math.round(share * ways.size());
        for (int i = 0; i < ways.size() && laid.size() < wanted; i++) {
            TileRect way = ways.get(i);
            boolean apart = true;
            for (TileRect other : laid) {
                apart &= gapBetween(way, other) > clearance;
            }
            if (apart) {
                laid.add(way);
            }
        }
        return laid;
    }

    /** The run of open tiles starting here along {@code (dc, dr)}, if it is a chokepoint. */
    private void addCrossing(int col, int row, int dc, int dr, List<TileRect> runs) {
        if (rock(col, row) || !rock(col - dc, row - dr)) {
            return;
        }
        int widest = clearance + 1;
        int length = 0;
        while (length <= widest && !rock(col + length * dc, row + length * dr)) {
            length++;
        }
        if (length > widest) {
            return;
        }
        // Across the run: the two sides of the way it shuts.
        int sideC = dr;
        int sideR = dc;
        for (int k = 0; k < length; k++) {
            int c = col + k * dc;
            int r = row + k * dr;
            if (!area.contains(c, r)
                    || clear[r * cols + c]
                    || nearKept(c, r)
                    || rock(c - sideC, r - sideR)
                    || rock(c + sideC, r + sideR)) {
                return;
            }
        }
        TileRect run = new TileRect(col, row, dc == 1 ? length : 1, dr == 1 ? length : 1);
        int middleC = col + (length / 2) * dc;
        int middleR = row + (length / 2) * dr;
        if (!joinedWithin(run, middleC - sideC, middleR - sideR, middleC + sideC, middleR + sideR)) {
            runs.add(run);
        }
    }

    /** Whether a kept tile lies within the clearance of this one. */
    private boolean nearKept(int col, int row) {
        for (int r = row - clearance; r <= row + clearance; r++) {
            for (int c = col - clearance; c <= col + clearance; c++) {
                if (inside(c, r) && apron[r * cols + c]) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether one tile reaches another within {@link #ICE_DETOUR} steps, four-connected, with {@code shut} solid. */
    private boolean joinedWithin(TileRect shut, int fromC, int fromR, int toC, int toR) {
        int span = ICE_DETOUR;
        int side = 2 * span + 1;
        int[] steps = new int[side * side];
        Arrays.fill(steps, -1);
        int[] queue = new int[side * side];
        int head = 0;
        int tail = 0;
        queue[tail++] = span * side + span;
        steps[span * side + span] = 0;
        while (head < tail) {
            int at = queue[head++];
            int lc = at % side;
            int lr = at / side;
            int c = fromC + lc - span;
            int r = fromR + lr - span;
            if (c == toC && r == toR) {
                return true;
            }
            if (steps[at] == span) {
                continue;
            }
            for (int d = 0; d < 4; d++) {
                int nc = lc + (d == 0 ? 1 : d == 1 ? -1 : 0);
                int nr = lr + (d == 2 ? 1 : d == 3 ? -1 : 0);
                if (nc < 0 || nr < 0 || nc >= side || nr >= side) {
                    continue;
                }
                int next = nr * side + nc;
                int wc = fromC + nc - span;
                int wr = fromR + nr - span;
                if (steps[next] >= 0 || rock(wc, wr) || shut.contains(wc, wr)) {
                    continue;
                }
                steps[next] = steps[at] + 1;
                queue[tail++] = next;
            }
        }
        return false;
    }

    /**
     * One run a way: the runs across one way - the same along, side by side - joined, and the middle one of each kept,
     * in the order the ways were first met.
     */
    private static List<TileRect> middles(List<TileRect> runs) {
        int[] group = new int[runs.size()];
        for (int i = 0; i < group.length; i++) {
            group[i] = i;
        }
        for (int i = 0; i < runs.size(); i++) {
            for (int j = i + 1; j < runs.size(); j++) {
                if (sideBySide(runs.get(i), runs.get(j))) {
                    int a = root(group, i);
                    int b = root(group, j);
                    group[Math.max(a, b)] = Math.min(a, b);
                }
            }
        }
        List<TileRect> middles = new ArrayList<>();
        for (int i = 0; i < runs.size(); i++) {
            if (root(group, i) != i) {
                continue;
            }
            List<TileRect> members = new ArrayList<>();
            for (int j = i; j < runs.size(); j++) {
                if (root(group, j) == i) {
                    members.add(runs.get(j));
                }
            }
            middles.add(members.get(members.size() / 2));
        }
        return middles;
    }

    private static int root(int[] group, int i) {
        int at = i;
        while (group[at] != at) {
            at = group[at];
        }
        return at;
    }

    /** Two runs across one way: lying the same way, a row (or column) apart, overlapping along it. */
    private static boolean sideBySide(TileRect a, TileRect b) {
        if (a.height() == 1 && b.height() == 1) {
            return Math.abs(a.row() - b.row()) == 1 && a.col() < b.endCol() && b.col() < a.endCol();
        }
        if (a.width() == 1 && b.width() == 1) {
            return Math.abs(a.col() - b.col()) == 1 && a.row() < b.endRow() && b.row() < a.endRow();
        }
        return false;
    }

    /** How many tiles lie between two rects, the larger of the column and row gaps; zero when they touch. */
    private static int gapBetween(TileRect a, TileRect b) {
        int across = Math.max(0, Math.max(a.col() - b.endCol(), b.col() - a.endCol()));
        int down = Math.max(0, Math.max(a.row() - b.endRow(), b.row() - a.endRow()));
        return Math.max(across, down);
    }

    // ---- the grid ----

    /**
     * Everything the first end reaches, four-connected, within the area and the ring of tiles round it -
     * where its ends lie. A unit has width, so a diagonal pinch is not a way through ({@link Maps}).
     */
    private boolean[] reach() {
        boolean[] reached = new boolean[tiles.length];
        int first = ends[0];
        if (!tiles[first].passable()) {
            return reached;
        }
        int[] queue = new int[tiles.length];
        int head = 0;
        int tail = 0;
        queue[tail++] = first;
        reached[first] = true;
        while (head < tail) {
            int at = queue[head++];
            int col = at % cols;
            int row = at / cols;
            tail = visit(col - 1, row, reached, queue, tail);
            tail = visit(col + 1, row, reached, queue, tail);
            tail = visit(col, row - 1, reached, queue, tail);
            tail = visit(col, row + 1, reached, queue, tail);
        }
        return reached;
    }

    private int visit(int col, int row, boolean[] reached, int[] queue, int tail) {
        if (col < area.col() - 1 || col > area.endCol() || row < area.row() - 1 || row > area.endRow()) {
            return tail;
        }
        if (rock(col, row)) {
            return tail;
        }
        int at = row * cols + col;
        if (reached[at]) {
            return tail;
        }
        reached[at] = true;
        queue[tail] = at;
        return tail + 1;
    }

    private boolean endsMeet(boolean[] reached) {
        for (int end : ends) {
            if (!reached[end]) {
                return false;
            }
        }
        return true;
    }

    private int pockets(boolean[] reached) {
        int pockets = 0;
        for (int row = area.row(); row < area.endRow(); row++) {
            for (int col = area.col(); col < area.endCol(); col++) {
                int at = row * cols + col;
                if (tiles[at].passable() && !reached[at]) {
                    pockets++;
                }
            }
        }
        return pockets;
    }

    private boolean reachedAt(boolean[] reached, int col, int row) {
        return inside(col, row) && reached[row * cols + col];
    }

    /** Off the map reads as rock, as {@link TileMap} reads it. */
    private boolean rock(int col, int row) {
        return !inside(col, row) || !tiles[row * cols + col].passable();
    }

    private boolean inside(int col, int row) {
        return col >= 0 && row >= 0 && col < cols && row < rows;
    }

    /** A tile's index, or -1 off the map. */
    private int index(int col, int row) {
        return inside(col, row) ? row * cols + col : -1;
    }

    private static int between(Randomness rng, int min, int max) {
        return min + rng.nextInt(max - min + 1);
    }
}
