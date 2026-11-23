package pl.mzebrows.pwt.client.draw;

/**
 * How to draw a door being opened (G0069): a thin ring on the door itself, a little wider than one tile, filling
 * clockwise from straight up as the clock runs - a loading circle, in the colour its charge names. On the door
 * rather than over it, so it is seen where the man is working and never covers what stands beyond. And how a door, or
 * ice, is seen worn as it is broken at (ADR 0025, pillar 5): its squares darkened and cracked, more as less is left.
 *
 * @param scale the ring's radius, as a multiple of one tile
 * @param width how thick it is drawn, as a multiple of one tile
 * @param segments how many straight pieces a whole ring is drawn in
 * @param crackWidth how thick a crack is drawn, as a multiple of one tile
 * @param crackDim how far towards black its squares are drawn with nothing left of it, from 0 to 1
 */
public record DoorMark(float scale, float width, int segments, float crackWidth, float crackDim) {

    /** The cracks a square shows, worst last: each {@code {fromX, fromY, toX, toY}} as fractions of a tile from its middle. */
    private static final float[][] CRACKS = {
        {-0.42f, 0.30f, 0.04f, -0.02f},
        {0.04f, -0.02f, 0.40f, 0.36f},
        {0.04f, -0.02f, -0.12f, -0.44f},
        {0.40f, 0.36f, 0.44f, -0.20f}
    };

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
        if (crackWidth <= 0f) {
            throw new IllegalArgumentException("door mark crackWidth must be positive, was " + crackWidth);
        }
        if (crackDim < 0f || crackDim > 1f) {
            throw new IllegalArgumentException("door mark crackDim is a fraction, was " + crackDim);
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

    /** How many cracks a square this worn shows, in {@code [0, 1]}: none whole, every one when nearly gone. */
    public int cracksFor(float wear) {
        return wear <= 0f ? 0 : Math.min(CRACKS.length, (int) Math.ceil(wear * CRACKS.length));
    }

    /** One end of crack {@code crack}, as a fraction of a tile from the square's middle: {@code 0..3} as {@link #CRACKS}. */
    public float crackAt(int crack, int end) {
        return CRACKS[crack][end];
    }

    /** How thick to draw a crack for a door of this tile pitch. */
    public float crackWidthFor(float pitch) {
        return pitch * crackWidth;
    }
}
