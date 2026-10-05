package pl.mzebrows.pwt.map;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Scatters circles over a map: on ground of a given kind, clear of each other, and clear of anything
 * already there.
 *
 * <p><strong>Tile-anchored, never tile-centred.</strong> A tile decides <em>which</em> patch of ground
 * a thing goes on; where inside that patch it actually stands is drawn at random. Objects sitting at
 * tile centres are the single most obvious tell that a level was generated rather than laid out, and
 * one line of jitter removes it (G0009). At most one thing is anchored per tile, which gives a
 * natural minimum spacing of about a tile without any spacing pass.
 *
 * <p>This is deliberately grid-anchored rather than Poisson-disc. Bridson's algorithm is the right
 * tool for dense continuous scatter that must not read as a grid at all - undergrowth, debris - and
 * would be the thing to reach for if that ever appears. Here the grid is the unit a level is authored
 * and generated in, and terrain is per tile, so anchoring to it is what makes "mushrooms grow on
 * organic ground" a filter rather than a rejection loop.
 *
 * <p><strong>It reports what it could not place.</strong> A full arena and a broken generator look
 * identical to a caller that is handed no answer, which is what was wrong with what this replaces.
 *
 * <p>Allocates freely: laying out a level happens once, not per frame.
 */
public final class Scatter {

    /** Told where each circle ended up. The caller decides what actually goes there. */
    @FunctionalInterface
    public interface Place {
        void at(float x, float y);
    }

    /** Which tiles nothing may be anchored on, whatever their ground. */
    @FunctionalInterface
    public interface Exclusion {
        boolean excludes(int col, int row);
    }

    private static final Set<Terrain> ANY_GROUND = anyGround();

    private final TileMap map;
    private final Randomness rng;
    private final float clearance;
    private final float wallMargin;

    /** How far off impassable ground a placed circle must stay. Zero is flush, which is the default. */
    private float rockMargin;

    /** Tiles never offered, whatever their ground. None by default. */
    private Exclusion excluded = (col, row) -> false;

    private final List<Circle> taken = new ArrayList<>();

    /**
     * @param clearance gap left between anything placed and anything already there, in world units
     * @param wallMargin how far from the edge of the map a placed circle must stay. Something flush
     *     against a wall leaves a gap nothing can fit through, and a unit pushed into it has nowhere
     *     to be put. Pass at least the diameter of the largest thing that must get past.
     */
    public Scatter(TileMap map, Randomness rng, float clearance, float wallMargin) {
        this.map = map;
        this.rng = rng;
        this.clearance = clearance;
        this.wallMargin = wallMargin;
    }

    /**
     * Keeps everything this scatter places at least {@code margin} off impassable ground, and returns
     * this scatter.
     *
     * <p>For something that <strong>never moves</strong>. A boulder laid nearly flush against rock
     * leaves a slot narrower than a man, and nothing routes round an entity - the route knows rock and
     * steering knows entities - so a unit walks into the slot and stays there. That is the edge margin's
     * argument, applied to the rock inside the map. Off by default, because something that moves is
     * shoved out of a slot by the solver, and a unit hatched or put down in a narrow place has to fit
     * there. Pass at least the diameter of the largest thing that must get past.
     */
    public Scatter keepingOffRock(float margin) {
        this.rockMargin = margin;
        return this;
    }

    /**
     * Anchors nothing this scatter places on a tile {@code excluded} names, and returns this scatter - ground a
     * generator kept clear, such as a pass's apron. The tile an anchor falls on decides, as its ground does.
     */
    public Scatter excluding(Exclusion excluded) {
        this.excluded = excluded;
        return this;
    }

    /** Marks space as occupied without putting anything there - keeps a scatter off the commander. */
    public void reserve(float x, float y, float radius) {
        taken.add(new Circle(x, y, radius));
    }

    /** Scatters over any ground that can be stood on. */
    public int scatter(int count, float radius, Place place) {
        return scatter(count, radius, ANY_GROUND, place);
    }

    /**
     * Places up to {@code count} circles of this radius inside one rectangle of tiles - a spawn area
     * rather than the whole map.
     *
     * <p>Same jitter, same clearance and the same report of what would not fit as the full-map
     * scatter, because it is the same routine with a smaller candidate list. A squad therefore lands
     * spread out and off the tile centres, and a spawn area too small for the squad says so instead
     * of stacking them.
     *
     * @return how many it actually placed, which is the caller's cue that the area was too small
     */
    public int scatter(TileRect area, int count, float radius, Place place) {
        return place(shuffledTilesIn(area, ANY_GROUND), count, radius, place);
    }

    /**
     * The same, on tiles whose terrain is one of {@code on} only - food on the ground it grows on, in one band.
     *
     * @return how many it actually placed
     */
    public int scatter(TileRect area, int count, float radius, Set<Terrain> on, Place place) {
        return place(shuffledTilesIn(area, on), count, radius, place);
    }

