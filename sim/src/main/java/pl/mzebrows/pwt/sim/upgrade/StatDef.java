package pl.mzebrows.pwt.sim.upgrade;

/**
 * How far one stat may be moved, whatever is held: a range for the sum of percentages of a scaled
 * stat, a floor for a count. Every block states all three; the ones a stat's form does not use are
 * zero, which the catalogue check holds to so no key lies.
 *
 * @param minPercent the most a scaled stat may come down, as a percentage of its base; at least -100
 * @param maxPercent the most it may go up
 * @param min the least a count may be
 */
public record StatDef(int minPercent, int maxPercent, int min) {

    /** The hundred percent a scaled number cannot lose more than. */
    private static final int ALL = -100;

    public StatDef {
        if (minPercent > 0 || maxPercent < 0) {
            throw new IllegalArgumentException(
                    "a stat's range must hold zero, or nothing held would move it: " + minPercent + ".." + maxPercent);
        }
        if (minPercent < ALL) {
            throw new IllegalArgumentException("a stat cannot come down by more than all of it: " + minPercent);
        }
    }
}
