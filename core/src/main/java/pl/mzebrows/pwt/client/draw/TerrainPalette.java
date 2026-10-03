package pl.mzebrows.pwt.client.draw;

import com.badlogic.gdx.graphics.Color;
import pl.mzebrows.pwt.map.Terrain;

/**
 * The one place that turns a kind of ground into something visible - the terrain half of G0003, and
 * the exact counterpart of {@link Palette} for entities. The map says {@code ORGANIC}; only this
 * class knows that means green.
 *
 * <p>The second axis is the <em>region</em>. Two areas of the same terrain are shifted slightly apart
 * in brightness so a level reads as places rather than as one flat sheet, which is the whole point of
 * partitioning the ground into regions in the first place. The shift is small on purpose: it has to
 * be enough to see a boundary when you look for it and not enough to be mistaken for a different kind
 * of ground.
 *
 * <p>The third is the <em>patch</em>. A biome painted in one flat colour reads as a sheet of paper
 * however large it is, and a level four screens wide made of four such sheets reads as a
 * spreadsheet. So the ground is mottled: a hash of where you are, nudging the colour a little,
 * inventing no ground that is not there - it is the same terrain, lit slightly differently.
 *
 * <p><strong>Hashed per patch of tiles, not per tile.</strong> A hash of each tile on its own is by
 * construction white noise: every tile is independent of its neighbours, and the eye reads that as
 * static rather than as ground - which is exactly how it looked. Hashing a block of tiles together
 * gives the same variation at a scale you can see, and the mottling reads as ground that is not
 * uniform rather than as a screen that needs adjusting. The patch size is the dial that matters
 * here; the variance only decides how loud it is.
 *
 * <p><strong>Quantised into a handful of steps rather than a shade per patch</strong>, so the
 * renderer can still merge a run of adjacent tiles into one quad. Patches make that cheaper rather
 * than dearer: a run can only break at a patch boundary now.
 *
 * <p><strong>The simulation and the map hold no visual data.</strong> A region is an id, and how far
 * apart two ids look is decided here from that id alone - deterministically, so the same region is
 * the same shade on every run and after every restart.
 *
 * <p>Indexed by ordinal for the same reason {@link Palette} is: terrains are data and there will be
 * more of them, and a terrain with no colour cannot exist because the constructor refuses an
 * incomplete palette.
 */
@SuppressWarnings("EnumOrdinal")
public final class TerrainPalette {

    /** Mixing constant for turning a region id into a stable shade. Any odd word would do. */
    private static final int SHADE_MIX = 0x9E37_79B9;

    /** A second mixing word, for the tile hash. Different from the region's, or the two would agree. */
    private static final int TILE_MIX = 0x85EB_CA6B;

    private final Color[] byTerrain;
    private final float regionVariance;
    private final float tileVariance;
    private final int tileShades;
    private final int tilePatch;

    /** Reused per call: shading must not allocate a colour per tile per frame. */
    private final Color shaded = new Color();

    /**
     * @param byTerrain one colour per {@link Terrain}, indexed by ordinal
     * @param regionVariance how far apart two regions of the same terrain may be shaded, as a
     *     fraction toward white or black. Zero draws every region identically.
     * @param tileVariance how far apart two tiles of the same terrain and region may be shaded, as
     *     the same kind of fraction. Smaller than the region's by some way, or the regions stop
     *     reading as places. Zero draws every tile identically, which is what this used to do.
     * @param tileShades how many steps that variance is quantised into. One is no variation at all;
     *     more than a handful costs run-merging in the renderer and buys nothing the eye can see.
     * @param tilePatch how many tiles across one patch of a single shade is. One hashes every tile
     *     on its own, which is white noise and reads as static; a few tiles reads as ground.
     */
    public TerrainPalette(Color[] byTerrain, float regionVariance, float tileVariance, int tileShades, int tilePatch) {
        if (byTerrain.length != Terrain.values().length) {
            throw new IllegalArgumentException("terrain palette needs one colour per terrain, got " + byTerrain.length
                    + " of " + Terrain.values().length);
        }
        if (tileShades < 1) {
            throw new IllegalArgumentException("tile shades must be at least 1, was " + tileShades);
        }
        if (tilePatch < 1) {
            throw new IllegalArgumentException("tile patch must be at least 1 tile, was " + tilePatch);
        }
        this.byTerrain = byTerrain.clone();
        this.regionVariance = regionVariance;
        this.tileVariance = tileVariance;
        this.tileShades = tileShades;
        this.tilePatch = tilePatch;
    }

    /** How many shades one terrain in one region is drawn in. */
    public int tileShades() {
        return tileShades;
    }

    /**
     * Which of those shades this tile wears: the shade of the patch it falls in.
     *
     * <p>A pure function of where the tile is, so the ground looks the same on every run and after
     * every restart, and nothing has to be stored per tile. {@code floorDiv} rather than {@code /},
     * so a patch does not double in width where the coordinates cross zero.
     */
    public int shadeStepFor(int col, int row) {
        if (tileShades == 1) {
            return 0;
        }
        int patchCol = Math.floorDiv(col, tilePatch);
        int patchRow = Math.floorDiv(row, tilePatch);
        int mixed = (patchCol * 73_856_093) ^ (patchRow * 19_349_663);
        mixed *= TILE_MIX;
        mixed ^= mixed >>> 13;
        return Math.floorMod(mixed, tileShades);
    }

    /** The colour of a kind of ground before any region shading. */
    public Color colorFor(Terrain terrain) {
        return byTerrain[terrain.ordinal()];
    }

    /**
     * The colour to draw a tile: its terrain's colour, nudged by which region it belongs to. Returns
     * a shared scratch colour, so copy it out before the next call.
     */
    public Color colorFor(Terrain terrain, int region) {
        return colorFor(terrain, region, 0);
    }

    /**
     * The same, for one of the tile shades within that region. Returns a shared scratch colour, so
     * copy it out before the next call.
     */
    public Color colorFor(Terrain terrain, int region, int shadeStep) {
        shaded.set(byTerrain[terrain.ordinal()]);
        shift(shadeFor(region));
        shift(tileShadeFor(shadeStep));
        return shaded;
    }

    /** How far a given tile shade is shifted, spread evenly across the variance. */
    public float tileShadeFor(int shadeStep) {
        if (tileShades == 1 || tileVariance == 0f) {
            return 0f;
        }
        float unit = shadeStep / (float) (tileShades - 1);
        return (unit * 2f - 1f) * tileVariance;
    }

    private void shift(float shift) {
        if (shift == 0f) {
            return;
        }
        float target = shift > 0f ? 1f : 0f;
        float amount = Math.abs(shift);
        shaded.r += (target - shaded.r) * amount;
        shaded.g += (target - shaded.g) * amount;
        shaded.b += (target - shaded.b) * amount;
    }

    /**
     * How far this region is shifted, in {@code [-regionVariance, regionVariance]}. A pure function
     * of the id, so a region keeps its shade across restarts and across a rebuilt world.
     */
    public float shadeFor(int region) {
        if (regionVariance == 0f || region < 0) {
            return 0f;
        }
        int mixed = region * SHADE_MIX;
        mixed ^= mixed >>> 15;
        float unit = (mixed >>> 8) * 0x1.0p-24f;
        return (unit * 2f - 1f) * regionVariance;
    }
}