    /**
     * The same, filling the tiles nearest one point first - a brood out of a hole, which should come
     * out of the hole rather than anywhere in the square around it. Still jittered inside each tile,
     * and tiles the same distance away are still taken in a shuffled order, so nothing lines up.
     *
     * @return how many it actually placed
     */
    public int scatterNearest(TileRect area, float x, float y, int count, float radius, Place place) {
        int[] candidates = shuffledTilesIn(area, ANY_GROUND);
        float[] distance = new float[candidates.length];
        for (int i = 0; i < candidates.length; i++) {
            int col = candidates[i] % map.cols();
            int row = candidates[i] / map.cols();
            float toX = (col + 0.5f) * map.tileSize() - x;
            float toY = (row + 0.5f) * map.tileSize() - y;
            distance[i] = toX * toX + toY * toY;
        }
        // Insertion sort: stable, so the shuffle still decides between equals, and a hatch area is a
        // few dozen tiles at most.
        for (int i = 1; i < candidates.length; i++) {
            int tile = candidates[i];
            float key = distance[i];
            int j = i - 1;
            while (j >= 0 && distance[j] > key) {
                candidates[j + 1] = candidates[j];
                distance[j + 1] = distance[j];
                j--;
            }
            candidates[j + 1] = tile;
            distance[j + 1] = key;
        }
        return place(candidates, count, radius, place);
    }

    /**
     * Places up to {@code count} circles of this radius on tiles whose terrain is one of {@code on}.
     *
     * @return how many it actually placed, which is the caller's cue that the ground was full
     */
    public int scatter(int count, float radius, Set<Terrain> on, Place place) {
        return place(shuffledTiles(on), count, radius, place);
    }

    /** Walks a prepared candidate list, jittering inside each tile until it has placed enough. */
    private int place(int[] candidates, int count, float radius, Place place) {
        if (count <= 0) {
            return 0;
        }
        int placed = 0;
        for (int i = 0; i < candidates.length && placed < count; i++) {
            int col = candidates[i] % map.cols();
            int row = candidates[i] / map.cols();
            float x = rng.nextFloat(col * map.tileSize(), (col + 1) * map.tileSize());
            float y = rng.nextFloat(row * map.tileSize(), (row + 1) * map.tileSize());
            if (!fits(x, y, radius)) {
                continue;
            }
            reserve(x, y, radius);
            place.at(x, y);
            placed++;
        }
        return placed;
    }

    /**
     * Every tile of the wanted ground, in a shuffled order. Fisher-Yates against the caller's seeded
     * stream, so a layout is the same on every run and the order is not the map's own row order -
     * walking tiles in order would fill a level from one corner.
     */
    private int[] shuffledTiles(Set<Terrain> on) {
        return shuffledTilesIn(new TileRect(0, 0, map.cols(), map.rows()), on);
    }

    /** The same, restricted to one rectangle of the grid. Tiles outside the map are simply not offered. */
    private int[] shuffledTilesIn(TileRect area, Set<Terrain> on) {
        int[] candidates = new int[area.tileCount()];
        int found = 0;
        for (int row = area.row(); row < area.endRow(); row++) {
            for (int col = area.col(); col < area.endCol(); col++) {
                if (map.inBounds(col, row)
                        && on.contains(map.terrainAtTile(col, row))
                        && !excluded.excludes(col, row)) {
                    candidates[found++] = row * map.cols() + col;
                }
            }
        }
        int[] shuffled = new int[found];
        System.arraycopy(candidates, 0, shuffled, 0, found);
        for (int i = found - 1; i > 0; i--) {
            int swap = rng.nextInt(i + 1);
            int held = shuffled[i];
            shuffled[i] = shuffled[swap];
            shuffled[swap] = held;
        }
        return shuffled;
    }

    /** Clear of the map edge, clear of impassable ground, and clear of everything already placed. */
    private boolean fits(float x, float y, float radius) {
        float inset = radius + wallMargin;
        if (x < inset || y < inset || x > map.worldWidth() - inset || y > map.worldHeight() - inset) {
            return false;
        }
        if (!map.isClear(x, y, radius + rockMargin)) {
            return false;
        }
        for (Circle circle : taken) {
            float dx = x - circle.x();
            float dy = y - circle.y();
            float required = radius + circle.radius() + clearance;
            if (dx * dx + dy * dy < required * required) {
                return false;
            }
        }
        return true;
    }

    private static Set<Terrain> anyGround() {
        EnumSet<Terrain> ground = EnumSet.noneOf(Terrain.class);
        for (Terrain terrain : Terrain.values()) {
            if (terrain.passable()) {
                ground.add(terrain);
            }
        }
        return ground;
    }

    private record Circle(float x, float y, float radius) {}
}
