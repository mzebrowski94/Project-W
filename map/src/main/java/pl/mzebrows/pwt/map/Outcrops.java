package pl.mzebrows.pwt.map;

/**
 * A biome's outcrops: rock laid as bars {@code length} x {@code thickness}, a share {@link #arms()} of them
 * with an arm bent off one end - the Adventures' rock bent round a corner. Each is kept the clearance off
 * all other rock, so none can enclose anything or close a pass.
 *
 * @param density the fraction of the biome laid as outcrops, laid until reached; zero is no outcrops
 * @param arms the chance an outcrop has an arm; zero is straight bars only
 */
public record Outcrops(float density, int lengthMin, int lengthMax, int thicknessMin, int thicknessMax, float arms) {

    public Outcrops {
        if (density < 0f || density > 1f) {
            throw new IllegalArgumentException("an outcrop density is a fraction of the biome, was " + density);
        }
        if (density > 0f && (lengthMin < 1 || lengthMax < lengthMin)) {
            throw new IllegalArgumentException(
                    "outcrop lengths run from at least 1 up, were " + lengthMin + ".." + lengthMax);
        }
        if (density > 0f && (thicknessMin < 1 || thicknessMax < thicknessMin)) {
            throw new IllegalArgumentException(
                    "outcrop thicknesses run from at least 1 up, were " + thicknessMin + ".." + thicknessMax);
        }
        if (arms < 0f || arms > 1f) {
            throw new IllegalArgumentException("arms is a chance, was " + arms);
        }
    }

    /** No outcrops: what a biome whose block leaves them out has. */
    public static Outcrops off() {
        return new Outcrops(0f, 0, 0, 0, 0, 0f);
    }
}
