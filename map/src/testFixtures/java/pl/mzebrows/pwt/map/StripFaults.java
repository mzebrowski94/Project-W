package pl.mzebrows.pwt.map;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What a laid strip may never show, each check a list of faults - empty when it holds - so a sweep can report
 * every seed and every fault at once. Ridges and passes are read off the map and the biomes' rects: a ridge is
 * the columns between two biomes, and a pass is a run of open tiles on its edge column. A gatehouse is a house
 * laid in a ridge's columns. A test fixture, so core's mission sweep holds the shipped biomes to it as well.
 */
public final class StripFaults {

    private StripFaults() {}

    /** Every check. */
    public static List<String> all(Strip.Laid laid, List<BiomeGround> grounds, StripRules rules) {
        List<String> faults = new ArrayList<>();
        faults.addAll(layout(laid, grounds, rules));
        faults.addAll(passes(laid, rules));
        faults.addAll(reach(laid));
        faults.addAll(clearance(laid, rules));
        faults.addAll(membership(laid, grounds));
        faults.addAll(finaleEdge(laid));
        faults.addAll(houses(laid, grounds, rules));
        faults.addAll(entries(laid, grounds, rules));
        faults.addAll(kept(laid, grounds, rules));
        return faults;
    }

    /** Biomes west to east inside the border, each as wide as its ground allows, a ridge between every two. */
    public static List<String> layout(Strip.Laid laid, List<BiomeGround> grounds, StripRules rules) {
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
            int ridgeWidth = p > 0 ? rect.col() - rects.get(p - 1).endCol() : 0;
            boolean gatehouse = p > 0 && gatehouse(laid, p - 1);
            if (p > 0 && ridgeWidth != (gatehouse ? rules.houses().gatehouseRidge() : rules.ridgeWidth())) {
                faults.add("ridge " + (p - 1) + (gatehouse ? ", a gatehouse," : "") + " is " + ridgeWidth + " wide");
            }
        }
        return faults;
    }

    /**
     * Every ridge crossed by between {@code passesMin} and {@code passesMax} passes, as many out as in - a gatehouse
     * by one.
     */
    public static List<String> passes(Strip.Laid laid, StripRules rules) {
        List<String> faults = new ArrayList<>();
        for (int b = 0; b < ridges(laid); b++) {
            int in = runs(laid.map(), westEdge(laid, b));
            int out = runs(laid.map(), eastEdge(laid, b));
            int min = gatehouse(laid, b) ? 1 : rules.passesMin();
            int max = gatehouse(laid, b) ? 1 : rules.passesMax();
            if (in != out || in < min || in > max) {
                faults.add("ridge " + b + " has " + in + " passes in and " + out + " out");
            }
        }
        return faults;
    }

    /** How many passes cross a ridge, read on its west edge. */
    public static int passesThrough(Strip.Laid laid, int ridge) {
        return runs(laid.map(), westEdge(laid, ridge));
    }

    /** Every open tile on the strip reached from the first pass's end: every pass connects, and no pockets. */
    public static List<String> reach(Strip.Laid laid) {
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
    public static List<String> clearance(Strip.Laid laid, StripRules rules) {
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
     * base, or a gatehouse's floor. Each biome holds as many regions as its ground asks for.
     */
    public static List<String> membership(Strip.Laid laid, List<BiomeGround> grounds) {
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
                        && map.terrainAtTile(c, r) != laidGround(laid, grounds, c, r)) {
                    faults.add("tile (" + c + "," + r + ") in biome " + biome + " shows " + map.terrainAtTile(c, r)
                            + ", should be " + laidGround(laid, grounds, c, r));
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
    public static List<String> finaleEdge(Strip.Laid laid) {
        TileRect finale = laid.biomes().getLast();
        for (int r = finale.row(); r < finale.endRow(); r++) {
            if (!laid.map().passableAtTile(finale.col(), r)) {
                return List.of("the finale's west edge has rock at (" + finale.col() + "," + r + ")");
            }
        }
        return List.of();
    }

    /**
     * Every house walled round in {@link Terrain#WALL} but for its doorways, and no wall anywhere else; each doorway
     * {@code doorwayWidth} long in a wall of its own, off the corners, open with its doorstep - the clearance deep
     * outside it; the inside open; inside, doorways and doorsteps the base of the biome each tile is in. A house
     * inside a biome has {@code doorways} doorways and lies wholly in it, each biome holding between its
     * {@code min} and {@code max}; a gatehouse has two and lies in a ridge, never the one before the finale.
     */
    public static List<String> houses(Strip.Laid laid, List<BiomeGround> grounds, StripRules rules) {
        List<String> faults = new ArrayList<>();
        TileMap map = laid.map();
        HouseRules shape = rules.houses();
        boolean[] walled = new boolean[map.cols() * map.rows()];
        int[] perBiome = new int[grounds.size()];
        for (House house : laid.houses()) {
            TileRect walls = house.walls();
            String name = "house at (" + walls.col() + "," + walls.row() + ")";
            int ridge = ridgeOf(laid, walls);
            int biome = biomeOf(laid, walls);
            if (ridge < 0 && biome < 0) {
                faults.add(name + " lies across a biome's edge");
            } else if (ridge >= 0 && ridge == ridges(laid) - 1) {
                faults.add(name + " is a gatehouse before the finale");
            } else if (biome >= 0) {
                perBiome[biome]++;
            }
            int wanted = ridge >= 0 ? 2 : shape.doorways();
            if (house.doorways().size() != wanted) {
                faults.add(name + " has " + house.doorways().size() + " doorways, should have " + wanted);
            }
            boolean[] used = new boolean[4];
            for (TileRect door : house.doorways()) {
                int wall = wallOf(house, door);
                if (wall < 0) {
                    faults.add(name + " has a doorway at (" + door.col() + "," + door.row()
                            + ") off its walls or at a corner");
                    continue;
                }
                if (used[wall]) {
                    faults.add(name + " has two doorways in one wall");
                }
                used[wall] = true;
                if (Math.max(door.width(), door.height()) != shape.doorwayWidth()) {
                    faults.add(name + " has a doorway " + Math.max(door.width(), door.height()) + " wide");
                }
                floor(map, door, name + "'s doorway", faults);
                baseGround(map, grounds, doorstep(door, wall, rules.clearance()), name + "'s doorstep", faults);
            }
            floor(map, house.inside(), name + "'s inside", faults);
            for (int r = walls.row(); r < walls.endRow(); r++) {
                for (int c = walls.col(); c < walls.endCol(); c++) {
                    if (house.inside().contains(c, r)) {
                        continue;
                    }
                    walled[r * map.cols() + c] = true;
                    if (!inDoorway(house, c, r) && map.terrainAtTile(c, r) != Terrain.WALL) {
                        faults.add(name + " has " + map.terrainAtTile(c, r) + " in its wall at (" + c + "," + r + ")");
                    }
                }
            }
        }
        for (int r = 0; r < map.rows(); r++) {
            for (int c = 0; c < map.cols(); c++) {
                if (map.terrainAtTile(c, r) == Terrain.WALL && !walled[r * map.cols() + c]) {
                    faults.add("wall at (" + c + "," + r + ") is no house's");
                }
            }
        }
        for (int p = 0; p < grounds.size(); p++) {
            Houses wanted = grounds.get(p).houses();
            if (perBiome[p] < wanted.min() || perBiome[p] > wanted.max()) {
                faults.add("biome " + p + " has " + perBiome[p] + " houses, its ground asks for " + wanted.min() + ".."
                        + wanted.max());
            }
        }
        return faults;
    }

    /**
     * Every biome entered from the west as {@link Strip.Laid#entries} says: the first by the start alone, inside it
     * and on its base ground; every other by each pass through the ridge before it, a run of open tiles a pass wide
     * on the ridge's east edge.
     */
    public static List<String> entries(Strip.Laid laid, List<BiomeGround> grounds, StripRules rules) {
        List<String> faults = new ArrayList<>();
        if (laid.entries().size() != laid.biomes().size()) {
            return List.of(laid.entries().size() + " biomes' entries for "
                    + laid.biomes().size() + " biomes");
        }
        List<TileRect> first = laid.entries().getFirst();
        if (first.size() != 1) {
            faults.add("the first biome is entered " + first.size() + " ways, not by the start alone");
        } else {
            TileRect start = first.getFirst();
            TileRect biome = laid.biomes().getFirst();
            if (!biome.contains(start.col(), start.row()) || !biome.contains(start.endCol() - 1, start.endRow() - 1)) {
                faults.add("the start " + start + " is not inside the first biome");
            } else {
                baseGround(laid.map(), grounds, start, "the start", faults);
            }
        }
        for (int p = 1; p < laid.biomes().size(); p++) {
            List<TileRect> passes = laid.entries().get(p);
            int edge = eastEdge(laid, p - 1);
            if (passes.size() != passesThrough(laid, p - 1)) {
                faults.add("biome " + p + " is entered by " + passes.size() + " passes, its ridge has "
                        + passesThrough(laid, p - 1));
            }
            for (TileRect pass : passes) {
                if (pass.col() != edge || pass.width() != 1 || pass.height() != rules.passWidth()) {
                    faults.add("biome " + p + " is entered at " + pass + ", not a pass's mouth on column " + edge);
                    continue;
                }
                for (int r = pass.row(); r < pass.endRow(); r++) {
                    if (!laid.map().passableAtTile(edge, r)) {
                        faults.add("biome " + p + "'s entry at (" + edge + "," + r + ") is rock");
                    }
                }
            }
        }
        return faults;
    }

    /**
     * Every open tile generation kept is its biome's base ground, or a house's floor inside one - an apron may keep a
     * ridge's face, which is rock - and the start, every house's inside, doorways and doorsteps are kept.
     */
    public static List<String> kept(Strip.Laid laid, List<BiomeGround> grounds, StripRules rules) {
        List<String> faults = new ArrayList<>();
        TileMap map = laid.map();
        for (int r = 0; r < map.rows(); r++) {
            for (int c = 0; c < map.cols(); c++) {
                if (laid.kept(c, r)
                        && map.passableAtTile(c, r)
                        && map.terrainAtTile(c, r) != laidGround(laid, grounds, c, r)) {
                    faults.add("kept tile (" + c + "," + r + ") shows " + map.terrainAtTile(c, r));
                    return faults;
                }
            }
        }
        if (!laid.entries().isEmpty() && !laid.entries().getFirst().isEmpty()) {
            allKept(laid, laid.entries().getFirst().getFirst(), "the start", faults);
        }
        for (House house : laid.houses()) {
            String name =
                    "house at (" + house.walls().col() + "," + house.walls().row() + ")";
            allKept(laid, house.inside(), name + "'s inside", faults);
            for (TileRect door : house.doorways()) {
                int wall = wallOf(house, door);
                allKept(laid, door, name + "'s doorway", faults);
                if (wall >= 0) {
                    allKept(laid, doorstep(door, wall, rules.clearance()), name + "'s doorstep", faults);
                }
            }
        }
        return faults;
    }

    private static void allKept(Strip.Laid laid, TileRect rect, String what, List<String> faults) {
        for (int r = rect.row(); r < rect.endRow(); r++) {
            for (int c = rect.col(); c < rect.endCol(); c++) {
                if (!laid.kept(c, r)) {
                    faults.add(what + " is not kept at (" + c + "," + r + ")");
                    return;
                }
            }
        }
    }

    /** Whether a ridge is a gatehouse: a house stands in its columns. */
    public static boolean gatehouse(Strip.Laid laid, int ridge) {
        for (House house : laid.houses()) {
            if (ridgeOf(laid, house.walls()) == ridge) {
                return true;
            }
        }
        return false;
    }

    /** How many houses lie inside a biome. */
    public static int housesIn(Strip.Laid laid, int biome) {
        int count = 0;
        for (House house : laid.houses()) {
            count += biomeOf(laid, house.walls()) == biome ? 1 : 0;
        }
        return count;
    }

    /** The gatehouse in a ridge, or null. */
    public static @Nullable House gatehouseIn(Strip.Laid laid, int ridge) {
        for (House house : laid.houses()) {
            if (ridgeOf(laid, house.walls()) == ridge) {
                return house;
            }
        }
        return null;
    }

    /** The ridge whose columns hold these walls, or -1. */
    private static int ridgeOf(Strip.Laid laid, TileRect walls) {
        for (int b = 0; b < ridges(laid); b++) {
            if (walls.col() >= westEdge(laid, b) && walls.endCol() - 1 <= eastEdge(laid, b)) {
                return b;
            }
        }
        return -1;
    }

    /** The biome whose rect holds these walls whole, or -1. */
    private static int biomeOf(Strip.Laid laid, TileRect walls) {
        for (int p = 0; p < laid.biomes().size(); p++) {
            TileRect rect = laid.biomes().get(p);
            if (rect.contains(walls.col(), walls.row()) && rect.contains(walls.endCol() - 1, walls.endRow() - 1)) {
                return p;
            }
        }
        return -1;
    }

    /** Which wall a doorway is in - west, east, south, north - off the corners, or -1. */
    public static int wallOf(House house, TileRect door) {
        TileRect walls = house.walls();
        TileRect inside = house.inside();
        if (door.width() == 1 && door.row() >= inside.row() && door.endRow() <= inside.endRow()) {
            if (door.col() == walls.col()) {
                return 0;
            }
            if (door.col() == walls.endCol() - 1) {
                return 1;
            }
        }
        if (door.height() == 1 && door.col() >= inside.col() && door.endCol() <= inside.endCol()) {
            if (door.row() == walls.row()) {
                return 2;
            }
            if (door.row() == walls.endRow() - 1) {
                return 3;
            }
        }
        return -1;
    }

    /** The ground just outside a doorway, {@code depth} deep. */
    private static TileRect doorstep(TileRect door, int wall, int depth) {
        return switch (wall) {
            case 0 -> new TileRect(door.col() - depth, door.row(), depth, door.height());
            case 1 -> new TileRect(door.endCol(), door.row(), depth, door.height());
            case 2 -> new TileRect(door.col(), door.row() - depth, door.width(), depth);
            default -> new TileRect(door.col(), door.endRow(), door.width(), depth);
        };
    }

    private static boolean inDoorway(House house, int col, int row) {
        for (TileRect door : house.doorways()) {
            if (door.contains(col, row)) {
                return true;
            }
        }
        return false;
    }

    /** Every tile of {@code rect} a house's floor, whichever biome it is in. */
    private static void floor(TileMap map, TileRect rect, String what, List<String> faults) {
        for (int r = rect.row(); r < rect.endRow(); r++) {
            for (int c = rect.col(); c < rect.endCol(); c++) {
                if (map.terrainAtTile(c, r) != Terrain.FLOOR) {
                    faults.add(what + " shows " + map.terrainAtTile(c, r) + " at (" + c + "," + r + "), not a floor");
                    return;
                }
            }
        }
    }

    /** What an open tile generation laid should show: a house's floor inside a house or its doorway, else the base. */
    private static Terrain laidGround(Strip.Laid laid, List<BiomeGround> grounds, int col, int row) {
        for (House house : laid.houses()) {
            if (house.inside().contains(col, row) || inDoorway(house, col, row)) {
                return Terrain.FLOOR;
            }
        }
        return grounds.get(laid.map().biomeAtTile(col, row)).base();
    }

    /** Every tile of {@code rect} the base of the biome it is in - open, then, and no patch on it. */
    private static void baseGround(
            TileMap map, List<BiomeGround> grounds, TileRect rect, String what, List<String> faults) {
        for (int r = rect.row(); r < rect.endRow(); r++) {
            for (int c = rect.col(); c < rect.endCol(); c++) {
                Terrain base = grounds.get(map.biomeAtTile(c, r)).base();
                if (map.terrainAtTile(c, r) != base) {
                    faults.add(what + " shows " + map.terrainAtTile(c, r) + " at (" + c + "," + r + "), its base is "
                            + base);
                    return;
                }
            }
        }
    }

    public static int ridges(Strip.Laid laid) {
        return laid.biomes().size() - 1;
    }

    public static int westEdge(Strip.Laid laid, int ridge) {
        return laid.biomes().get(ridge).endCol();
    }

    public static int eastEdge(Strip.Laid laid, int ridge) {
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
