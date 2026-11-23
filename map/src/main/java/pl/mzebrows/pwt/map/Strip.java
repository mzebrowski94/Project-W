package pl.mzebrows.pwt.map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A mission's ground: its biomes laid west to east inside a rock border, a ridge between every two and
 * before the finale, each crossed by passes (missions ticket 13), a share of them gatehouses (ticket 12). The one
 * seam everything that lays ground sits behind.
 *
 * <p>In order: each biome's base, and the start kept to it; each ridge's core, its faces wandering into the biomes either side - the west
 * side only before the finale, so the finale's west edge is straight - and its passes carved through core and
 * faces alike, with an apron that follows a bent pass out through the face; notches the faces left filled; each
 * biome's regions; each biome's {@link Coats}, houses first, entered by its passes' ends - and the first by the
 * start; and last the whole strip flooded from the start, anything it misses sealed.
 *
 * <p>A <em>gatehouse</em> is a ridge between two biomes - never the one before the finale - widened round a
 * {@link House}: a column of rock, the wall, the inside, the wall, a column of rock. Its one pass comes in through
 * the west face to a doorway in the west wall and leaves by one in the east, on another row.
 *
 * <p>A tile is in the biome whose ground it shows (G0062): a biome's own columns are its own, a face is the
 * biome it wandered into, a ridge's first half is the west biome's and the rest the east's - so a one-column
 * ridge goes east - and the border is the biome it closes.
 *
 * <p>Streams (G0065): a node per biome position and one per ridge, derived from the map stream; each node only
 * derives - width, regions and coats for a biome, faces, passes and whether it is a gatehouse for a ridge - so
 * retuning one moves nothing else. Allocates freely: a world is built once.
 */
public final class Strip {

    private static final long POSITIONS = 0xA54F_F53A_5F1D_36F1L;
    private static final long RIDGES = 0x510E_527F_ADE6_82D1L;
    private static final long WIDTH = 0x9B05_688C_2B3E_6C1FL;
    private static final long REGIONS = 0x1F83_D9AB_FB41_BD6BL;
    private static final long COATS = 0x5BE0_CD19_137E_2179L;
    private static final long FACES = 0xCBBB_9D5D_C105_9ED8L;
    private static final long PASSES = 0x629A_292A_367C_D507L;
    private static final long GATEHOUSE = 0x9159_015A_3070_DD17L;

    /** A pass that bends the whole way through; otherwise the column a gatehouse's pass steps from entry to exit. */
    private static final int BENDS = -1;

    /** How many times a face's walk is averaged over its neighbouring rows: more, a smoother face. */
    private static final int FACE_SMOOTHING = 2;

    /** Stretches a face's smoothed walk so it reaches its full depth on most rows, as the prototype tuned it. */
    private static final float FACE_STRETCH = 1.6f;

    /** Places tried for a second pass before the ridge keeps fewer. */
    private static final int TRIES = 40;

    /** Bounds the notch fill, which converges in two or three. */
    private static final int NOTCH_ROUNDS = 16;

    /** Bounds the face widening; a strip with a slot takes one round, rarely two. */
    private static final int WIDEN_ROUNDS = 8;

    private final StripRules rules;
    private final List<BiomeGround> grounds;
    private final List<TileRect> rects;
    private final TileMap.Builder map;
    private final int cols;

    /** The biome each column is in. */
    private final int[] owner;

    /** Whether each ridge is a gatehouse. */
    private final boolean[] gated;

    private final List<House> houses = new ArrayList<>();

    private final List<Clearing> clearings = new ArrayList<>();

    /** Where each biome is entered from the west: the start for the first, each pass's mouth for the rest. */
    private final List<List<TileRect>> entries = new ArrayList<>();

    private final boolean[] apron;
    private final boolean[] carved;
    /** Per tile: rock a ridge's face laid, which a slot under the clearance may give back ({@link #widenFaces}). */
    private final boolean[] faced;

