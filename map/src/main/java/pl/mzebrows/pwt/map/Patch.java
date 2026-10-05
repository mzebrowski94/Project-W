package pl.mzebrows.pwt.map;

/**
 * One layer of a biome's patches: pieces of other ground laid until they take {@link #density()} of the
 * biome, each {@code sizeMin..sizeMax} tiles on a side.
 *
 * <p>Written in a biome's block as {@code rect|blob <ground> density sizeMin sizeMax}.
 */
public sealed interface Patch permits Patch.Rect, Patch.Blob {

    PatchGround ground();

    /** The fraction of the biome this layer takes, laid until reached. */
    float density();

    int sizeMin();

    int sizeMax();

    /** A rectangle: a small patch of colour, as the Adventures paint them. */
    record Rect(PatchGround ground, float density, int sizeMin, int sizeMax) implements Patch {
        public Rect {
            check(density, sizeMin, sizeMax);
        }
    }

    /**
     * A grown blob: compact with a ragged rim - ground worth going round, such as thicket. Grown by Eden
     * growth from the middle of its box, keeping the nearest the middle of a few candidates, then one
     * smoothing pass.
     */
    record Blob(PatchGround ground, float density, int sizeMin, int sizeMax) implements Patch {
        public Blob {
            check(density, sizeMin, sizeMax);
        }
    }

    private static void check(float density, int sizeMin, int sizeMax) {
        if (density < 0f || density > 1f) {
            throw new IllegalArgumentException("a patch density is a fraction of the biome, was " + density);
        }
        if (sizeMin < 1 || sizeMax < sizeMin) {
            throw new IllegalArgumentException("patch sizes run from at least 1 up, were " + sizeMin + ".." + sizeMax);
        }
    }
}
