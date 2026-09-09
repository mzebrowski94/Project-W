package pl.mzebrows.pwt.client;

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

    private final Color[] byTerrain;
    private final float regionVariance;

    /** Reused per call: shading must not allocate a colour per tile per frame. */
    private final Color shaded = new Color();

    /**
     * @param byTerrain one colour per {@link Terrain}, indexed by ordinal
     * @param regionVariance how far apart two regions of the same terrain may be shaded, as a
     *     fraction toward white or black. Zero draws every region identically.
     */
    public TerrainPalette(Color[] byTerrain, float regionVariance) {
        if (byTerrain.length != Terrain.values().length) {
            throw new IllegalArgumentException("terrain palette needs one colour per terrain, got " + byTerrain.length
                    + " of " + Terrain.values().length);
        }
        this.byTerrain = byTerrain.clone();
        this.regionVariance = regionVariance;
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
        shaded.set(byTerrain[terrain.ordinal()]);
        float shift = shadeFor(region);
        if (shift == 0f) {
            return shaded;
        }
        float target = shift > 0f ? 1f : 0f;
        float amount = Math.abs(shift);
        shaded.r += (target - shaded.r) * amount;
        shaded.g += (target - shaded.g) * amount;
        shaded.b += (target - shaded.b) * amount;
        return shaded;
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
