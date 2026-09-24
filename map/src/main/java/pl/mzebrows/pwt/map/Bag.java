package pl.mzebrows.pwt.map;

/**
 * A shuffle bag over a range of values: every value comes up once before any comes up again.
 *
 * <p>For a count or a choice drawn over and over - how big a nest's brood is, how many crates a point
 * is worth. Drawn independently, three smallest broods in a row are as likely as any other three, and
 * that is a streak a player notices; from a bag, the smallest comes up once and not again until every
 * other size has. The average is unchanged. Tetris deals its pieces this way for the same reason.
 *
 * <p><strong>The whole state is one {@code long}</strong>: bit {@code i} set means value {@code i} has
 * been dealt from the current bag. It lives on whatever owns the bag - a component field for a bag
 * that lasts, a local for one used up in a single pass - so a snapshot carries it with no help from
 * here, and nothing is allocated. Zero is a full bag, which is what {@code reset()} leaves.
 *
 * <p>Two calls rather than one, because a caller has two things to keep - the value and the bag
 * after it - and Java has no allocation-free way to return both.
 */
public final class Bag {

    /** The widest range a bag can hold: one bit of a {@code long} per value. */
    public static final int MOST = Long.SIZE;

    private Bag() {}

    /**
     * A value in {@code [0, size)} not yet dealt from this bag, uniformly among those left. A range of
     * one value draws nothing at all, so a fixed count never consumes a number from the stream.
     */
    public static int draw(Randomness rng, long drawn, int size) {
        if (size < 1 || size > MOST) {
            throw new IllegalArgumentException("a bag holds 1 to " + MOST + " values, was " + size);
        }
        if (size == 1) {
            return 0;
        }
        long left = ~drawn & everything(size);
        int pick = rng.nextInt(Long.bitCount(left));
        for (int i = 0; i < pick; i++) {
            left &= left - 1;
        }
        return Long.numberOfTrailingZeros(left);
    }

    /** The bag once {@code value} has been dealt from it: full again if that was the last one left. */
    public static long take(long drawn, int value, int size) {
        long after = drawn | (1L << value);
        return after == everything(size) ? 0L : after;
    }

    /** Every value in a range of this size, as bits. */
    private static long everything(int size) {
        return size == MOST ? -1L : (1L << size) - 1L;
    }
}