    private final List<List<Integer>> ends;

    /** The row the last ridge's first pass came out on, or -1 before the first ridge. */
    private int lastExit = -1;

    private Strip(StripRules rules, List<BiomeGround> grounds, List<TileRect> rects, boolean[] gated, float tileSize) {
        this.rules = rules;
        this.gated = gated;
        this.grounds = grounds;
        this.rects = rects;
        this.cols = rects.getLast().endCol() + 1;
        this.map = TileMap.builder(cols, rules.rows(), tileSize);
        this.owner = new int[cols];
        this.apron = new boolean[cols * rules.rows()];
        this.carved = new boolean[cols * rules.rows()];
        this.faced = new boolean[cols * rules.rows()];
        this.ends = new ArrayList<>();
        for (int p = 0; p < grounds.size(); p++) {
            ends.add(new ArrayList<>());
            entries.add(new ArrayList<>());
        }
    }

    /**
     * The strip laid, and where each biome lies on it and is entered.
     *
     * <p>Not a record: it holds which tiles were kept, one flag a tile, and a record would hand that array out.
     */
    public static final class Laid {

        private final TileMap map;
        private final List<TileRect> biomes;
        private final List<House> houses;
        private final List<Clearing> clearings;
        private final List<List<TileRect>> entries;
        private final boolean[] kept;

        private Laid(
                TileMap map,
                List<TileRect> biomes,
                List<House> houses,
                List<Clearing> clearings,
                List<List<TileRect>> entries,
                boolean[] kept) {
            this.map = map;
            this.biomes = List.copyOf(biomes);
            this.houses = List.copyOf(houses);
            this.clearings = List.copyOf(clearings);
            this.entries = entries.stream().map(List::copyOf).toList();
            this.kept = kept.clone();
        }

        public TileMap map() {
            return map;
        }

        /** Each biome's columns, inside the border rows, west to east; the finale last. */
        public List<TileRect> biomes() {
            return biomes;
        }

        /** Every house laid, a biome's and a gatehouse alike, west to east. */
        public List<House> houses() {
            return houses;
        }

        /** Every clearing laid (G0115), biome by biome west to east: open ground no crag was laid in, reached. */
        public List<Clearing> clearings() {
            return clearings;
        }

        /**
         * Where each biome is entered from the west, by position: the start for the first, and for every other the
         * mouth of each pass through the ridge before it, one column on that ridge's east edge.
         */
        public List<List<TileRect>> entries() {
            return entries;
        }

        /**
         * Whether generation kept this tile its biome's base ground and nothing else: the start, an apron, a house's
         * inside, a doorway or a doorstep. False off the map.
         */
        public boolean kept(int col, int row) {
            return map.inBounds(col, row) && kept[row * map.cols() + col];
        }
    }

