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
 * Ice is drawn as shards rather than squares (G0115, amended 2026-11-23): each of its tiles a few irregular fragments
 * with seams of the ground showing between them.
 *
 * @param crackWidth how thick a crack is drawn, as a multiple of one tile
 * @param crackLength how long a crack is drawn, as a fraction of its full length across a square, from 0 to 1
 * @param crackDim how far towards black its squares are drawn with nothing left of it, from 0 to 1
 * @param shardGap how far each of ice's shards is drawn in towards its own middle, as a fraction of it, from 0 to 1:
 *     the seams between them
 */
public record DoorMark(
        float scale, float width, int segments, float crackWidth, float crackLength, float crackDim, float shardGap) {

    /** The cracks a square shows, worst last: each {@code {fromX, fromY, toX, toY}} as fractions of a tile from its middle. */
    private static final float[][] CRACKS = {
        {-0.42f, 0.30f, 0.04f, -0.02f},
        {0.04f, -0.02f, 0.40f, 0.36f},
        {0.04f, -0.02f, -0.12f, -0.44f},
        {0.40f, 0.36f, 0.44f, -0.20f}
    };

    /**
     * A tile of ice as shards: each a fan from its first point, as fractions of a tile from the tile's middle - one
     * point inside, off centre, and a rim that wanders in and out of the square's edge like broken rock, cut at
     * irregular places, so the pieces are uneven and the run's outline is never a ruled line.
     */
    private static final float[][] SHARDS = {
        {0.07f, -0.05f, -0.46f, -0.5f, -0.2f, -0.43f, 0.12f, -0.49f},
        {0.07f, -0.05f, 0.12f, -0.49f, 0.34f, -0.44f, 0.48f, -0.47f, 0.44f, -0.16f, 0.5f, 0.08f},
        {0.07f, -0.05f, 0.5f, 0.08f, 0.45f, 0.3f, 0.49f, 0.47f, 0.2f, 0.44f, -0.18f, 0.5f},
        {0.07f, -0.05f, -0.18f, 0.5f, -0.47f, 0.46f, -0.43f, 0.2f, -0.5f, -0.1f},
        {0.07f, -0.05f, -0.5f, -0.1f, -0.44f, -0.3f, -0.46f, -0.5f}
    };

    /** Each shard's middle, the mean of its points: what its seam draws it in towards. */
    private static final float[][] SHARD_MIDDLES = middles();

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
        if (crackLength <= 0f || crackLength > 1f) {
            throw new IllegalArgumentException("door mark crackLength is a fraction, was " + crackLength);
        }
        if (shardGap < 0f || shardGap >= 1f) {
            throw new IllegalArgumentException("door mark shardGap is a fraction under one, was " + shardGap);
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

    /**
     * One end of crack {@code crack}, as a fraction of a tile from the square's middle: {@code 0..3} as {@link #CRACKS},
     * drawn {@code crackLength} of its full length about its own middle.
     */
    public float crackAt(int crack, int end) {
        float[] full = CRACKS[crack];
        float middle = (full[end % 2] + full[end % 2 + 2]) * 0.5f;
        return middle + (full[end] - middle) * crackLength;
    }

    /** How many shards a tile of ice is drawn as. */
    public int shards() {
        return SHARDS.length;
    }

    /** How many points shard {@code shard} has: a fan from the first. */
    public int shardPoints(int shard) {
        return SHARDS[shard].length / 2;
    }

    /**
     * Point {@code point} of shard {@code shard}, its {@code axis} (0 across, 1 down), as a fraction of a tile from the
     * tile's middle: drawn {@code shardGap} of the way in towards the shard's own middle.
     */
    public float shardAt(int shard, int point, int axis) {
        float at = SHARDS[shard][point * 2 + axis];
        return at + (SHARD_MIDDLES[shard][axis] - at) * shardGap;
    }

    private static float[][] middles() {
        float[][] middles = new float[SHARDS.length][2];
        for (int shard = 0; shard < SHARDS.length; shard++) {
            int points = SHARDS[shard].length / 2;
            for (int point = 0; point < points; point++) {
                middles[shard][0] += SHARDS[shard][point * 2] / points;
                middles[shard][1] += SHARDS[shard][point * 2 + 1] / points;
            }
        }
        return middles;
    }

    /** How thick to draw a crack for a door of this tile pitch. */
    public float crackWidthFor(float pitch) {
        return pitch * crackWidth;
    }
}
