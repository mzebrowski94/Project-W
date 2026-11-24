package pl.mzebrows.pwt.sim.def;

/**
 * The one rounding rule for a whole quantity - hit points, damage, a count of creatures (G0054, G0063): they
 * are whole numbers, and the only place a fraction can arise - a percentage applied to a whole base - rounds
 * here, once.
 *
 * <p><strong>To the nearest, halves up, and a base above zero never rounds to zero</strong>, so no
 * percentage can turn a blow that did something into one that does nothing, or a roster line into none.
 * Two percentages are multiplied before that one rounding, never rounded in turn. Integer arithmetic
 * throughout: the result is exact, and the same on every machine without asking anything of floats.
 * The same shape {@code Fold.scaleTicks} already gives a duration in whole ticks.
 */
public final class Whole {

    private static final int HUNDRED = 100;

    private static final long HUNDRED_SQUARED = (long) HUNDRED * HUNDRED;

    private Whole() {}

    /** A whole per cent as a fraction, the shape a chance is compared with: 30 is 0.3. */
    public static float fraction(int percent) {
        return percent / (float) HUNDRED;
    }

    /** {@code percent} per cent of {@code base}: 125 of 150 is 188, 115 of 7 is 8, 10 of 1 is 1. */
    public static int percentOf(int base, int percent) {
        return percentOf(base, percent, HUNDRED);
    }

    /** {@code base} less {@code percent} per cent of it, rounded once: 1000 less 90 is 100, 7 less 0 is 7. */
    public static int less(int base, int percent) {
        return percentOf(base, HUNDRED - percent);
    }

    /**
     * {@code percent} per cent of {@code another} per cent of {@code base}, rounded once: 125 and 115 of 150 is
     * 216, 125 and 85 of 150 is 159.
     */
    public static int percentOf(int base, int percent, int another) {
        if (base <= 0 || percent <= 0 || another <= 0) {
            return 0;
        }
        long once = (long) base * percent;
        if (once > (Long.MAX_VALUE - HUNDRED_SQUARED) / (2L * another)) {
            return Integer.MAX_VALUE;
        }
        long scaled = (once * another * 2 + HUNDRED_SQUARED) / (2 * HUNDRED_SQUARED);
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, scaled));
    }

    /**
     * Three percentages of {@code base}, rounded once: 125, 115 and 150 of 150 is 324. The tier's, the swarm's and a
     * mission strength's meet a whole number here, never in turn (G0054, G0106).
     */
    public static int percentOf(int base, int percent, int another, int third) {
        if (base <= 0 || percent <= 0 || another <= 0 || third <= 0) {
            return 0;
        }
        long once = (long) base * percent * another;
        long cube = HUNDRED_SQUARED * HUNDRED;
        if (once > (Long.MAX_VALUE - cube) / (2L * third)) {
            return Integer.MAX_VALUE;
        }
        long scaled = (once * third * 2 + cube) / (2 * cube);
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, scaled));
    }

    /**
     * {@code percent} per cent of {@code base}, rounded <em>down</em> - the one exception to the rule above, for a host's
     * count laid on creatures that walk in (G0106): a share he asked for is never more than he asked, and a line of one
     * at 50 is none. 1 at 150 is 1, 3 at 150 is 4, anything at a hundred is itself.
     */
    public static int percentOfDown(int base, int percent) {
        if (base <= 0 || percent <= 0) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, (long) base * percent / HUNDRED);
    }

    /**
     * A count a power has given, to the nearest, halves up, and above zero never to zero: 0.94 is 1, 2.5 is 3, 9.09
     * is 9, nothing or less is 0. The one place this rule meets a {@code double} rather than a percentage - a
     * level's planned drafts, whose curve is a {@code StrictMath} power taken once (G0118), so it is the same on
     * every machine.
     */
    public static int nearest(double value) {
        if (!(value > 0.0)) {
            return 0;
        }
        double rounded = StrictMath.floor(value + 0.5);
        return (int) Math.max(1.0, Math.min(Integer.MAX_VALUE, rounded));
    }

    /**
     * How many of {@code pieces} are left for {@code current} of {@code max} shared out between them, rounded up, never
     * fewer than one nor more than all: a snake's parts and a worm's teeth (G0095, G0096) - the last piece goes with the
     * last point. Nothing left, or no whole to share, is one.
     */
    public static int piecesLeft(int current, int max, int pieces) {
        if (max <= 0 || current <= 0) {
            return Math.min(1, pieces);
        }
        long left = ((long) current * pieces + max - 1) / max;
        return (int) Math.max(Math.min(1, pieces), Math.min(pieces, left));
    }
}