    /**
     * Lays a strip.
     *
     * @param biomes the dealt biomes, west to east; at least one
     * @param finale the last biome, after the last ridge
     * @param start where the strip is entered: tiles inside the first biome, kept its base ground and reached from
     *     every pass
     * @param map the map stream; only derived from, never drawn
     */
    public static Laid lay(
            StripRules rules,
            List<BiomeGround> biomes,
            BiomeGround finale,
            TileRect start,
            Randomness map,
            float tileSize) {
        if (biomes.isEmpty()) {
            throw new IllegalArgumentException("a strip needs a biome before its finale");
        }
        List<BiomeGround> grounds = new ArrayList<>(biomes);
        grounds.add(finale);
        Randomness positions = map.derive(POSITIONS);
        Randomness ridges = map.derive(RIDGES);
        Randomness[] at = new Randomness[grounds.size()];
        boolean[] gated = new boolean[grounds.size() - 1];
        for (int b = 0; b < gated.length - 1; b++) {
            gated[b] = ridges.derive(b).derive(GATEHOUSE).nextFloat()
                    < rules.houses().gatehouseShare();
        }
        List<TileRect> rects = new ArrayList<>();
        int col = 1;
        for (int p = 0; p < grounds.size(); p++) {
            at[p] = positions.derive(p);
            BiomeGround ground = grounds.get(p);
            int width = between(at[p].derive(WIDTH), ground.widthMin(), ground.widthMax());
            rects.add(new TileRect(col, 1, width, rules.rows() - 2));
            col += width + (p < gated.length ? ridgeWidth(rules, gated[p]) : 0);
        }

        TileRect first = rects.getFirst();
        if (!first.contains(start.col(), start.row()) || !first.contains(start.endCol() - 1, start.endRow() - 1)) {
            throw new IllegalArgumentException("the start " + start + " must lie inside the first biome " + first);
        }

        Strip strip = new Strip(rules, grounds, rects, gated, tileSize);
        strip.bases();
        strip.start(start);
        for (int b = 0; b < gated.length; b++) {
            Randomness ridge = ridges.derive(b);
            strip.ridge(b, ridge.derive(FACES), ridge.derive(PASSES));
        }
        strip.fillNotches();
        strip.regions(at);
        strip.coats(at);
        strip.widenFaces();
        strip.floors();
        strip.seal();
        strip.houses.sort(Comparator.comparingInt(house -> house.walls().col()));
        return new Laid(strip.map.build(), rects, strip.houses, strip.clearings, strip.entries, strip.apron);
    }

    /**
     * The tile a biome's fixed layout is measured from - a finale's set piece, every position of it an offset from
     * here (G0064): its middle column, rounded west, and its middle row. Asked of a finale's rect, it is (197, 15) on
     * the Adventures' ground. The one place it is worked out.
     */
    public static TileRect anchor(TileRect biome) {
        return TileRect.ofTile(biome.col() + (biome.width() - 1) / 2, biome.row() + biome.height() / 2);
    }

    private static int ridgeWidth(StripRules rules, boolean gatehouse) {
        return gatehouse ? rules.houses().gatehouseRidge() : rules.ridgeWidth();
    }

    // ---- bases ----

    /** Every column its biome's base, then the rock border round the whole strip. */
    private void bases() {
        for (int c = 0; c < cols; c++) {
            owner[c] = ownerOf(c);
            map.rect(c, 0, 1, rules.rows(), grounds.get(owner[c]).base());
        }
        map.border(1, Terrain.ROCK);
    }

    /** The start: kept the first biome's base, and the first biome's first end, which the strip is flooded from. */
    private void start(TileRect start) {
        for (int r = start.row(); r < start.endRow(); r++) {
            for (int c = start.col(); c < start.endCol(); c++) {
                apron[r * cols + c] = true;
            }
        }
        ends.getFirst().add(start.row() * cols + start.col());
        entries.getFirst().add(start);
    }

    private int ownerOf(int col) {
        for (int p = 0; p < rects.size(); p++) {
            TileRect rect = rects.get(p);
            if (col < rect.endCol()) {
                if (p == 0 || col >= rect.col()) {
                    return p;
                }
                int ridgeCol = rects.get(p - 1).endCol();
                return col - ridgeCol < ridgeWidth(rules, gated[p - 1]) / 2 ? p - 1 : p;
            }
        }
        return rects.size() - 1;
    }

    // ---- ridges ----

    /** Ridge {@code b}, between biome {@code b} and the next: core, faces, passes - or one through a gatehouse. */
    private void ridge(int b, Randomness faces, Randomness passes) {
        int width = ridgeWidth(rules, gated[b]);
        int x0 = rects.get(b).endCol();
        int x1 = x0 + width - 1;
        boolean beforeFinale = b + 1 == grounds.size() - 1;
        int westRough = rules.ridgeRough();
        int eastRough = beforeFinale ? 0 : rules.ridgeRough();
        map.rect(x0, 1, width, rules.rows() - 2, Terrain.ROCK);
        face(x0, -1, westRough, faces);
        face(x1, 1, eastRough, faces);

        int xa = x0 - westRough - 1;
        int xb = x1 + eastRough + 1;
        int[][] drawn = gated[b] ? new int[][] {gatehouse(x0, xa, xb, passes)} : passRows(passes);
        for (int[] pass : drawn) {
            if (pass[2] == BENDS) {
                carve(pass, xa, xb);
            }
            mouth(b, x0, -1, westRough, pass, xa, xb);
            mouth(b + 1, x1, 1, eastRough, pass, xa, xb);
        }
    }

