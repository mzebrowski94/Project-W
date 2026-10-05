package pl.mzebrows.pwt.map;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A mission's ground: its biomes laid west to east inside a rock border, a ridge between every two and
 * before the finale, each crossed by passes (missions ticket 13). The one seam everything that lays ground sits
 * behind.
 *
 * <p>In order: each biome's base; each ridge's core, its faces wandering into the biomes either side - the west
 * side only before the finale, so the finale's west edge is straight - and its passes carved through core and
 * faces alike, with an apron that follows a bent pass out through the face; notches the faces left filled; each
 * biome's regions; each biome's {@link Coats}, entered by its passes' ends; and last the whole strip flooded from
 * the first pass, anything it misses sealed.
 *
 * <p>A tile is in the biome whose ground it shows (G0062): a biome's own columns are its own, a face is the
 * biome it wandered into, a ridge's first half is the west biome's and the rest the east's - so a one-column
 * ridge goes east - and the border is the biome it closes.
 *
 * <p>Streams (G0065): a node per biome position and one per ridge, derived from the map stream; each node only
 * derives - width, regions and coats for a biome, faces and passes for a ridge - so retuning one moves nothing
 * else. Allocates freely: a world is built once.
 */
public final class Strip {

    private static final long POSITIONS = 0xA54F_F53A_5F1D_36F1L;
    private static final long RIDGES = 0x510E_527F_ADE6_82D1L;
    private static final long WIDTH = 0x9B05_688C_2B3E_6C1FL;
    private static final long REGIONS = 0x1F83_D9AB_FB41_BD6BL;
    private static final long COATS = 0x5BE0_CD19_137E_2179L;
    private static final long FACES = 0xCBBB_9D5D_C105_9ED8L;
    private static final long PASSES = 0x629A_292A_367C_D507L;

    /** How many times a face's walk is averaged over its neighbouring rows: more, a smoother face. */
    private static final int FACE_SMOOTHING = 2;

    /** Stretches a face's smoothed walk so it reaches its full depth on most rows, as the prototype tuned it. */
    private static final float FACE_STRETCH = 1.6f;

    /** Places tried for a second pass before the ridge keeps fewer. */
    private static final int TRIES = 40;

    /** Bounds the notch fill, which converges in two or three. */
    private static final int NOTCH_ROUNDS = 16;

    private final StripRules rules;
    private final List<BiomeGround> grounds;
    private final List<TileRect> rects;
    private final TileMap.Builder map;
    private final int cols;

    /** The biome each column is in. */
    private final int[] owner;

    private final boolean[] apron;
    private final boolean[] carved;
    private final List<List<Integer>> ends;

    /** The row the last ridge's first pass came out on, or -1 before the first ridge. */
    private int lastExit = -1;

    private Strip(StripRules rules, List<BiomeGround> grounds, List<TileRect> rects, float tileSize) {
        this.rules = rules;
        this.grounds = grounds;
        this.rects = rects;
        this.cols = rects.getLast().endCol() + 1;
        this.map = TileMap.builder(cols, rules.rows(), tileSize);
        this.owner = new int[cols];
        this.apron = new boolean[cols * rules.rows()];
        this.carved = new boolean[cols * rules.rows()];
        this.ends = new ArrayList<>();
        for (int p = 0; p < grounds.size(); p++) {
            ends.add(new ArrayList<>());
        }
    }

    /**
     * The strip laid, and where each biome lies on it.
     *
     * @param biomes each biome's columns, inside the border rows, west to east; the finale last
     */
    public record Laid(TileMap map, List<TileRect> biomes) {
        public Laid {
            biomes = List.copyOf(biomes);
        }
    }

