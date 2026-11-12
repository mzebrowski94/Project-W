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
}