    /**
     * The house in a gatehouse ridge whose west edge is {@code x0}, and its pass: entered at least {@code snake}
     * rows from the last ridge's exit, left on another row no further than the house allows, a row in from the
     * border either side so the doorways are never at its corners. The house stands as high as both doorways
     * need, in the middle of the rows that allow. Returns the pass, {@code [entry, exit, step column]}.
     */
    private int[] gatehouse(int x0, int xa, int xb, Randomness rng) {
        HouseRules shape = rules.houses();
        int top = 2;
        int entries = rules.gatehouseEntryRows();
        int entry = top + firstEntry(rng, entries, top);
        int exit = otherRow(entry, rng, top, entries, shape.gatehouseJog(rules.passJog()));
        lastExit = exit;

        int door = shape.doorwayWidth();
        int inDoor = entry + (rules.passWidth() - door) / 2;
        int outDoor = exit + (rules.passWidth() - door) / 2;
        int high = shape.gatehouseHeight();
        int lowest = Math.max(top, Math.max(inDoor, outDoor) + door - high);
        int highest = Math.min(rules.rows() - 2 - high, Math.min(inDoor, outDoor));
        TileRect walls = new TileRect(x0 + 1, (lowest + highest) / 2 - 1, shape.gatehouseWidth() + 2, high + 2);
        TileRect in = new TileRect(walls.col(), inDoor, 1, door);
        TileRect out = new TileRect(walls.endCol() - 1, outDoor, 1, door);
        map.rect(walls.col(), walls.row(), walls.width(), walls.height(), Terrain.WALL);

        int x1 = walls.endCol();
        open(xa, entry, x0 - xa + 1, rules.passWidth());
        open(x1, exit, xb - x1 + 1, rules.passWidth());
        open(walls.col() + 1, walls.row() + 1, walls.width() - 2, walls.height() - 2);
        open(in.col(), in.row(), in.width(), in.height());
        open(out.col(), out.row(), out.width(), out.height());
        int step = rules.clearance();
        keep(in.col() - step, in.row(), step + 1, door);
        keep(out.col(), out.row(), step + 1, door);
        keep(walls.col() + 1, walls.row() + 1, walls.width() - 2, walls.height() - 2);
        houses.add(new House(walls, List.of(in, out)));
        return new int[] {entry, exit, walls.col() + walls.width() / 2};
    }

    /** Kept: a house's inside, a doorway and its doorstep - as a biome's houses are by its coats. */
    private void keep(int col, int row, int width, int height) {
        for (int c = col; c < col + width; c++) {
            for (int r = row; r < row + height; r++) {
                apron[r * cols + c] = true;
            }
        }
    }

    /** A row other than {@code entry} among the entry rows, at most {@code jog} from it. */
    private static int otherRow(int entry, Randomness rng, int top, int entries, int jog) {
        int lo = Math.max(top, entry - jog);
        int hi = Math.min(top + entries - 1, entry + jog);
        int pick = lo + rng.nextInt(hi - lo);
        return pick >= entry ? pick + 1 : pick;
    }

    /** A rect laid as the ground of whichever biome each column is in, carved so no notch fill closes it. */
    private void open(int col, int row, int width, int height) {
        for (int c = col; c < col + width; c++) {
            for (int r = row; r < row + height; r++) {
                map.set(c, r, grounds.get(owner[c]).base());
                carved[r * cols + c] = true;
            }
        }
    }

