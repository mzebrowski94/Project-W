package pl.mzebrows.pwt.map;

import java.util.ArrayList;
import java.util.List;

/**
 * What a laid strip may never show, each check a list of faults - empty when it holds - so a sweep can report
 * every seed and every fault at once. Ridges and passes are read off the map and the biomes' rects: a ridge is
 * the columns between two biomes, and a pass is a run of open tiles on its edge column.
 */
final class StripFaults {

    private StripFaults() {}

    /** Every check. */
    static List<String> all(Strip.Laid laid, List<BiomeGround> grounds, StripRules rules) {
        List<String> faults = new ArrayList<>();
        faults.addAll(layout(laid, grounds, rules));
        faults.addAll(passes(laid, rules));
        faults.addAll(reach(laid));
        faults.addAll(clearance(laid, rules));
        faults.addAll(membership(laid, grounds));
        faults.addAll(finaleEdge(laid));
        return faults;
    }

    /** Biomes west to east inside the border, each as wide as its ground allows, a ridge between every two. */
    static List<String> layout(Strip.Laid laid, List<BiomeGround> grounds, StripRules rules) {
        List<String> faults = new ArrayList<>();
        List<TileRect> rects = laid.biomes();
        TileMap map = laid.map();
        if (rects.size() != grounds.size()) {
            faults.add(rects.size() + " biomes laid for " + grounds.size());
            return faults;
        }
        if (map.rows() != rules.rows()
                || rects.getFirst().col() != 1
                || map.cols() != rects.getLast().endCol() + 1) {
            faults.add("strip " + map.cols() + "x" + map.rows() + " does not hold its biomes inside one border");
        }
        for (int p = 0; p < rects.size(); p++) {
            TileRect rect = rects.get(p);
            BiomeGround ground = grounds.get(p);
            if (rect.width() < ground.widthMin() || rect.width() > ground.widthMax()) {
                faults.add("biome " + p + " is " + rect.width() + " wide, outside " + ground.widthMin() + ".."
                        + ground.widthMax());
            }
            if (rect.row() != 1 || rect.height() != rules.rows() - 2) {
                faults.add("biome " + p + " does not run between the border rows");
            }
            if (p > 0 && rect.col() - rects.get(p - 1).endCol() != rules.ridgeWidth()) {
                faults.add("ridge " + (p - 1) + " is "
                        + (rect.col() - rects.get(p - 1).endCol()) + " wide");
            }
        }
        return faults;
    }

    /** Every ridge crossed by between {@code passesMin} and {@code passesMax} passes, as many out as in. */
    static List<String> passes(Strip.Laid laid, StripRules rules) {
        List<String> faults = new ArrayList<>();
        for (int b = 0; b < ridges(laid); b++) {
            int in = runs(laid.map(), westEdge(laid, b));
            int out = runs(laid.map(), eastEdge(laid, b));
            if (in != out || in < rules.passesMin() || in > rules.passesMax()) {
                faults.add("ridge " + b + " has " + in + " passes in and " + out + " out");
            }
        }
        return faults;
    }

    /** How many passes cross a ridge, read on its west edge. */
    static int passesThrough(Strip.Laid laid, int ridge) {
        return runs(laid.map(), westEdge(laid, ridge));
    }

    /** Every open tile on the strip reached from the first pass's end: every pass connects, and no pockets. */
    static List<String> reach(Strip.Laid laid) {
        TileMap map = laid.map();
        int x0 = westEdge(laid, 0);
        int start = -1;
        for (int r = 0; r < map.rows() && start < 0; r++) {
            if (map.passableAtTile(x0, r)) {
                start = r;
            }
        }
        if (start < 0) {
            return List.of("ridge 0 has no pass to start from");
        }
        boolean[] reached = Maps.reachableFrom(map, x0, start);
        int cutOff = 0;
        String first = "";
        for (int r = 0; r < map.rows(); r++) {
            for (int c = 0; c < map.cols(); c++) {
                if (map.passableAtTile(c, r) && !reached[r * map.cols() + c]) {
                    if (cutOff++ == 0) {
                        first = " (" + c + "," + r + ")";
                    }
                }
            }
        }
        return cutOff == 0 ? List.of() : List.of(cutOff + " open tiles cut off, first at" + first);
    }

    /** No run of open ground shorter than the clearance between two rocks, along any row or column. */
    static List<String> clearance(Strip.Laid laid, StripRules rules) {
        List<String> faults = new ArrayList<>();
        TileMap map = laid.map();
        for (int r = 0; r < map.rows(); r++) {
            narrowRuns(map, 0, r, 1, 0, map.cols(), rules.clearance(), "across", faults);
        }
        for (int c = 0; c < map.cols(); c++) {
            narrowRuns(map, c, 0, 0, 1, map.rows(), rules.clearance(), "down", faults);
        }
        return faults;
    }

