package pl.mzebrows.pwt.map;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * The ground a {@link Patch} is laid in: one terrain, or <em>neighbour</em> - the base of the biome on
 * the patch's nearer side, or the biome's fallback when there is none there or it is the biome's own.
 * What lets a biome look different beside different neighbours, by rule rather than by hand.
 *
 * @param terrain the terrain, or null for neighbour ground
 */
public record PatchGround(@Nullable Terrain terrain) {

    /** How neighbour ground is written in a biome's block. */
    public static final String NEIGHBOUR = "neighbour";

    public PatchGround {
        if (terrain != null && !terrain.passable()) {
            throw new IllegalArgumentException("a patch is ground, not " + terrain);
        }
    }

    /** The base of the biome on the nearer side. */
    public static PatchGround neighbour() {
        return new PatchGround(null);
    }

    /** One terrain, wherever the patch lies. */
    public static PatchGround of(Terrain terrain) {
        return new PatchGround(terrain);
    }

    /** {@code neighbour}, or a terrain's name in lower case - the binder's reading of this type. */
    public static PatchGround parse(String text) {
        if (text.equals(NEIGHBOUR)) {
            return neighbour();
        }
        return of(Terrain.valueOf(text.toUpperCase(Locale.ROOT)));
    }

    /**
     * The terrain to lay for a patch nearer one side of its biome.
     *
     * @param nearer the base of the biome on the patch's nearer side, null when there is none
     */
    public Terrain resolve(@Nullable Terrain nearer, Terrain base, Terrain fallback) {
        if (terrain != null) {
            return terrain;
        }
        return nearer == null || nearer == base ? fallback : nearer;
    }
}