    /** One face: a smoothed walk per row, how deep the ridge wanders into the biome beside it there. */
    private void face(int edge, int dir, int rough, Randomness rng) {
        if (rough == 0) {
            return;
        }
        int rows = rules.rows();
        float[] walk = new float[rows];
        for (int r = 0; r < rows; r++) {
            walk[r] = rng.nextFloat() * (rough + 1);
        }
        for (int pass = 0; pass < FACE_SMOOTHING; pass++) {
            float[] next = new float[rows];
            for (int r = 0; r < rows; r++) {
                next[r] = (walk[Math.max(0, r - 1)] + walk[r] + walk[Math.min(rows - 1, r + 1)]) / 3f;
            }
            walk = next;
        }
        for (int r = 1; r < rows - 1; r++) {
            int depth = Math.min(rough, (int) (walk[r] * FACE_STRETCH));
            for (int d = 1; d <= depth; d++) {
                map.set(edge + dir * d, r, Terrain.ROCK);
                faced[r * cols + edge + dir * d] = true;
            }
        }
    }

    /**
     * The rows each pass enters and leaves by, {@code [entry, exit]}. The first is entered at least {@code snake}
     * rows from where the last ridge's pass came out - drawn from those rows only, so a ridge always has one;
     * the others are thrown until they keep {@code passApart} rows of rock from every pass before them.
     */
    private int[][] passRows(Randomness rng) {
        int count = between(rng, rules.passesMin(), rules.passesMax());
        int top = 1;
        int entries = rules.entryRows();
        List<int[]> passes = new ArrayList<>();
        int entry = top + firstEntry(rng, entries, top);
        passes.add(new int[] {entry, exit(entry, rng, top, entries), BENDS});
        for (int k = 1; k < count; k++) {
            for (int attempt = 0; attempt < TRIES; attempt++) {
                int y0 = top + rng.nextInt(entries);
                int y1 = exit(y0, rng, top, entries);
                if (apart(y0, y1, passes)) {
                    passes.add(new int[] {y0, y1, BENDS});
                    break;
                }
            }
        }
        lastExit = passes.getFirst()[1];
        return passes.toArray(new int[0][]);
    }

    /** An entry row, as an offset from the top, among those at least {@code snake} from the last exit. */
    private int firstEntry(Randomness rng, int entries, int top) {
        if (lastExit < 0) {
            return rng.nextInt(entries);
        }
        int allowed = 0;
        for (int i = 0; i < entries; i++) {
            allowed += Math.abs(top + i - lastExit) >= rules.snake() ? 1 : 0;
        }
        int pick = rng.nextInt(allowed);
        for (int i = 0; i < entries; i++) {
            if (Math.abs(top + i - lastExit) >= rules.snake() && pick-- == 0) {
                return i;
            }
        }
        throw new IllegalStateException("StripRules guarantees an entry row " + rules.snake() + " from any exit");
    }

    private int exit(int entry, Randomness rng, int top, int entries) {
        int jog = between(rng, -rules.passJog(), rules.passJog());
        return Math.clamp(entry + jog, top, top + entries - 1);
    }

    private boolean apart(int y0, int y1, List<int[]> passes) {
        int lo = Math.min(y0, y1);
        int hi = Math.max(y0, y1) + rules.passWidth();
        for (int[] other : passes) {
            int otherLo = Math.min(other[0], other[1]);
            int otherHi = Math.max(other[0], other[1]) + rules.passWidth();
            if (lo < otherHi + rules.passApart() && hi + rules.passApart() > otherLo) {
                return false;
            }
        }
        return true;
    }

    /**
     * A pass's bottom row at a column: a straight bend from its entry at {@code xa} to its exit at {@code xb} - or,
     * through a gatehouse, its entry west of the house's middle and its exit east of it.
     */
    private static int rowAt(int[] pass, int col, int xa, int xb) {
        if (pass[2] != BENDS) {
            return col < pass[2] ? pass[0] : pass[1];
        }
        int x = Math.clamp(col, xa, xb);
        int span = xb - xa;
        return pass[0] + Math.floorDiv(2 * (pass[1] - pass[0]) * (x - xa) + span, 2 * span);
    }

