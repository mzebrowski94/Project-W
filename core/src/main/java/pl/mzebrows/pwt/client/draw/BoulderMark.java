package pl.mzebrows.pwt.client.draw;

/**
 * How to shade a boulder so it reads as round rock rather than a flat disc.
 *
 * <p>Over its own colour, {@code steps} smaller discs, each lighter than the last and shifted towards the north-west,
 * where the light comes from: a stepped gradient, the pixel-art way to shade a sphere with flat shapes. Each disc stays
 * inside the boulder's outline, so the shading never changes its size on screen.
 *
 * @param steps how many lighter discs are laid over the base; zero draws it flat
 * @param light how far towards white the last disc is, in {@code [0, 1]}
 * @param shift how far towards the light the last disc's middle sits, as a share of the radius along each axis; at
 *     most 0.7, so every disc stays inside the outline
 */
public record BoulderMark(int steps, float light, float shift) {

    /** Past this along each axis, a disc's offset plus its radius reaches outside the boulder. */
    private static final float MAX_SHIFT = 0.7f;

    public BoulderMark {
        if (steps < 0) {
            throw new IllegalArgumentException("boulder steps must not be negative, was " + steps);
        }
        if (light < 0f || light > 1f) {
            throw new IllegalArgumentException("boulder light must be in [0, 1], was " + light);
        }
        if (shift < 0f || shift > MAX_SHIFT) {
            throw new IllegalArgumentException("boulder shift must be in [0, " + MAX_SHIFT + "], was " + shift);
        }
    }

    /** How far through the gradient step {@code step} is, the first 1: in {@code (0, 1)}, never the base or a point. */
    public float at(int step) {
        return step / (steps + 1f);
    }

    /** Step {@code step}'s disc radius on a boulder of this radius. */
    public float radiusAt(float radius, int step) {
        return radius * (1f - at(step));
    }

    /** How far step {@code step}'s middle sits towards the light along each axis, on a boulder of this radius. */
    public float offsetAt(float radius, int step) {
        return radius * shift * at(step);
    }

    /** How far towards white step {@code step} is drawn. */
    public float lightAt(int step) {
        return steps == 0 ? 0f : light * step / steps;
    }
}