    /**
     * Lays a strip.
     *
     * @param biomes the dealt biomes, west to east; at least one
     * @param finale the last biome, after the last ridge
     * @param map the map stream; only derived from, never drawn
     */
    public static Laid lay(
            StripRules rules, List<BiomeGround> biomes, BiomeGround finale, Randomness map, float tileSize) {
        if (biomes.isEmpty()) {
            throw new IllegalArgumentException("a strip needs a biome before its finale");
        }
        List<BiomeGround> grounds = new ArrayList<>(biomes);
        grounds.add(finale);
        Randomness positions = map.derive(POSITIONS);
        Randomness ridges = map.derive(RIDGES);
        Randomness[] at = new Randomness[grounds.size()];
        List<TileRect> rects = new ArrayList<>();
        int col = 1;
        for (int p = 0; p < grounds.size(); p++) {
            at[p] = positions.derive(p);
            BiomeGround ground = grounds.get(p);
            int width = between(at[p].derive(WIDTH), ground.widthMin(), ground.widthMax());
            rects.add(new TileRect(col, 1, width, rules.rows() - 2));
            col += width + rules.ridgeWidth();
        }

        Strip strip = new Strip(rules, grounds, rects, tileSize);
        strip.bases();
        for (int b = 0; b < grounds.size() - 1; b++) {
            Randomness ridge = ridges.derive(b);
            strip.ridge(b, ridge.derive(FACES), ridge.derive(PASSES));
        }
        strip.fillNotches();
        strip.regions(at);
        strip.coats(at);
        strip.seal();
        return new Laid(strip.map.build(), rects);
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

    private int ownerOf(int col) {
        for (int p = 0; p < rects.size(); p++) {
            TileRect rect = rects.get(p);
            if (col < rect.endCol()) {
                if (p == 0 || col >= rect.col()) {
                    return p;
                }
                int ridgeCol = rects.get(p - 1).endCol();
                return col - ridgeCol < rules.ridgeWidth() / 2 ? p - 1 : p;
            }
        }
        return rects.size() - 1;
    }

    // ---- ridges ----

    /** Ridge {@code b}, between biome {@code b} and the next: core, faces, passes. */
    private void ridge(int b, Randomness faces, Randomness passes) {
        int x0 = rects.get(b).endCol();
        int x1 = x0 + rules.ridgeWidth() - 1;
        boolean beforeFinale = b + 1 == grounds.size() - 1;
        int westRough = rules.ridgeRough();
        int eastRough = beforeFinale ? 0 : rules.ridgeRough();
        map.rect(x0, 1, rules.ridgeWidth(), rules.rows() - 2, Terrain.ROCK);
        face(x0, -1, westRough, faces);
        face(x1, 1, eastRough, faces);

        int[][] drawn = passRows(passes);
        int xa = x0 - westRough - 1;
        int xb = x1 + eastRough + 1;
        for (int[] pass : drawn) {
            carve(pass, xa, xb);
            mouth(b, x0, -1, westRough, pass, xa, xb);
            mouth(b + 1, x1, 1, eastRough, pass, xa, xb);
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
        passes.add(new int[] {entry, exit(entry, rng, top, entries)});
        for (int k = 1; k < count; k++) {
            for (int attempt = 0; attempt < TRIES; attempt++) {
                int y0 = top + rng.nextInt(entries);
                int y1 = exit(y0, rng, top, entries);
                if (apart(y0, y1, passes)) {
                    passes.add(new int[] {y0, y1});
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

    /** A pass's bottom row at a column: a straight bend from its entry at {@code xa} to its exit at {@code xb}. */
    private static int rowAt(int[] pass, int col, int xa, int xb) {
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
            Coats.lay(
                    map,
                    rects.get(p),
                    grounds.get(p),
                    baseOf(p - 1),
                    baseOf(p + 1),
                    apron,
                    entered,
                    rules.clearance(),
                    at[p].derive(COATS));
        }
    }

    private @Nullable Terrain baseOf(int biome) {
        return biome < 0 || biome >= grounds.size() ? null : grounds.get(biome).base();
    }

    /** The net: whatever open ground the first pass does not reach becomes rock. */
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