    /** The pass laid as the ground of whichever biome each column is in, through core and faces alike. */
    private void carve(int[] pass, int xa, int xb) {
        for (int c = xa; c <= xb; c++) {
            int y = rowAt(pass, c, xa, xb);
            for (int r = y; r < y + rules.passWidth(); r++) {
                map.set(c, r, grounds.get(owner[c]).base());
                carved[r * cols + c] = true;
            }
        }
    }

    /**
     * Where a pass comes out into a biome: its ends on the ridge's edge column, and the apron - a row either side
     * of the pass, following it out through the face and on for {@code apron} tiles past.
     */
    private void mouth(int biome, int edge, int dir, int rough, int[] pass, int xa, int xb) {
        int y = rowAt(pass, edge, xa, xb);
        for (int r = y; r < y + rules.passWidth(); r++) {
            ends.get(biome).add(r * cols + edge);
        }
        if (dir > 0) {
            entries.get(biome).add(new TileRect(edge, y, 1, rules.passWidth()));
        }
        TileRect rect = rects.get(biome);
        for (int d = 1; d <= rough + 1 + rules.apron(); d++) {
            int c = edge + dir * d;
            if (!rect.contains(c, rect.row())) {
                break;
            }
            int at = rowAt(pass, c, xa, xb);
            for (int r = Math.max(rect.row(), at - 1); r <= Math.min(rect.endRow() - 1, at + rules.passWidth()); r++) {
                apron[r * cols + c] = true;
            }
        }
    }

    /** A biome tile with rock on three sides - a slot a face left - becomes rock, unless a pass or apron. */
    private void fillNotches() {
        for (int round = 0; round < NOTCH_ROUNDS; round++) {
            int filled = 0;
            for (TileRect rect : rects) {
                for (int r = rect.row(); r < rect.endRow(); r++) {
                    for (int c = rect.col(); c < rect.endCol(); c++) {
                        if (rock(c, r) || carved[r * cols + c] || apron[r * cols + c]) {
                            continue;
                        }
                        boolean vertical = rock(c, r - 1) && rock(c, r + 1) && (rock(c - 1, r) || rock(c + 1, r));
                        boolean horizontal = rock(c - 1, r) && rock(c + 1, r) && (rock(c, r - 1) || rock(c, r + 1));
                        if (vertical || horizontal) {
                            map.set(c, r, Terrain.ROCK);
                            filled++;
                        }
                    }
                }
            }
            if (filled == 0) {
                return;
            }
        }
    }

    /**
     * A run of open ground shorter than the clearance between two rocks, along a row or down a column, gives back
     * the face rock that bounds it - never the core's, the border's, a notch's or a coat's. What a pass's bend leaves
     * of a face a row in from the border is such a slot. Last of what lays rock, after the coats, so only a strip that
     * would be left with a slot is laid any differently. Giving a tile back lengthens the run being mended but can open
     * one across it - rock either side of the tile on the other axis - so a tile that would is kept, and the slot is
     * left for the strip sweep to report rather than traded for another.
     */
    private void widenFaces() {
        for (int round = 0; round < WIDEN_ROUNDS; round++) {
            if (widenFacesAlong(1, 0) + widenFacesAlong(0, 1) == 0) {
                return;
            }
        }
    }

    private int widenFacesAlong(int dc, int dr) {
        int widened = 0;
        for (int r = 0; r < rules.rows(); r++) {
            for (int c = 0; c < cols; c++) {
                if (rock(c, r) || !rock(c - dc, r - dr)) {
                    continue;
                }
                int run = 0;
                while (run < rules.clearance() && !rock(c + run * dc, r + run * dr)) {
                    run++;
                }
                if (run >= rules.clearance()) {
                    continue;
                }
                if (unface(c - dc, r - dr, dr, dc) || unface(c + run * dc, r + run * dr, dr, dc)) {
                    widened++;
                }
            }
        }
        return widened;
    }

