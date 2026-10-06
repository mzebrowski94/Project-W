package pl.mzebrows.pwt.client.draw;

/**
 * How to draw a door being opened (G0069): a thin ring on the door itself, a little wider than one tile, filling
 * clockwise from straight up as the clock runs - a loading circle, in the colour its charge names. On the door
 * rather than over it, so it is seen where the man is working and never covers what stands beyond.
 *
 * @param scale the ring's radius, as a multiple of one tile
 * @param width how thick it is drawn, as a multiple of one tile
 * @param segments how many straight pieces a whole ring is drawn in
 */
public record DoorMark(float scale, float width, int segments) {

    public DoorMark {
        if (scale <= 0f) {
            throw new IllegalArgumentException("door mark scale must be positive, was " + scale);
        }
        if (width <= 0f) {
            throw new IllegalArgumentException("door mark width must be positive, was " + width);
        }
        if (segments < 3) {
            throw new IllegalArgumentException("door mark segments must be at least three, was " + segments);
        }
    }

    /** The radius of the ring for a door of this tile pitch. */
    public float radiusFor(float pitch) {
        return pitch * scale;
    }

    /** How thick to draw it for a door of this tile pitch. */
    public float widthFor(float pitch) {
        return pitch * width;
    }

    /** How many of the ring's pieces to draw this far through, in {@code [0, 1]}; at least one once begun. */
    public int segmentsFor(float fraction) {
        return Math.max(1, Math.round(segments * Math.min(1f, fraction)));
    }

    /** The angle, in degrees from straight up and going clockwise, where piece {@code step} ends. */
    public float angleAt(int step) {
        return 90f - step * 360f / segments;
    }
}
