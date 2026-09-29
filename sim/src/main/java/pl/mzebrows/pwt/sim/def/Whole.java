package pl.mzebrows.pwt.sim.def;

/**
 * The one rounding rule for hit points and damage (G0054): they are whole numbers, and the only place a
 * fraction can arise - a percentage applied to a whole base - rounds here, once.
 *
 * <p><strong>To the nearest, halves up, and a base above zero never rounds to zero</strong>, so no
 * percentage can turn a blow that did something into one that does nothing. Integer arithmetic
 * throughout: the result is exact, and the same on every machine without asking anything of floats.
 * The same shape {@code Fold.scaleTicks} already gives a duration in whole ticks.
 */
public final class Whole {

    private static final int HUNDRED = 100;

    private Whole() {}

    /** {@code percent} per cent of {@code base}: 125 of 150 is 188, 115 of 7 is 8, 10 of 1 is 1. */
    public static int percentOf(int base, int percent) {
        if (base <= 0 || percent <= 0) {
            return 0;
        }
        long scaled = ((long) base * percent * 2 + HUNDRED) / (2L * HUNDRED);
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, scaled));
    }
}