    /**
     * Lays a face's rock back as its biome's base, unless that leaves a run under the clearance through it along
     * {@code (dc, dr)} - the axis across the one being mended. False for anything that is not face rock, or is kept.
     */
    private boolean unface(int col, int row, int dc, int dr) {
        if (col < 0 || row < 0 || col >= cols || row >= rules.rows() || !faced[row * cols + col]) {
            return false;
        }
        int across = 1 + openFrom(col - dc, row - dr, -dc, -dr) + openFrom(col + dc, row + dr, dc, dr);
        if (across < rules.clearance()) {
            return false;
        }
        faced[row * cols + col] = false;
        map.set(col, row, grounds.get(owner[col]).base());
        return true;
    }

    /** How many open tiles from here in a direction, up to the clearance. */
    private int openFrom(int col, int row, int dc, int dr) {
        int open = 0;
        while (open < rules.clearance() && !rock(col + open * dc, row + open * dr)) {
            open++;
        }
        return open;
    }

    // ---- biomes ----

    /** Each biome's regions over its own columns, numbered on from the biome before (G0062). */
    private void regions(Randomness[] at) {
        int first = 0;
        for (int p = 0; p < grounds.size(); p++) {
            int from = firstColumnOf(p);
            int to = lastColumnOf(p);
            int count = grounds.get(p).regions();
            Maps.partitionRegions(
                    map, new TileRect(from, 0, to - from + 1, rules.rows()), first, count, at[p].derive(REGIONS));
            for (int id = first; id < first + count; id++) {
                map.regionInBiome(id, p);
            }
            first += count;
        }
    }

    private int firstColumnOf(int biome) {
        int c = 0;
        while (owner[c] != biome) {
            c++;
        }
        return c;
    }

    private int lastColumnOf(int biome) {
        int c = cols - 1;
        while (owner[c] != biome) {
            c--;
        }
        return c;
    }

    private void coats(Randomness[] at) {
        for (int p = 0; p < grounds.size(); p++) {
            int[] entered = ends.get(p).stream().mapToInt(Integer::intValue).toArray();
            Coats.Laid laid = Coats.lay(
                    map,
                    rects.get(p),
                    grounds.get(p),
                    baseOf(p - 1),
                    baseOf(p + 1),
                    apron,
                    entered,
                    rules,
                    at[p].derive(COATS));
            houses.addAll(laid.houses());
            clearings.addAll(laid.clearings());
        }
    }

    /**
     * Every house's inside and doorways laid as {@link Terrain#FLOOR}, gatehouses and a biome's houses alike - once,
     * after everything that lays ground, so nothing paints over it. Its doorsteps keep the biome's base.
     */
    private void floors() {
        for (House house : houses) {
            floor(house.inside());
            for (TileRect doorway : house.doorways()) {
                floor(doorway);
            }
        }
    }

    private void floor(TileRect rect) {
        map.rect(rect.col(), rect.row(), rect.width(), rect.height(), Terrain.FLOOR);
    }

    private @Nullable Terrain baseOf(int biome) {
        return biome < 0 || biome >= grounds.size() ? null : grounds.get(biome).base();
    }

    /** The net: whatever open ground the start does not reach becomes rock. */
    private void seal() {
        int start = ends.getFirst().getFirst();
        Maps.sealUnreachable(map, start % cols, start / cols, Terrain.ROCK);
    }

    private boolean rock(int col, int row) {
        return col < 0
                || row < 0
                || col >= cols
                || row >= rules.rows()
                || !map.terrainAt(col, row).passable();
    }

    private static int between(Randomness rng, int min, int max) {
        return min + rng.nextInt(max - min + 1);
    }
}