    /**
     * Every tile in the biome it should be: a biome's own columns its own, a ridge's first half the west biome's
     * and the rest the east's, the border with the biome it closes - and every open ridge tile showing its biome's
     * base. Each biome holds as many regions as its ground asks for.
     */
    static List<String> membership(Strip.Laid laid, List<BiomeGround> grounds) {
        List<String> faults = new ArrayList<>();
        TileMap map = laid.map();
        if (map.biomeCount() != grounds.size()) {
            faults.add(map.biomeCount() + " biomes on the map for " + grounds.size());
        }
        for (int c = 0; c < map.cols(); c++) {
            int owner = owner(laid, c);
            for (int r = 0; r < map.rows(); r++) {
                int biome = map.biomeAtTile(c, r);
                if (biome != owner) {
                    faults.add("tile (" + c + "," + r + ") in biome " + biome + ", should be " + owner);
                } else if (inRidge(laid, c)
                        && map.passableAtTile(c, r)
                        && map.terrainAtTile(c, r) != grounds.get(biome).base()) {
                    faults.add("tile (" + c + "," + r + ") in biome " + biome + " shows " + map.terrainAtTile(c, r)
                            + ", its base is " + grounds.get(biome).base());
                }
            }
        }
        for (int p = 0; p < grounds.size(); p++) {
            int regions = regionsIn(map, p);
            if (regions != grounds.get(p).regions()) {
                faults.add("biome " + p + " has " + regions + " regions, its ground asks for "
                        + grounds.get(p).regions());
            }
        }
        return faults;
    }

    /** The finale's west edge straight: no rock from the ridge before it in its first column. */
    static List<String> finaleEdge(Strip.Laid laid) {
        TileRect finale = laid.biomes().getLast();
        for (int r = finale.row(); r < finale.endRow(); r++) {
            if (!laid.map().passableAtTile(finale.col(), r)) {
                return List.of("the finale's west edge has rock at (" + finale.col() + "," + r + ")");
            }
        }
        return List.of();
    }

    static int ridges(Strip.Laid laid) {
        return laid.biomes().size() - 1;
    }

    static int westEdge(Strip.Laid laid, int ridge) {
        return laid.biomes().get(ridge).endCol();
    }

    static int eastEdge(Strip.Laid laid, int ridge) {
        return laid.biomes().get(ridge + 1).col() - 1;
    }

    /** The biome a column should be in. */
    private static int owner(Strip.Laid laid, int col) {
        List<TileRect> rects = laid.biomes();
        for (int p = 0; p < rects.size(); p++) {
            if (col < rects.get(p).endCol()) {
                return p == 0 || col >= rects.get(p).col() ? p : ridgeHalf(laid, p - 1, col);
            }
        }
        return rects.size() - 1;
    }

    private static int ridgeHalf(Strip.Laid laid, int ridge, int col) {
        int width = eastEdge(laid, ridge) - westEdge(laid, ridge) + 1;
        return col - westEdge(laid, ridge) < width / 2 ? ridge : ridge + 1;
    }

    private static boolean inRidge(Strip.Laid laid, int col) {
        for (int b = 0; b < ridges(laid); b++) {
            if (col >= westEdge(laid, b) && col <= eastEdge(laid, b)) {
                return true;
            }
        }
        return false;
    }

    private static int regionsIn(TileMap map, int biome) {
        boolean[] seen = new boolean[map.regionCount()];
        int count = 0;
        for (int r = 0; r < map.rows(); r++) {
            for (int c = 0; c < map.cols(); c++) {
                int region = map.regionAtTile(c, r);
                if (map.biomeAtTile(c, r) == biome && !seen[region]) {
                    seen[region] = true;
                    count++;
                }
            }
        }
        return count;
    }

    /** Runs of open tiles down one column. */
    private static int runs(TileMap map, int col) {
        int runs = 0;
        for (int r = 0; r < map.rows(); r++) {
            if (map.passableAtTile(col, r) && !map.passableAtTile(col, r - 1)) {
                runs++;
            }
        }
        return runs;
    }

    private static void narrowRuns(
            TileMap map, int col, int row, int dc, int dr, int length, int clearance, String way, List<String> faults) {
        int run = -1;
        for (int step = 0; step < length; step++) {
            int c = col + dc * step;
            int r = row + dr * step;
            boolean open = map.passableAtTile(c, r);
            if (open && run >= 0) {
                run++;
            } else if (!open) {
                if (run > 0 && run < clearance) {
                    faults.add("open run of " + run + " at (" + (c - dc * run) + "," + (r - dr * run) + ") " + way);
                }
                run = 0;
            }
        }
    }
}
